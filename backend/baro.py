"""Dedicated BARO relay. Python 3.12+, standard library; no Android write secret."""
from __future__ import annotations

import concurrent.futures
import contextlib
import hashlib
import json
import math
import os
import re
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CURRENT = ("temperature_2m,relative_humidity_2m,apparent_temperature,is_day,"
           "precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,"
           "wind_direction_10m,wind_gusts_10m,uv_index,visibility")
HOURLY = ("weather_code,is_day,cloud_cover,temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,"
          "precipitation_probability,precipitation,pressure_msl,visibility,"
          "cloud_cover_low,cloud_cover_mid,cloud_cover_high,wind_speed_10m,"
          "wind_gusts_10m,wind_direction_10m,wind_speed_300hPa,wind_direction_300hPa,"
          "total_column_integrated_water_vapour,freezing_level_height,cape,"
          "convective_inhibition,soil_moisture_0_to_1cm,soil_temperature_0cm,"
          "direct_radiation_instant,diffuse_radiation_instant,uv_index")
REPO = "dpan538/baro"
TTL = 900


class Failure(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def fetch_json(url, *, body=None, token=None, timeout=8):
    headers = {"Accept": "application/json", "User-Agent": "BARO/1 native-relay"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
        headers["X-GitHub-Api-Version"] = "2022-11-28"
    request = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=timeout) as response:
            raw = response.read(3_000_001)
            if len(raw) > 3_000_000:
                raise Failure(502, "upstream_size")
            value = json.loads(raw)
            if not isinstance(value, dict) and not isinstance(value, list):
                raise ValueError()
            return value
    except urllib.error.HTTPError as error:
        raise Failure(503 if error.code in (429, 503) else 502, "upstream_http") from None
    except (OSError, ValueError, TimeoutError):
        raise Failure(502, "upstream_unavailable") from None


class Store:
    def __init__(self, path):
        self.path = path
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with self.db() as db:
            db.executescript("""
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS cache(k TEXT PRIMARY KEY, at REAL, body TEXT);
            CREATE TABLE IF NOT EXISTS leases(k TEXT PRIMARY KEY, until REAL);
            CREATE TABLE IF NOT EXISTS calls(at REAL, weight INTEGER);
            CREATE TABLE IF NOT EXISTS feedback(id TEXT PRIMARY KEY, hash TEXT, at REAL, receipt TEXT, state TEXT);
            """)

    @contextlib.contextmanager
    def db(self):
        db = sqlite3.connect(self.path, timeout=5)
        try:
            yield db
            db.commit()
        finally:
            db.close()

    def reserve(self, weight, now):
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM calls WHERE at < ?", (now - 86400,))
            for seconds, limit in ((60, 500), (3600, 4000), (86400, 9000)):
                used = db.execute("SELECT COALESCE(SUM(weight),0) FROM calls WHERE at > ?", (now-seconds,)).fetchone()[0]
                if used + weight > limit:
                    raise Failure(429, "provider_budget")
            db.execute("INSERT INTO calls VALUES(?,?)", (now, weight))

    def cached(self, key, loader, weight=1):
        deadline = time.monotonic() + 12
        while True:
            now = time.time()
            with self.db() as db:
                db.execute("BEGIN IMMEDIATE")
                row = db.execute("SELECT at,body FROM cache WHERE k=?", (key,)).fetchone()
                if row and 0 <= now-row[0] < TTL:
                    return json.loads(row[1]), row[0], False
                if row:
                    db.execute("DELETE FROM cache WHERE k=?", (key,))
                lease = db.execute("SELECT until FROM leases WHERE k=?", (key,)).fetchone()
                acquired = not lease or lease[0] < now
                if acquired:
                    db.execute("INSERT OR REPLACE INTO leases VALUES(?,?)", (key, now+20))
            if acquired:
                break
            if time.monotonic() >= deadline:
                raise Failure(503, "cache_busy")
            time.sleep(.05)
        try:
            self.reserve(weight, now)
            result = loader()
            with self.db() as db:
                db.execute("INSERT OR REPLACE INTO cache VALUES(?,?,?)", (key, now, json.dumps(result, allow_nan=False)))
                db.execute("DELETE FROM cache WHERE k IN (SELECT k FROM cache ORDER BY at DESC LIMIT -1 OFFSET 256)")
            return result, now, False
        finally:
            with self.db() as db:
                db.execute("DELETE FROM leases WHERE k=?", (key,))


class Service:
    def __init__(self, store, fetch=fetch_json, token=None):
        self.store, self.fetch, self.token = store, fetch, token

    def weather(self, params):
        if set(params) != {"lat", "lon"} or any(len(v) != 1 for v in params.values()):
            raise Failure(400, "coordinates_required")
        try:
            lat, lon = (round(float(params[k][0]), 2) for k in ("lat", "lon"))
            if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
                raise ValueError()
        except (ValueError, OverflowError):
            raise Failure(400, "invalid_coordinates") from None
        common = dict(latitude=lat, longitude=lon, timezone="auto", timeformat="unixtime", forecast_days=3, past_days=1)
        def load_forecast():
            p = dict(common, current=CURRENT, hourly=HOURLY, daily="temperature_2m_max,temperature_2m_min,sunrise,sunset", wind_speed_unit="kmh")
            host = "https://customer-api.open-meteo.com" if os.getenv("BARO_OPEN_METEO_KEY") else "https://api.open-meteo.com"
            if os.getenv("BARO_OPEN_METEO_KEY"):
                p["apikey"] = os.environ["BARO_OPEN_METEO_KEY"]
            result = self.fetch(host + "/v1/forecast?" + urllib.parse.urlencode(p))
            if not isinstance(result, dict) or not isinstance(result.get("current"), dict) or not isinstance(result.get("hourly"), dict) or not result.get("timezone"):
                raise Failure(502, "invalid_forecast")
            return result
        forecast, at, stale = self.store.cached(f"wx:{lat:.2f}:{lon:.2f}", load_forecast, weight=5)
        try:
            def load_air():
                p = dict(common, current="us_aqi,pm2_5")
                host = "https://customer-air-quality-api.open-meteo.com" if os.getenv("BARO_OPEN_METEO_KEY") else "https://air-quality-api.open-meteo.com"
                if os.getenv("BARO_OPEN_METEO_KEY"):
                    p["apikey"] = os.environ["BARO_OPEN_METEO_KEY"]
                return self.fetch(host + "/v1/air-quality?" + urllib.parse.urlencode(p))
            air, air_at, air_stale = self.store.cached(f"air:{lat:.2f}:{lon:.2f}", load_air)
            air_error = None
        except Failure as error:
            air, air_at, air_stale, air_error = None, None, False, error.code
        return {"schema": 1, "latitude": lat, "longitude": lon, "fetchedAt": int(at), "stale": stale,
                "forecast": forecast, "air": air, "airFetchedAt": air_at, "airStale": air_stale, "airError": air_error,
                "source": "Open-Meteo / CAMS · model estimates · CC BY 4.0"}

    def search(self, params):
        if set(params) != {"q", "lang"} or any(len(v) != 1 for v in params.values()):
            raise Failure(400, "invalid_search")
        q, lang = params["q"][0].strip(), params["lang"][0]
        if not 2 <= len(q) <= 80 or lang not in ("zh", "en") or any(ord(c) < 32 for c in q):
            raise Failure(400, "invalid_search")
        url = "https://geocoding-api.open-meteo.com/v1/search?" + urllib.parse.urlencode(dict(name=q, count=8, language=lang, format="json"))
        data, _, _ = self.store.cached("geo:" + lang + ":" + q.casefold(), lambda: self.fetch(url))
        return {"results": [{k: x.get(k) for k in ("id", "name", "latitude", "longitude", "timezone", "country", "admin1")} for x in data.get("results", [])]}

    def feedback(self, body):
        if not isinstance(body, dict) or set(body) != {"body", "requestId", "version"}:
            raise Failure(400, "invalid_feedback")
        message, identity, version = (body[k] for k in ("body", "requestId", "version"))
        if not all(isinstance(x, str) for x in (message, identity, version)) or not 1 <= len(message.strip()) <= 1000 or not re.fullmatch(r"[a-zA-Z0-9-]{16,80}", identity) or not re.fullmatch(r"[\w.+-]{1,50}", version):
            raise Failure(400, "invalid_feedback")
        if not self.token:
            raise Failure(503, "feedback_not_configured")
        digest = hashlib.sha256((message + "\0" + version).encode()).hexdigest()
        now = time.time()
        with self.store.db() as db:
            db.execute("BEGIN IMMEDIATE")
            old = db.execute("SELECT hash,at,receipt,state FROM feedback WHERE id=?", (identity,)).fetchone()
            if old and old[0] != digest:
                raise Failure(409, "request_conflict")
            if old and old[2]:
                return dict(accepted=True, requestId=identity, receipt=old[2])
            if old and now-old[1] < 25:
                raise Failure(409, "feedback_pending")
            if not old and db.execute("SELECT COUNT(*) FROM feedback WHERE at>?", (now-3600,)).fetchone()[0] >= 20:
                raise Failure(429, "feedback_rate_limit")
            db.execute("INSERT OR REPLACE INTO feedback VALUES(?,?,?,?,?)", (identity, digest, now, None, "pending"))
        base = f"https://api.github.com/repos/{REPO}/issues"
        marker = f"<!-- BARO:{identity}:{digest} -->"
        # Reconcile an ambiguous previous POST before ever creating another issue.
        if old:
            for page in range(1, 6):
                issues = self.fetch(base + f"?state=all&sort=created&direction=desc&per_page=100&page={page}", token=self.token)
                if not isinstance(issues, list):
                    raise Failure(502, "invalid_receipt")
                found = next((x for x in issues if marker in (x.get("body") or "")), None)
                if found:
                    return self.confirm(identity, found, marker)
                if len(issues) < 100:
                    break
            else:
                raise Failure(409, "reconciliation_required")
        issue = self.fetch(base, token=self.token, body={"title": f"App feedback · {version}", "body": message + "\n\n" + marker})
        return self.confirm(identity, issue, marker)

    def confirm(self, identity, issue, marker):
        number = issue.get("number")
        if not isinstance(number, int) or number < 1:
            raise Failure(502, "invalid_receipt")
        url = f"https://github.com/{REPO}/issues/{number}"
        confirmed = self.fetch(f"https://api.github.com/repos/{REPO}/issues/{number}", token=self.token)
        if confirmed.get("html_url") != url or confirmed.get("number") != number or marker not in (confirmed.get("body") or "") or "pull_request" in confirmed:
            raise Failure(502, "invalid_receipt")
        with self.store.db() as db:
            db.execute("UPDATE feedback SET receipt=?,state='accepted' WHERE id=?", (url, identity))
        return dict(accepted=True, requestId=identity, receipt=url)


def handler_for(service):
    class Handler(BaseHTTPRequestHandler):
        server_version = "BARO/1"
        def log_message(self, *args):
            pass  # No precise coordinates, messages, or credentials in logs.

        def respond(self, status, data):
            raw = json.dumps(data, allow_nan=False, separators=(",", ":")).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            with contextlib.suppress(BrokenPipeError, ConnectionResetError):
                self.wfile.write(raw)

        def dispatch(self):
            url = urllib.parse.urlsplit(self.path)
            params = urllib.parse.parse_qs(url.query, keep_blank_values=True)
            if self.command == "GET" and url.path == "/healthz":
                return {"service": "baro", "schema": 1, "feedbackConfigured": bool(service.token)}
            if self.command == "GET" and url.path == "/v1/weather":
                return service.weather(params)
            if self.command == "GET" and url.path == "/v1/places":
                return service.search(params)
            if self.command == "POST" and url.path == "/v1/feedback" and not params:
                if self.headers.get_content_type() != "application/json":
                    raise Failure(415, "json_required")
                try:
                    size = int(self.headers.get("Content-Length", "0"))
                    if not 1 <= size <= 8192:
                        raise ValueError()
                    body = json.loads(self.rfile.read(size))
                except (ValueError, UnicodeError):
                    raise Failure(400, "invalid_body") from None
                return service.feedback(body)
            raise Failure(404, "not_found")

        def do_GET(self):
            try:
                self.respond(200, self.dispatch())
            except Failure as error:
                self.respond(error.status, {"error": error.code})
            except Exception:
                self.respond(500, {"error": "internal_error"})

        do_POST = do_GET
    return Handler


def main():
    service = Service(Store(os.getenv("BARO_DB", "./data/baro.sqlite")), token=os.getenv("BARO_GITHUB_TOKEN"))
    server = ThreadingHTTPServer((os.getenv("BARO_BIND", "127.0.0.1"), int(os.getenv("PORT", "8787"))), handler_for(service))
    server.daemon_threads = True
    server.serve_forever()


if __name__ == "__main__":
    main()
