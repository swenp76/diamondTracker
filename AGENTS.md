# diamond9 – Claude Code Projektbeschreibung

## Was ist diese App?
diamond9 ist eine Android-App für Baseball Coaches.
Sie hilft beim Verwalten von Teams, Erstellen von Aufstellungen und Tracken von Statistiken – direkt während des Spiels.

## Technologie
- **Sprache:** Kotlin
- **Ziel:** Android (minSdk 26, targetSdk 36)
- **Datenbank:** Room (SQLite) über DAOs + DatabaseHelper-Wrapper
- **UI:** Jetpack Compose (Material 3)
- **Package:** de.baseball.diamond9
- **DB-Datei:** `pitcher.db` (Version 21)
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
├── GameMatcher.kt            ← Game deduplication and merging logic
├── ManageOpponentsActivity.kt
├── OpponentLineupActivity.kt
├── OwnLineupActivity.kt
├── PitchTrackActivity.kt     ← Defense / Pitching
├── PitcherListActivity.kt
├── PitcherTrendHelper.kt     ← Statistik-Hilfsfunktionen (buildBatterStats, getTrendLevel, …)
├── PlayerMatcher.kt          ← Player deduplication logic (Levenshtein distance)
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

Checkliste bei neuer DB-Version:
- [ ] Neue Migration in `AppDatabase.kt` angelegt (`MIGRATION_X_Y`)
- [ ] `version` in `@Database(...)` erhöht
- [ ] Entsprechende Backup-Migration in `BackupManager` ergänzt
- [ ] `dbVersion` im Backup-Export aktualisiert
- [ ] Restore-Logik für die neue Version getestet

### 2. Keine destruktive Migration

`fallbackToDestructiveMigration()` darf **nicht** verwendet werden.
Jede DB-Versionsänderung braucht eine explizite Migration.

### 3. Farbkonsistenz

Alle Farben über `colors.xml` referenzieren:
```xml
<color name="color_primary">#1a5fa8</color>
<color name="color_strike">#c0392b</color>
<color name="color_ball">#1a5fa8</color>
<color name="color_green">#2c7a2c</color>
<color name="color_purple">#7d3c98</color>
<color name="color_orange">#d35400</color>
<color name="color_text_primary">#333333</color>
<color name="color_text_secondary">#888888</color>
```

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
- ✅ **#7** Spielübergreifende Statistiken (SeasonStatsActivity): Batter-Tab (PA/AB/H/AVG/OBP/BB/K) + Pitcher-Tab (BF/P/S%/BB/K) – inkl. Datumsfilter (Von/Bis)
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
- ✅ Unit-Tests: PitcherTrendHelperTest (32 Tests), GameBatterStatsDaoTest (11 Tests), BackupManagerMigrationsTest (19 Tests)
- ✅ Export-Verbesserung: PDF/JPG mit Headlines ("Batting stats", "Pitching stats") und Spiel-Metadaten (Zeit, Nr.)
- ✅ Security: Backup-Sicherheits-Checks (Path Traversal Schutz, String-Längen-Validierung 50/3/20/10)
- ✅ **#21** Datenintegrität: `PlayerMatcher` verhindert Dubletten beim Import (Levenshtein-Distanz für Namen + Trikotnummern-Abgleich).
- ✅ **#22** Game Merging: `GameMatcher` erkennt Dubletten (gleiches Datum/Gegner) und erlaubt Zusammenführen inkl. Undo.
- ✅ **#23** Foreign Key CASCADE: Migration 18 aktiviert CASCADE-Löschung für At-Bats/Pitches/Pitcher bei Spiel-Löschung.
- ✅ **#24** Pitcher Name Resolution: Fix für "Unknown"-Namen in Statistiken durch SQL JOIN auf Roster-Tabelle.

---

## Offene Aufgaben (nach Priorität)

### 🔴 Hoch

- ✅ **#3** Cleanup: NavDrawer Einstellungen (Teams/Opponents entfernt)

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

#### #21 – Datenintegrität: Spieler-Doubletten beim Import verhindern
Beim Importieren von Spielen oder Wiederherstellen von Backups prüfen, ob ein Spieler mit demselben Namen/Nummer bereits im Team existiert. Aktuell könnten Duplikate mit unterschiedlichen IDs entstehen, wenn dasselbe Team auf verschiedenen Geräten angelegt wurde.
Logik: Vor dem Insert prüfen (`SELECT id FROM players WHERE team_id = :t AND name = :n AND number = :nr`) und ggf. vorhandene ID verwenden statt neu anzulegen.

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

## Datenbankschema (Version 19)

| Tabelle | Wichtige Felder |
|---------|----------------|
| `teams` | id, name |
| `team_positions` | team_id, position |
| `players` | id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year |
| `games` | id, date, opponent, team_id, inning, outs, leadoff_slot, **start_time**, **elapsed_time_ms**, **game_time**, **is_home**, **current_inning**, **is_top_half** |
| `pitchers` | id, game_id, name, player_id |
| `pitches` | id, **pitcher_id (nullable)**, **at_bat_id (nullable)**, type, sequence_nr, inning |
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
| 15 → 16 | `pitches.type` null backfill | ✅ |
| 16 → 17 | `pitches.at_bat_id` null backfill (0L) | ✅ |
| 17 → 18 | `FOREIGN KEY` + `ON DELETE CASCADE` (games/at_bats/pitchers/pitches) | ✅ |
| 18 → 19 | `pitches` Tabelle: `pitcher_id` und `at_bat_id` nullable (Fix für Offense Tracking) | ✅ |
| 19 → 20 | `game_runners` Tabelle hinzugefügt | ✅ |
| 20 → 21 | `is_locked` Feld in `games` Tabelle | ✅ |

**Hinweis:** Jede Migration hier eintragen und gleichzeitig `BackupManager` aktualisieren.
