# diamond9 – Claude Code Projektbeschreibung

## Was ist diese App?
diamond9 ist eine Android-App für Baseball Coaches.
Sie hilft beim Verwalten von Teams, Erstellen von Aufstellungen und Tracken von Statistiken – direkt während des Spiels.

## Technologie
- **Sprache:** Kotlin
- **Ziel:** Android (minSdk 26, targetSdk 35)
- **Datenbank:** Room (SQLite) über DAOs + DatabaseHelper-Wrapper
- **UI:** Jetpack Compose (Material 3)
- **Package:** de.baseball.diamond9
- **DB-Datei:** `pitcher.db` (Version 13)
- **Lokalisierung:** Deutsch (Fallback, `values/`), Englisch (`values-en/`)

## Projektstruktur
```
diamond9/src/main/java/de/baseball/diamond9/
├── db/
│   ├── AppDatabase.kt        ← Room-Datenbank, Migrations, Version 12
│   ├── AtBatDao.kt
│   ├── GameDao.kt
│   ├── LineupDao.kt
│   ├── OpponentTeamDao.kt
│   ├── PitcherDao.kt
│   ├── PlayerDao.kt
│   ├── ScoreboardDao.kt
│   └── TeamDao.kt
├── AppNavigation.kt          ← NavDrawer (AppDrawer Composable)
├── AboutActivity.kt          ← Über die App (Lizenz, GitHub)
├── BackupManager.kt          ← Backup/Restore (JSON, dbVersion 11) + Einzelspiel-Export/Import
├── BatterStatsActivity.kt    ← Batting-Statistiken pro Spiel
├── BattingTrackActivity.kt   ← Offense / Batting
├── CoachAct.kt               ← Startseite
├── DatabaseHelper.kt         ← Wrapper über alle DAOs + Entities
├── GameHubActivity.kt        ← Spiel-Hub (Offense/Defense/Lineup + Scoreboard + Spieluhr + Half-Inning Bar)
├── HalfInningManager.kt      ← Half-Inning State + Logic (pure, no Android deps)
├── GameListActivity.kt       ← Spielliste mit Import/Export pro Spiel
├── ManageOpponentsActivity.kt
├── OpponentLineupActivity.kt
├── OwnLineupActivity.kt
├── PitchTrackActivity.kt     ← Defense / Pitching
├── PitcherListActivity.kt
├── PitcherTrendHelper.kt     ← Statistik-Hilfsfunktionen (buildBatterStats, getTrendLevel, …)
├── SeasonStatsActivity.kt    ← Saison-Statistiken (Batter + Pitcher)
├── SettingsActivity.kt
├── StatsActivity.kt          ← Pitcher-Statistiken (pro Spiel, inkl. IP)
├── TeamDetailActivity.kt
└── TeamListActivity.kt
```

## Architektur-Prinzipien

- Alle DB-Zugriffe laufen über `DatabaseHelper` (nie direkt DAO aufrufen)
- Alle Entities und Query-Result-Klassen sind in `DatabaseHelper.kt` als `data class` definiert
- UI ausschließlich in Jetpack Compose (kein XML für neue Screens)
- Farben zentral in `res/values/colors.xml` – keine hardcodierten Hex-Werte in Composables
- Strings zentral in `res/values/strings.xml` – keine hardcodierten Strings in Composables

---

## Entwicklungsregeln (PFLICHT)

### 1. Backup-Migration bei jeder neuen Room-Migration
Jede neue Room-Migration (`MIGRATION_X_Y` in `AppDatabase.kt`) zieht
**immer** auch eine entsprechende Backup-Migration in `BackupManager` nach
sich. Beide müssen im **selben Commit** landen.

### 2. Keine destruktive Migration
`fallbackToDestructiveMigration()` darf **nicht** verwendet werden.
Jede DB-Versionsänderung braucht eine explizite Migration.

### 3. Farbkonsistenz
Alle Farben über `colors.xml` referenzieren. Keine hardcodierten Hex-Werte in Composables.

### 4. Test-Driven Security & Logic
Für jede neue Geschäftslogik oder Sicherheitsfunktion (z. B. Input-Validierung) **muss** ein entsprechender Test existieren:
- **Unit Tests:** Für reine Logik (z. B. `BackupManagerSecurityTest.kt`).
- **UI Tests:** Für Oberflächen-Beschränkungen (z. B. `UISecurityTest.kt`).
- **Checkliste:**
    - [ ] Input-Längenbegrenzung (meist 50 Zeichen) durch Tests abgedeckt.
    - [ ] Path-Traversal-Schutz bei Dateioperationen validiert.
    - [ ] Neue Tests laufen in der CI oder lokal via `./gradlew connectedDebugAndroidTest` erfolgreich durch.

---

