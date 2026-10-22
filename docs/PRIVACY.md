# Privacy

The current preview has no account, analytics, advertising SDK or background weather polling. Weather values are labelled samples. Interface preferences stay in private device storage; data-cache clearing leaves those preferences intact.

Android location is off by default. Enabling it requests foreground location, follows the permission level granted by Android, and stops updates when the activity is not visible. Coordinates are not sent to a weather service in this version and are not included in copied feedback. Browser previews do not ask for location.

Opening a data-source, update or feedback link sends normal navigation to that destination through your browser. Submitting a GitHub Issue is a separate action in GitHub; copying feedback only places your draft, app version and display preferences on the clipboard. Nothing is posted automatically. Public GitHub issues are visible to other people.

The bundled APK currently has no Internet permission. Live forecast fetching and any future cache or location transfer must update this document when implemented.
