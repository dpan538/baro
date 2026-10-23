# Third-party notices

The repository's MIT license covers authored BARO code and engineering documents. It does not relicense the following third-party resources or grant rights to their names.

| Resource | Use | Original license / source |
| --- | --- | --- |
| MiSans | Chinese UI, original Xiaomi WOFF2 chunks bundled in the app | [Included agreement](dist/assets/fonts/MiSans/MiSans-License.pdf), [source manifest](dist/assets/fonts/MiSans/sources.json), [Xiaomi font site](https://hyperos.mi.com/font/zh/) |
| Inter | Latin text and numbers | [SIL OFL 1.1](dist/assets/fonts/Inter-OFL.txt), Inter Project Authors |
| Roboto Mono | Monospaced labels | [SIL OFL 1.1](dist/assets/fonts/RobotoMono-OFL.txt), Roboto Mono Project Authors |
| Space Grotesk | Observation headings and geometric readouts | [SIL OFL 1.1](dist/assets/fonts/SpaceGrotesk-OFL.txt), Space Grotesk Project Authors |
| Rajdhani | Observation instrument digits | [SIL OFL 1.1](dist/assets/fonts/Rajdhani-OFL.txt), Indian Type Foundry |
| IBM Plex Mono | Observation body and terminal metadata | [SIL OFL 1.1](dist/assets/fonts/IBMPlexMono-OFL.txt), IBM Corp. |
| Noto Sans SC | Renamed BARO Instrument SC Chinese subset; original source retained | [SIL OFL 1.1](dist/assets/fonts/NotoSansSC-OFL.txt), [source and hashes](dist/assets/fonts/instrument-sources.json) |
| Gradle Wrapper | Reproducible developer build bootstrap | [Apache License 2.0](licenses/Gradle-Apache-2.0.txt), Gradle contributors |
| Open-Meteo | Weather API adapters and recorded public response fixtures | [Forecast documentation](https://open-meteo.com/en/docs), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| CAMS / Open-Meteo Air Quality | AQI adapter and recorded fixture | [Air Quality data sources](https://open-meteo.com/en/docs/air-quality-api) |

MiSans files are used only as bundled application resources, retain the original agreement and vendor bytes, and are identified in the application's Sources & Licenses screen. They are not sublicensed under MIT or offered here as a standalone font product. Do not modify or extract them for a separate font distribution.

The preview does not make live weather requests. Recorded API responses used for tests are not current observations. Weather icons and pixel-rendering code are authored in this project; no third-party icon package is bundled.
