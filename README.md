<div align="center">

# ⚡ Logic Labs
### The Digital Logic Laboratory in Your Pocket.

**A faithful, fully offline simulation of the legendary K&H IDL-800A Digital Lab trainer.**
Place 74xx DIP packages on a true-to-topology breadboard, wire them with sagging jumpers, drive them from the console, and verify your builds against sealed truth-table experiments.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://developer.android.com)

</div>

---

## 📸 The App in Action

| | | |
| :---: | :---: | :---: |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01_home.png" width="260"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02_bench_full_adder.png" width="260"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03_oscilloscope.png" width="260"> |
| Instrument-rack home & course progress | Full-adder on the bench | The oscilloscope sheet |

> *"No accounts, no network, no tracking. Just pure, unadulterated digital logic."*

Logic Labs is not a toy simulator. It is a university-level **digital twin** of physical hardware, engineered natively for touchscreens and secured for academic coursework.

---

## 🚀 Why Logic Labs?

Most mobile electronics apps use flat, 2D schematic symbols. Logic Labs simulates the **actual physical topology** of the AD-200 breadboard and the IDL-800A console.

### 🧩 1. True-to-Topology 2.5D Breadboard
* **1,896 physical sockets** simulated with a high-performance Disjoint-Set Union (DSU) netlist engine.
* **Sagging jumpers:** wires have weight and geometry. You aren't just connecting Node A to Node B; you are physically wiring a breadboard.
* **Tri-state awareness:** the engine simulates HIGH, LOW, high-impedance (Z) and **conflict burnout**. Wire two outputs together in PRACTICAL mode and the chips will burn out — route your power rails like the real bench.
* **14 datasheet-exact 74xx parts:** 7400, 7402, 7404, 7408, 7410, 7411, 7420, 7432, 74266, 7483, 7486, 7474, 7476, 7448 — gates, flip-flops, adders and a BCD-to-seven-segment decoder.

### 🎓 2. Cryptographic Academic Integrity
* **Sealed truth-table verifier:** sweep the full input space of your build and seal the result with an **HMAC-SHA256 provenance hash**.
* **PDF certificates:** export an A4 lab report of your working circuit. Perfect for professors grading remote lab work — no fake screenshots, just cryptographic proof of a working build.
* **Coursework:** twelve sealed classic labs plus eighteen extended experiments — universal-gate builds, arithmetic, code converters, multiplexers, sequential circuits and DACs — each with objective checklists.

### 🎨 3. Sensory Engineering
* **Persistence-of-vision:** LEDs simulate optical decay and afterglow when driven by high-frequency clocks.
* **Synthesized console audio & haptics:** debounced pulsers, synthesized click/pop sounds and haptic feedback make the app feel like a piece of vintage lab equipment — all individually toggleable.
* **Nine hardware themes:** choose between **Obsidian Stealth**, **Amber CRT**, **HP Slate**, **Cleanroom White** and more to match your lab environment.

### 🔒 4. Zero-Compromise Offline Architecture
* **Local-first:** your projects never leave your device — no network permission, no accounts, no tracking, no ads.
* **Autosave & crash recovery:** never lose a 3-hour lab session.
* **JSON export/import:** share your circuit topologies with classmates, or keep them as plain, readable files you control.

---

## 🧰 The Bench & The Console

| **The Breadboard (AD-200)** | **The Console (IDL-800A)** |
| :--- | :--- |
| 64-column terminal strips, DIP trench, seven distribution rails. | 8 SPDT logic switches (SW0–SW7) with mechanical motion. |
| Pan, pinch and loupe-assisted wiring with 2.2× magnifier. | 2 debounced pulsers for manual clocking. |
| Chip drag, rotation and datasheet-exact pinouts. | 8 buffered LED monitors with afterglow. |
| 2.5D rendering with custom Compose Canvas painters. | Stepped clock from 0.5 Hz to 100 kHz. |
| Boolean diagram view with live net colouring. | 2 BCD-driven seven-segment displays. |

