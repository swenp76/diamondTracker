# diamond9

> **Early Development** — This app is in an early stage of development. Features may be incomplete, change without notice, or contain bugs.

**Baseball Coach App for Android**

diamond9 helps baseball coaches manage their team during a game — track pitchers, build lineups, and log pitches in real time, all without internet access.

This project is built with AI-assisted development (vibe coding) using [Claude Code](https://claude.ai/code).

---

## Features

- **Team management** — Create teams, manage rosters with jersey numbers, positions, and birth year
- **Game management** — Create and manage games per team, with optional start time and home/away flag
- **Live pitch tracking** — Log Ball, Strike, Foul, HBP, Walk, K per batter with undo support
- **Pitcher stats** — Batters faced, balls, strikes, strike percentage, innings pitched (IP)
- **Batting tracking** — Log at-bat results (Hit, Walk, K, HBP, Out) per player per game
- **Scoreboard** — Inning-by-inning run tracking for both teams (9 innings)
- **Game timer** — Stopwatch per game with pause/resume, survives app restarts
- **Per-game batting stats** — AB, H, BB, K, HBP per batter for a single game
- **Season stats** — Aggregated batting (AB/H/AVG/BB/K) and pitching (BF/P/S%/BB/K/IP) across all games
- **Own lineup** — Starters and substitutes with substitution tracking
- **Opponent lineup** — Batting order, bench, and substitutions
- **Team export/import** — Share rosters as JSON
- **Per-game export/import** — Share complete game data (lineup, at-bats, pitches, scoreboard) as JSON
- **Offline-first** — Everything stored locally on device, no internet required
- **Multilingual** — English and German

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
3. Run on an emulator or physical device (minSdk 26, targetSdk 35)

```bash
./gradlew assembleDebug
```

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Database:** Room (SQLite)
- **Navigation:** Compose Navigation

## Roadmap

- Season management (group games by season)
- Configurable innings per team (7 / 9 / custom)
- Full database backup/restore (all teams and games)
- RBI tracking
- More translations

## License

See [LICENSE](LICENSE).

## Disclaimer

This app is provided as-is for personal use by baseball coaches.
