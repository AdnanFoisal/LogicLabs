# Logic Labs

A digital logic laboratory in your pocket — a faithful, fully offline simulation of the
K&H IDL-800A Digital Lab trainer, built for real coursework: place 74xx DIP packages on
a true-to-topology AD-200 breadboard, wire them with sagging jumpers, drive them from
the console's switches, pulsers and clock, and verify your builds against sealed
truth-table experiments.

## What's inside

- **The bench.** A 2.5D Compose-Canvas breadboard (64 tie-point columns, DIP trench,
  power rails) with pan/pinch/loupe-assisted wiring, chip drag and rotation, and
  tri-state-aware simulation (HIGH / LOW / Z / conflict-burnout) over a DSU netlist.
- **The console.** Eight SPDT logic switches, two debounced pulsers, eight buffered LED
  monitors with persistence-of-vision afterglow, a stepped clock (0.5 Hz – 100 kHz) and
  two BCD seven-segment displays — all with mechanical motion, synthesized sound and
  haptics.
- **Coursework.** Twelve sealed classic labs plus extended experiments (universal-gate
  builds, arithmetic, converters, sequential logic), each with objectives and an
  automated truth-table verifier that seals a shareable, HMAC-stamped PDF report.
- **A finished app around it.** An animated power-on splash, an instrument-rack home
  with CONTINUE and course progress, full settings (dark AMOLED and light benches,
  sound, haptics, afterglow/bloom, wire defaults), saved projects with thumbnails,
  autosave and crash recovery, JSON project export/import, and quiet achievements.

Everything runs offline. No accounts, no network, no tracking.

## Module map

| Module | Owns |
| --- | --- |
| `:app` | Shell: navigation, splash, home, settings, bench orchestration, HUD and sheets |
| `:core-digital` | The Digital simulation core (event-driven engine primitives) |
| `:core-bridge` | The bench's circuit model, netlist, AD-200 topology, TTL catalog |
| `:core-data` | Persistence: settings, session, progress/badges, projects (DataStore + files) |
| `:core-designsystem` | Theme tokens, motion vocabulary, chassis/glass components, icons |
| `:feature-breadboard` | The canvas: painters, geometry, gestures, hit-testing |
| `:feature-instruments` | The console: rockers, LEDs, clock dial, seven-segment modules |
| `:feature-tools` | Verifier, oscilloscope, courseware, certificates, feedback |
| `:core-testing` | Self-contained verification suites (tiered coverage) |
| `:hardware-hal` | Phone-sensor bridge for physical-input experiments |

## Building

```bash
gradlew.bat :app:assembleDebug     # debug APK
gradlew.bat :app:assembleRelease   # R8-shrunk release APK
gradlew.bat test                   # JVM verification suites
```

Requires JDK 17 and an Android SDK with platform 35 (`local.properties` pointing at
it). Target device: Android 8.0+ (API 26); tuned for 1080×2400 AMOLED phones.

## License

**GPL-3.0-or-later** — see [LICENSE](LICENSE). The simulation kernel in `:core-digital`
is derived from [hneemann/Digital](https://github.com/hneemann/Digital) (GPLv3), which
sets the license for the whole app. Bundled fonts and other third-party material are
covered in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