## Aktuelle Features
- ✅ Team-Auswahl beim Start (CoachAct)
- ✅ Spiele pro Team anlegen und verwalten
- ✅ Pitcher pro Spiel erfassen
- ✅ Live Pitch-Tracking (Ball, Strike, Batter Faced, HBP, Walk, K, Foul) mit Undo
- ✅ Statistiken (BF, Balls, Strikes, Strike%)
- ✅ Eigene Aufstellung (Starter + Substitutes, Wechsel)
- ✅ Gegner-Aufstellung (Batting Order + Bank, Wechsel)
- ✅ Teams anlegen mit Name und aktiven Positionen (1-9 + DH)
- ✅ Spieler/Roster pro Team verwalten (Name, Trikotnummer, Position, Geburtsjahr)
- ✅ Team Export/Import (JSON)
- ✅ Einstellungen-Screen
- ✅ Mehrsprachigkeit (Deutsch/Englisch)
- ✅ Offense / Batting-Tracking (BattingTrackActivity) mit At-Bat-Ergebnissen
- ✅ **#4** Out-Button: nur Outs+1, kein Batter-Wechsel (für Stolen Base Outs etc.)
- ✅ **#7** Spielübergreifende Statistiken (SeasonStatsActivity): Batter-Tab (PA/AB/H/AVG/OBP/BB/K) + Pitcher-Tab (BF/P/S%/BB/K)
- ✅ **#9** Scoreboard im GameHub (scoreboard_runs-Tabelle, 9 Innings, Away/Home, klickbare Zellen)
- ✅ **#10** Spieluhr im GameHub (start_time in games, aufwärts zählend, überlebt App-Neustart)
- ✅ **#2** Gegner teamabhängig (opponent_teams.team_id, DB v8, NavDrawer zeigt Opponents nur bei aktivem Team)
- ✅ **#1** Farbkonsistenz: alle Hex-Farben in colors.xml zentralisiert, Composables nutzen colorResource()
- ✅ **#5** Batting Stats pro Spiel (BatterStatsActivity, GameHub-Button "Batting Stats", Spalten PA/AB/H/AVG/OBP/BB/K)
- ✅ **#6** IP (Innings Pitched): formatIP(), getOutsForGame(), ip-Feld in PitcherStats, StatCard in StatsActivity
- ✅ Uhrzeit pro Spiel: game_time-Feld (DB v9), TimePickerDialog im GameDialog, Anzeige in GameItem + GameHub-TopAppBar
- ✅ Home/Away-Kennzeichnung pro Spiel (is_home-Feld, DB v10, Toggle im GameDialog)
- ✅ Spieluhr: Pause/Resume + elapsed_time_ms-Persistierung (DB v11)
- ✅ Einzelspiel-Export/Import: vollständiges JSON (Lineup, At-Bats, Pitches, Scoreboard) aus Spielliste
- ✅ Status-Bar-Lesbarkeit: dunkle Icons in CoachAct (isAppearanceLightStatusBars)
- ✅ At-Bat Results: 2-stufiger Result-Picker (H/K/BB in 1 Tap; OUT▸ → GO/FO/LO; ··· → KL/SAC/FC/E/DP/HBP)
- ✅ Unit-Tests: PitcherTrendHelperTest (30 Tests), GameBatterStatsDaoTest (11 Tests), BackupManagerMigrationsTest (15 Tests)

---

## Offene Aufgaben (nach Priorität)

### 🔴 Hoch

### 🟡 Mittel

#### #3 – Cleanup: NavDrawer Einstellungen
**Datei:** `SettingsActivity.kt`

`SettingsCard` für "Teams" und "Opponents" aus `SettingsScreen` entfernen —
diese Einträge sind im NavDrawer bereits vorhanden. `SettingsScreen` bleibt
als leere Hülle für künftige globale Einstellungen (Backup etc.) erhalten.

---

#### #8 – Feature: Saison-Verwaltung
Neue DB-Tabelle `seasons` (→ DB-Migration):
```sql
CREATE TABLE seasons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    team_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    year INTEGER NOT NULL
)
```
Neues Feld `season_id` in `games`-Tabelle.
Neuer Screen `SeasonListActivity`. Statistik-Queries um `season_id`-Filter erweitern.

---

#### #11 – Feature: Anzahl Innings pro Team
**Datei:** `TeamDetailActivity.kt`, `DatabaseHelper.kt`