---

## 🛠️ For Developers & Contributors

Logic Labs is built with a **10-module Gradle architecture** to keep the codebase clean, testable and fast to build.

### Tech Stack
* **UI:** Jetpack Compose, Canvas, Material 3
* **Architecture:** Composition-first state holders + a hand-rolled `AppContainer` for dependency injection (no Dagger/Hilt bloat)
* **Simulation core:** event-driven engine primitives in `:core-digital`, DSU netlist solver in `:core-bridge`
* **Data:** DataStore for preferences, file-backed JSON for project persistence
* **Language:** Kotlin 71% / Java 29% (the simulation kernel is Java)

### Module Map
| Module | Owns |
| :--- | :--- |
| `:app` | Shell: navigation, splash, home, settings, bench orchestration, HUD and sheets |
| `:core-digital` | The Digital simulation core — event-driven engine primitives (pure JVM) |
| `:core-bridge` | The bench's circuit model, DSU netlist, AD-200 topology, 74xx TTL catalog |
| `:core-data` | Persistence: settings, session, progress/badges, projects (DataStore + files) |
| `:core-designsystem` | Design system: nine hardware palettes, tokens, chassis/glass components, icons |
| `:feature-breadboard` | The canvas: painters, geometry, gestures, hit-testing |
| `:feature-instruments` | The console: rockers, LEDs, clock dial, seven-segment modules |
| `:feature-tools` | Verifier, oscilloscope, courseware, certificates, feedback (sfx/haptics) |
| `:hardware-hal` | Phone-sensor bridge for physical-input experiments |
| `:core-testing` | Self-contained verification suites (tiered coverage) |

### Building from Source
Requires **JDK 17** and an Android SDK with **platform 35** (`local.properties` pointing at it). Target device: Android 8.0+ (API 26), tuned for 1080×2400 AMOLED phones.

```bash
# Clone the repository
git clone https://github.com/AdnanFoisal/LogicLabs.git

# Build the debug APK
./gradlew :app:assembleDebug

# R8-shrunk release APK
./gradlew :app:assembleRelease

# Run the JVM verification suites
./gradlew test
```

---

## 🗺️ Roadmap (V2.0 and Beyond)
We are constantly expanding the lab. Planned additions include:
* **Passives:** pull-up/pull-down resistors, bypass capacitors, and the legendary NE555 timer.
* **Advanced peripherals:** 4×4 hex keypads, raw LEDs, and tactile pushbuttons.
* **Debug tools:** a logic probe (tap any wire to see its state) and an 8-channel logic analyzer.
* **Real-world physics:** switch bounce simulation and floating-input noise pickup.

---

## 🤝 Contributing
Contributions are welcome! If you are an electrical engineering student, a professor, or an Android developer, your perspective is valuable.
1. **Found a bug?** Open an Issue.
2. **Want to add a new 74xx chip?** Check out `:core-bridge` and submit a PR!
3. **UI/UX ideas?** Let's discuss in the Discussions tab.

---

## 📜 License
**GPL-3.0-or-later** — see [LICENSE](LICENSE).
The simulation kernel in `:core-digital` is derived from the incredible open-source project **[hneemann/Digital](https://github.com/hneemann/Digital)** (GPLv3), which sets the license for the whole app. Bundled fonts (Inter, JetBrains Mono) are under the SIL OFL 1.1 — full details in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<div align="center">

**Made with ❤️ and a lot of boolean algebra.**

[![Stars](https://img.shields.io/github/stars/AdnanFoisal/LogicLabs?style=social)](https://github.com/AdnanFoisal/LogicLabs)
[![Forks](https://img.shields.io/github/forks/AdnanFoisal/LogicLabs?style=social)](https://github.com/AdnanFoisal/LogicLabs/fork)

</div>
