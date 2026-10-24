"""BARO feedback -> independently verified public Issue. No client credentials."""
import hashlib
import json
import os
import re
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime

REPO = 'dpan538/baro'
API = 'https://api.github.com/repos/' + REPO + '/issues'
ISSUE = re.compile(r'https://github\.com/dpan538/baro/issues/([1-9][0-9]*)')
_lock = threading.Lock()


class RelayError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


class GitHubRelay:
    def __init__(self, token=None, *, request=None, clock=time.time):
        self.token = token if token is not None else os.environ.get('BARO_GITHUB_TOKEN', '')
        self.request = request or self._request
        self.clock = clock

    def _request(self, method, url, data=None):
        req = urllib.request.Request(url, None if data is None else json.dumps(data).encode(), {
            'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
            'Content-Type': 'application/json', 'User-Agent': 'BARO-feedback/1.0',
            'Cache-Control': 'no-cache', 'X-GitHub-Api-Version': '2022-11-28'}, method=method)
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *_args, **_kwargs):
                return None
        try:
            with urllib.request.build_opener(NoRedirect).open(req, timeout=12) as response:
                raw = response.read(4 * 1024 * 1024 + 1)
                if len(raw) > 4 * 1024 * 1024:
                    raise RelayError(502, 'github_response_invalid')
                return response.status, json.loads(raw)
        except (urllib.error.URLError, TimeoutError, ValueError, OSError):
            raise RelayError(503, 'github_unavailable') from None

    @staticmethod
    def validate(data):
        if not isinstance(data, dict) or set(data) != {'body', 'requestId', 'version'}:
            raise RelayError(400, 'invalid_input')
        body, request_id, version = (data[k] for k in ('body', 'requestId', 'version'))
        if not all(isinstance(v, str) for v in (body, request_id, version)):
            raise RelayError(400, 'invalid_input')
        # Preserve exact Unicode content for the Android body+NUL+version digest.
        if (not body.strip() or len(body) > 1000 or not re.fullmatch(r'[a-zA-Z0-9-]{16,80}', request_id)
                or not re.fullmatch(r'[a-zA-Z0-9.+-]{1,40}', version)
                or any(ord(c) < 32 and c not in '\n\r\t' for c in body)):
            raise RelayError(400, 'invalid_input')
        return body, request_id, version

    def submit(self, data):
        body, request_id, version = self.validate(data)
        if not self.token:
            raise RelayError(503, 'service_unconfigured')
        digest = hashlib.sha256((body + '\0' + version).encode()).hexdigest()
        prefix = '<!-- BARO:' + request_id + ':'
        marker = prefix + digest + ' -->'
        with _lock:
            recent = 0
            for page in range(1, 6):
                status, issues = self.request('GET', API + f'?state=all&sort=created&direction=desc&per_page=100&page={page}')
                if status != 200 or not isinstance(issues, list):
                    raise RelayError(503, 'github_unavailable')
                for issue in issues:
                    if not isinstance(issue, dict) or 'pull_request' in issue:
                        continue
                    text = issue.get('body') or ''
                    if prefix in text:
                        if marker not in text:
                            raise RelayError(409, 'request_conflict')
                        return self.receipt(issue, request_id, marker)
                    if '<!-- BARO:' in text:
                        try:
                            created = datetime.fromisoformat(issue['created_at'].replace('Z', '+00:00')).timestamp()
                            if created >= self.clock() - 3600:
                                recent += 1
                        except (KeyError, ValueError, TypeError):
                            raise RelayError(502, 'github_response_invalid') from None
                if len(issues) < 100:
                    break
            else:
                # Do not blindly POST when an older ambiguous receipt is beyond
                # the bounded reconciliation window. Fail closed for review.
                raise RelayError(503, 'reconciliation_window_full')
            if recent >= 20:
                raise RelayError(429, 'rate_limited')
            title = '[BARO feedback] ' + body.strip().splitlines()[0][:70]
            text = body + '\n\n---\nBARO ' + version + '\n' + marker
            status, issue = self.request('POST', API, {'title': title, 'body': text})
            if status != 201:
                raise RelayError(503, 'github_unavailable')
            # A timeout after a write keeps the same identity; the next request
            # reconciles against GitHub rather than blindly repeating the POST.
            return self.receipt(issue, request_id, marker)

    def receipt(self, issue, request_id, marker):
        if not isinstance(issue, dict) or 'pull_request' in issue:
            raise RelayError(502, 'github_response_invalid')
        match = ISSUE.fullmatch(issue.get('html_url', ''))
        if not match or marker not in (issue.get('body') or ''):
            raise RelayError(502, 'github_response_invalid')
        status, verified = self.request('GET', API + '/' + match.group(1))
        if (status != 200 or not isinstance(verified, dict) or 'pull_request' in verified
                or verified.get('html_url') != issue['html_url'] or marker not in (verified.get('body') or '')):
            raise RelayError(502, 'github_receipt_unverified')
        return {'accepted': True, 'requestId': request_id, 'receipt': issue['html_url']}


class FeedbackApp:
    def __init__(self, relay=None):
        self.relay = relay or GitHubRelay()

    def __call__(self, env, start_response):
        def reply(code, data):
            raw = json.dumps(data, ensure_ascii=False).encode()
            names = {200:'OK',400:'Bad Request',404:'Not Found',405:'Method Not Allowed',409:'Conflict',413:'Payload Too Large',415:'Unsupported Media Type',429:'Too Many Requests',502:'Bad Gateway',503:'Service Unavailable'}
            start_response(str(code) + ' ' + names[code], [('Content-Type', 'application/json; charset=utf-8'),
                ('Content-Length', str(len(raw))), ('Cache-Control', 'no-store'), ('X-Content-Type-Options', 'nosniff')])
            return [raw]
        path = env.get('PATH_INFO', '/v1/feedback')
        method = env.get('REQUEST_METHOD')
        if path not in ('/healthz', '/v1/feedback', '/api/index'):
            return reply(404, {'error': 'not_found'})
        if method == 'GET' and path in ('/healthz', '/api/index'):
            return reply(200, {'service': 'baro-feedback', 'schema': 1, 'configured': bool(self.relay.token)})
        if method != 'POST' or path == '/healthz':
            return reply(405, {'error': 'method_not_allowed'})
        if env.get('CONTENT_TYPE', '').split(';')[0].lower() != 'application/json':
            return reply(415, {'error': 'json_required'})
        try:
            length = int(env.get('CONTENT_LENGTH') or '0')
            if not 0 < length <= 8192:
                return reply(413, {'error': 'invalid_size'})
            raw = env['wsgi.input'].read(length)
            if len(raw) != length:
                raise ValueError()
            def unique(pairs):
                result = dict(pairs)
                if len(result) != len(pairs):
                    raise ValueError()
                return result
            data = json.loads(raw, object_pairs_hook=unique)
        except (ValueError, KeyError, TypeError, UnicodeError):
            return reply(400, {'error': 'invalid_input'})
        try:
            return reply(200, self.relay.submit(data))
        except RelayError as error:
            return reply(error.status, {'error': error.code})
        except Exception:
            return reply(503, {'error': 'service_unavailable'})