Neues Feld `innings` (Integer, Default 9) in `teams`-Tabelle (DB-Migration).
Toggle/Spinner in `TeamDetailScreen` (7 / 9 / custom).
Scoreboard (#9 ✅) liest Inning-Anzahl aus Team-Einstellungen.

---

### 🟢 Niedrig

#### #12 – Feature: Sportart Baseball / Softball
Neues Feld `sport_type` (String: "baseball" / "softball") in `teams`-Tabelle.
Toggle in `TeamDetailScreen`. Position 10 je nach Sportart: Baseball = DH, Softball = DP.

---

#### #13 – Feature: Anzahl Substitutes konfigurierbar
Neues Feld `max_substitutes` (Integer) in `teams`-Tabelle.
Eingabefeld in `TeamDetailScreen`. Lineup-Logik berücksichtigt Limit.

---

#### #14 – Architektur: Team-Settings zweigeteilt
Settings in zwei Bereiche aufteilen:
- **League Settings:** Innings, Zeitlimit, Mercy Rule, Sportart, Substitutes — importierbar/exportierbar als JSON
- **User Settings:** Teamname, Roster — nicht durch Liga-Import überschreibbar

JSON-Struktur:
```json
{
  "leagueSettings": { "innings": 7, "timeLimitMinutes": 120, "mercyRules": [...] },
  "userSettings": { "teamName": "...", "players": [...] }
}
```

---

#### #15 + #16 – Feature: Liga-Settings JSON Import/Export
Export-Funktion in `TeamDetailActivity` bereits vorhanden (`buildTeamJson`).
Auf Liga-Settings erweitern. Import über `ActivityResultContracts.OpenDocument`.

---

#### #17 – Feature: Mercy Rule
JSON-Struktur:
```json
"mercyRules": [
  { "fromInning": 3, "runDifference": 15 },
  { "fromInning": 5, "runDifference": 10 }
]
```
UI: Editierbare Liste in Liga-Einstellungen mit + / - Buttons.
Nach jedem abgeschlossenen Halbinning im Scoreboard prüfen ob Regel greift.

---

#### #18 + #19 + #20 – Feature: Backup / Restore (Teilweise implementiert)

`BackupManager.kt` ist angelegt (DB-Version 11).

Bereits implementiert:
- Einzelspiel-Export/Import: `exportGame(gameId)`, `importGame(teamId, json)` (Lineup, AtBats, Pitches, Scoreboard)
- Einstiegspunkt: `GameListActivity` (Share/Import-Menü)

Noch zu implementieren:
- Vollständiges DB-Backup aller Teams/Daten via `ActivityResultContracts.CreateDocument`
- Import via `ActivityResultContracts.OpenDocument`
- Einstiegspunkt in `SettingsActivity`

Restore-Reihenfolge (Foreign-Key-sicher):
`teams → players → games → pitchers → pitches → at_bats → lineups → scoreboard_runs → …`

---

## Datenbankschema (Version 13)

| Tabelle | Wichtige Felder |
|---------|----------------|
| `teams` | id, name |
| `team_positions` | team_id, position |
| `players` | id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year |
| `games` | id, date, opponent, team_id, inning, outs, leadoff_slot, **start_time**, **elapsed_time_ms**, **game_time**, **is_home**, **current_inning**, **is_top_half** |
| `pitchers` | id, game_id, name, player_id |
| `pitches` | id, pitcher_id, at_bat_id, type, sequence_nr, inning |
| `at_bats` | id, game_id, player_id, slot, inning, result |
| `pitcher_appearances` | id, player_id, game_id, date, batters_faced |
| `opponent_lineup` | id, game_id, batting_order, jersey_number |
| `opponent_bench` | id, game_id, jersey_number |
| `own_lineup` | game_id, slot, player_id |
| `substitutions` | id, game_id, slot, player_out_id, player_in_id |
| `opponent_substitutions` | id, game_id, slot, jersey_out, jersey_in |
| `opponent_teams` | id, name, **team_id** |
| **`scoreboard_runs`** | **id, game_id, inning, is_home, runs** |
| **`league_settings`** | **id, team_id (UNIQUE), innings (default 9), time_limit_minutes** |

**Pitch-Typen:** `B` = Ball, `S` = Strike, `F` = Foul, `BF` = Batter Faced,
`SO` = Strikeout-Pitch, `H` = Hit, `HBP` = Hit by Pitch, `W` = Walk

**At-Bat Results:** `K`, `KL` (strikeout looking), `GO`, `FO`, `LO` (outs), `BB`, `H`, `HBP`, `SAC`, `FC`, `E`, `DP` — legacy: `OUT`

---

## DB-Migrationen (Verlauf)

| Von → Nach | Änderung | Status |
|-----------|---------|--------|
| 1 → 2 | `opponent_teams`-Tabelle | ✅ |
| 2 → 3 | `inning`, `outs` in `games` | ✅ |
| 3 → 4 | `inning` in `pitches`, `leadoff_slot` in `games` | ✅ |
| 4 → 5 | `at_bat_id` in `pitches`, `at_bats`-Tabelle | ✅ |
| 5 → 6 | `scoreboard_runs`-Tabelle | ✅ |
| 6 → 7 | `start_time` in `games` | ✅ |
| 7 → 8 | `team_id` in `opponent_teams` (#2) | ✅ |
| 8 → 9 | `game_time` in `games` (Spieluhrzeit) | ✅ |
| 9 → 10 | `is_home` in `games` | ✅ |
| 10 → 11 | `elapsed_time_ms` in `games` | ✅ |
| 11 → 12 | `current_inning`, `is_top_half` in `games` (Half-Inning Tracking) | ✅ |
| 12 → 13 | `league_settings`-Tabelle (Team Hub / Liga-Settings) | ✅ |
| 13 → 14 | `game_number` in `games` | ✅ |
| 14 → 15 | `pitchers.name` null backfill | ✅ |
| 15 → 16 | `seasons`-Tabelle + `season_id` in `games` (#8) | geplant |
| 16 → 17 | `innings`, `sport_type`, `max_substitutes` in `teams` (#11, #12, #13) | geplant |

**Hinweis:** Jede Migration hier eintragen und gleichzeitig `BackupManager` aktualisieren.
