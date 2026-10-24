# BARO independent backend

Python 3.12+ standard library. No Annota endpoint, token or signing identity is used.

```sh
python3 -m unittest discover -s backend/tests -v
cd backend
BARO_DB=./data/baro.sqlite python3 baro.py
# GET http://127.0.0.1:8787/healthz
# GET http://127.0.0.1:8787/v1/weather?lat=-27.47&lon=153.03
# GET http://127.0.0.1:8787/v1/places?q=Brisbane&lang=en
```

For Android emulator development, build with `-PbaroBackendUrl=http://10.0.2.2:8787`. Only debug allows this loopback HTTP route. A distributable build uses the dedicated HTTPS origin via `-PbaroBackendUrl=https://YOUR-BARO-HOST`. An empty origin enables direct, public Open-Meteo evaluation calls and explicitly leaves automatic feedback unavailable. It does not imply a deployed BARO service.

## Deployment

Build `docker build -t baro backend`, then run with a persistent `/data` volume, `PORT=8787`, `BARO_BIND=0.0.0.0`, and `BARO_DB=/data/baro.sqlite`. Terminate TLS at a reverse proxy. Limit request bodies to 8 KiB, connections per client, and request duration at the proxy; do not expose the unauthenticated internal port directly. Use one persistent database for all workers of a deployment. Independent replicas without that volume do not share quotas or deduplication. SQLite WAL is for a local persistent filesystem, not NFS. Back up the volume and restrict it to the service account.

The free Open-Meteo endpoints are for noncommercial evaluation. Commercial deployment requires a dedicated subscription and `BARO_OPEN_METEO_KEY`; it remains server-side. Official limits checked 2026-10-23: 600/minute, 5,000/hour, 10,000/day, 300,000/month. This relay reserves conservative weighted budgets of 500/minute, 4,000/hour and 9,000/day. Forecast requests reserve five calls for more than ten variables; air and geocoding reserve one. Review the provider's actual billing formula and workload before scaling. The shared weather/air cache lasts 15 minutes, quantizes requests to two decimal coordinate places, coalesces concurrent requests, and retains at most 256 entries. Expired rows are deleted before refresh; failed refreshes return an error instead of serving old cache entries. Air failure does not manufacture an AQI.

## Feedback

Set **only** `BARO_GITHUB_TOKEN`, a fine-grained credential scoped to `dpan538/baro`, Issues read/write. Do not use Annota's token. `POST /v1/feedback` accepts exactly `body`, `requestId`, and `version`. The message is public on GitHub; the UI says so. Unicode messages contain 1–1000 code points; IDs are 16–80 alphanumeric/hyphen characters. No logs, locations, contacts or attachments are accepted. A successful response contains an actual Issue URL, matching request ID and `accepted:true`, after a separate GitHub GET confirms the Issue body marker. The Android client independently verifies the public Issue before clearing that draft revision.

SQLite keeps immutable request hashes, pending identities and receipts. A retry after an ambiguous POST first reconciles up to 500 recent Issues. Pending requests return conflict for 25 seconds; different content with the same ID is rejected. New identities are capped at 20/hour for the repository. Uncertain reconciliation beyond the search window requires operator attention. This is a modest-use relay, not an unlimited anonymous issue gateway. Configure reverse-proxy rate limiting before public exposure.

`GET /healthz` reports service name, schema and whether feedback is configured, without credentials. Invalid inputs, unavailable providers, exhausted budgets, missing credentials and invalid receipts remain failures. No public deployment or public feedback write has yet been verified. Local test doubles are not evidence of GitHub delivery.

Sources: [Forecast API](https://open-meteo.com/en/docs), [Air Quality](https://open-meteo.com/en/docs/air-quality-api), [Geocoding](https://open-meteo.com/en/docs/geocoding-api), [pricing and licensing](https://open-meteo.com/en/pricing), [GitHub Issues REST API](https://docs.github.com/en/rest/issues/issues).


### Dedicated public feedback origin

The distributed native app now configures the feedback-only Vercel origin separately through `baroFeedbackUrl` / `BuildConfig.FEEDBACK_URL`. An empty weather `baroBackendUrl` continues to use the direct official weather/geocoding APIs and does not disable the independent feedback service. This persistent SQLite backend remains a separate optional weather/backend implementation; it is not transplanted into Vercel's ephemeral filesystem. See [`feedback-service/README.md`](../feedback-service/README.md) for the serverless receiver and actual delivery acceptance requirements.
