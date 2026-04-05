# diamond9

> **Early Development** — This app is in an early stage of development. Features may be incomplete, change without notice, or contain bugs.

**Baseball Coach App for Android**

diamond9 helps baseball coaches manage their team during a game — track pitchers, build lineups, and log pitches in real time, all without internet access.

---

## Features

- **Team management** — Create teams, manage rosters with jersey numbers, positions, and birth year
- **Game management** — Create and manage games per team
- **Live pitch tracking** — Log Ball, Strike, Foul, HBP, Walk, K per batter with undo support
- **Pitcher stats** — Batters faced, balls, strikes, strike percentage
- **Own lineup** — Starters and substitutes with substitution tracking
- **Opponent lineup** — Batting order, bench, and substitutions
- **Team export/import** — Share rosters as JSON
- **Offline-first** — Everything stored locally on device, no internet required
- **Multilingual** — English (default) and German; more languages coming soon

## Screenshots

_Coming soon_

## Requirements

- Android 8.0 (API 26) or higher

## Installation

Download the latest APK from the [Releases](../../releases) page and install it on your Android device.

> You may need to enable *Install from unknown sources* in your Android settings.

## Build from source

1. Clone the repository
2. Open in Android Studio
3. Run on an emulator or physical device (minSdk 26, targetSdk 34)

```bash
./gradlew assembleDebug
```

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Database:** Room (SQLite)
- **Navigation:** Compose Navigation

## Roadmap

- Batting statistics (Hits, RBI, AVG)
- Score tracking (inning by inning)
- Export game stats (PDF or CSV)
- Settings export/import (JSON)
- More translations

## License

See [LICENSE](LICENSE).

## Disclaimer

This app is provided as-is for personal use by baseball coaches.
