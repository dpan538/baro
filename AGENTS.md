# BARO project instructions

- Current delivery is the browser weather-screen prototype. Do not build, package or publish APKs unless the user explicitly resumes Android work. Preserve the existing Android scaffold and historical local output.
- Interface language is Chinese OR English. Do not restore simultaneous bilingual labels or a third language option.
- Keep seven primary settings in three groups. Marketing copy belongs in GitHub description/README, not inside settings. Secondary cache/source/feedback tools live in the footer menu.
- Keep typography in the shared CSS roles/tokens and weather icons in the monoline SVG system. Prefer shared grid tracks and explicit centering over per-item offsets. Preserve the compact, baseline-aligned temperature unit and the title-sized gear without a circular focus ring.
- Run relevant source checks for behavior changes and inspect actual rendered output for visual changes. Do not add tests merely mirroring CSS/SVG. Do not claim native-device validation from a browser check.
- Every pushed commit must have an informative English title AND a detailed body explaining the problem, changes and actual validation. Finish with a short Chinese summary. A title-only commit does not satisfy the user's requested repository history format.
- Keep secrets, local paths, SDKs, keys, build caches and generated packages out of Git. Font bytes and their original licenses must be preserved.
