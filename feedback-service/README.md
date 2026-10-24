# BARO independent Vercel feedback relay

The native app submits to this service; the service creates one public `dpan538/baro` Issue and independently fetches it before returning a receipt. The app then independently verifies the same Issue marker before showing success or clearing that exact draft revision. The service accepts only `body`, `requestId`, and the draft's original `version`.

Vercel project: `baro-feedback` in `dpan538s-projects`. Production is public for native calls; preview deployment protection remains enabled. `BARO_GITHUB_TOKEN` is a Production Secret scoped only to `dpan538/baro`, Issues read/write. Credentials are never embedded in the APK, committed, returned by health checks or borrowed from Annota. The user's Annota project is a read-only workflow reference.

`api/index.py` exposes the WSGI app. Rewrites map `/v1/feedback` and `/healthz`; the latter reports service identity and a configuration boolean only. Missing configuration, invalid input, GitHub failure or an unverified receipt is an error. No message content, credentials, contacts, device logs or location are logged or collected automatically. Submitted feedback itself is public on GitHub; the native form states this.

Requests preserve exact Unicode content and bind `SHA256(body + NUL + original_version)` to a persistent client UUID. Retrying after an ambiguous write checks recent Issues, including closed ones, before another POST. The service serializes same-instance submissions, caps repository activity at 20/hour, scans up to 500 Issues, and fails closed when that window is full. It does not promise strict distributed locking between concurrent serverless instances; a larger deployment needs a durable global idempotency store. APK upgrade retries retain the draft's original version as well as its identity.

```sh
python3 -m unittest discover -s feedback-service -p 'test_*.py' -v
```

Local tests use a controlled GitHub transport and do not prove public delivery. Publication requires an opt-in native acceptance using the actual production endpoint, a real GitHub Issue and the automatic success dialog, plus failure/draft retention checks. Do not expose or print the Production Secret during deployment or verification.
