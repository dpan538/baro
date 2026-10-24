# Privacy

The Kotlin Android app has no account, analytics, ads or background weather polling. Preferences, selected place, forecast cache and feedback drafts remain in app-private storage. Clearing weather cache preserves preferences and drafts. The manifest disables backup; no general storage permission is requested.

Weather requests send selected coordinates to the dedicated BARO relay, or to Open-Meteo directly in evaluation builds without a configured relay. The relay forwards validated, rounded coordinates to fixed Open-Meteo endpoints and shares cached results. Weather and air quality are model estimates. City search sends the entered city name to Open-Meteo geocoding (GeoNames).

Location is off by default. Enabling it asks for Android foreground location, accepts approximate permission, and stops requests on pause or after a bounded single-fix attempt. Only a successful fix changes the forecast place. Denial, system-off, timeout and unavailable providers retain the existing valid place. No background-location permission is declared. Coordinates are not included in feedback.

Feedback is sent only after tapping Submit. It becomes a **public GitHub Issue in dpan538/baro**; the interface discloses this. Only message text, app version and random request ID are sent. Do not include private information. No contacts, precise location, screenshots, attachments, logs or device identifiers are attached automatically. GitHub write credentials remain on the dedicated server. Unconfigured service, failures, cancellation and unverified receipts retain the draft. Only a matching receipt, independently verified against GitHub, clears that submitted revision.

Update checks run only on user action and use public GitHub release metadata. APK downloads are user initiated, cancellable and verified before the Android system installer is opened. External links open their HTTPS destination through the system browser. These services receive the normal network information associated with those requests.

The frozen browser design remains a labelled, offline sample-data prototype; its older Java host/privacy description is historical. The separate public BARO feedback relay is verified in [preview.6-r2 acceptance](../android/verification/preview-0.10.0-preview.6-r2.json). This does not deploy the optional shared weather backend; the release fetches real weather directly.


### BARO feedback relay

When the user explicitly submits feedback, the native app sends only the entered text, the draft's request identity and original app version over HTTPS to the separate BARO Vercel service. The message is published as a public Issue in `dpan538/baro`, as stated above the form. The service's GitHub credential remains in Vercel Production and is not shipped in the APK. The service and app independently verify the Issue receipt before acknowledging delivery; failed/cancelled drafts and their identities remain local. Weather/location requests do not pass through this feedback-only endpoint. The service applies a modest repository submission limit and bounded reconciliation; strict cross-instance exactly-once delivery is not claimed.
