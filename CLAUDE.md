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
- **DB-Datei:** `pitcher.db`
- **Lokalisierung:** Deutsch (Fallback, `values/`), Englisch (`values-en/`)

## Projektstruktur
```
diamond9/src/main/java/de/baseball/diamond9/
├── db/
│   ├── AppDatabase.kt        ← Room-Datenbank, Migrations, Version 7
│   ├── AtBatDao.kt
│   ├── GameDao.kt
│   ├── LineupDao.kt
│   ├── OpponentTeamDao.kt
│   ├── PitcherDao.kt
│   ├── PlayerDao.kt
│   ├── ScoreboardDao.kt      ← neu: scoreboard_runs
│   └── TeamDao.kt
├── AppNavigation.kt          ← NavDrawer (AppDrawer Composable)
├── BackupManager.kt          ← Backup/Restore (JSON, dbVersion 7)
├── BattingTrackActivity.kt   ← Offense / Batting
├── CoachAct.kt               ← Startseite
├── DatabaseHelper.kt         ← Wrapper über alle DAOs + Entities
├── GameHubActivity.kt        ← Spiel-Hub (Offense/Defense/Lineup + Scoreboard + Spieluhr)
├── GameListActivity.kt
├── ManageOpponentsActivity.kt
├── OpponentLineupActivity.kt
├── OwnLineupActivity.kt
├── PitchTrackActivity.kt     ← Defense / Pitching
├── PitcherListActivity.kt
├── PitcherTrendHelper.kt     ← Statistik-Hilfsfunktionen
├── SeasonStatsActivity.kt    ← neu: Saison-Statistiken (Batter + Pitcher)
├── SettingsActivity.kt
├── StatsActivity.kt          ← Pitcher-Statistiken (pro Spiel)
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
- ✅ **#7** Spielübergreifende Statistiken (SeasonStatsActivity): Batter-Tab (AB/H/AVG/BB/K) + Pitcher-Tab (BF/P/S%/BB/K)
- ✅ **#9** Scoreboard im GameHub (scoreboard_runs-Tabelle, 9 Innings, Away/Home, klickbare Zellen)
- ✅ **#10** Spieluhr im GameHub (start_time in games, aufwärts zählend, überlebt App-Neustart)

---

## Offene Aufgaben (nach Priorität)

### 🔴 Hoch

#### #5 – Feature: Batter-Statistik (pro Spiel)
Die `at_bats`-Tabelle mit `result`-Feld (K, BB, H, HBP, OUT) ist bereits vorhanden.

Zu implementieren:
- Pro At-Bat: K, BB, H, Out erfassen (bereits teilweise vorhanden)
- Neuer Screen `BatterStatsActivity` oder Tab in `StatsActivity`:
  aggregiert pro Spieler über ein Spiel: AB, H, AVG, BB, K
- Neuer DAO-Query in `AtBatDao`:
  ```sql
  SELECT player_id,
    COUNT(*) as ab,
    SUM(CASE WHEN result = 'H' THEN 1 ELSE 0 END) as hits,
    SUM(CASE WHEN result = 'BB' THEN 1 ELSE 0 END) as walks,
    SUM(CASE WHEN result = 'K' THEN 1 ELSE 0 END) as strikeouts
  FROM at_bats WHERE game_id = :gameId GROUP BY player_id
  ```

---

#### #6 – Feature: IP (Innings Pitched)
**Datei:** `DatabaseHelper.kt`, `StatsActivity.kt`

Berechnung: `outs / 3` ganze Innings, `outs % 3` Rest → Darstellung z.B. `2.1`
```kotlin
fun formatIP(outs: Int): String = "${outs / 3}.${outs % 3}"
```
Outs = At-Bats mit `result = "K"` oder `result = "OUT"` für diesen Pitcher.

Neuer Query in `AtBatDao` oder `PitcherDao`:
```sql
SELECT COUNT(*) FROM at_bats
WHERE game_id = :gameId AND result IN ('K', 'OUT')
```

In `PitcherStats` neues Feld `ip: String` ergänzen und in `StatsActivity` anzeigen.

---

### 🟡 Mittel

#### #1 – Design: Farbkonsistenz
Alle hardcodierten Hex-Farben in allen Composables durch `colorResource(R.color.X)` ersetzen.
Farben in `colors.xml` zentralisieren (siehe Farbdefinitionen oben).

---

#### #2 – Bug: Gegner teamabhängig
**Datei:** `AppNavigation.kt`, `DatabaseHelper.kt`

- `NavItem.Opponents` im Drawer nur anzeigen wenn ein Team aktiv ausgewählt ist
- `opponent_teams`-Tabelle um `team_id`-Spalte erweitern (DB-Migration)
- `ManageOpponentsActivity` filtert Gegner nach aktivem Team

---

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

#### #18 + #19 + #20 – Feature: Backup / Restore (Skeleton vorhanden)

`BackupManager.kt` ist angelegt (DB-Version 7, Export-Skeleton).
Noch zu implementieren:
- Export aller verbleibenden Tabellen (players, pitchers, pitches, at_bats, lineups, …)
- Export in Datei via `ActivityResultContracts.CreateDocument`
- Import via `ActivityResultContracts.OpenDocument`
- Einstiegspunkt in `SettingsActivity`

Restore-Reihenfolge (Foreign-Key-sicher):
`teams → players → games → pitchers → pitches → at_bats → lineups → scoreboard_runs → …`

---

## Datenbankschema (Version 7)

| Tabelle | Wichtige Felder |
|---------|----------------|
| `teams` | id, name |
| `team_positions` | team_id, position |
| `players` | id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year |
| `games` | id, date, opponent, team_id, inning, outs, leadoff_slot, **start_time** |
| `pitchers` | id, game_id, name, player_id |
| `pitches` | id, pitcher_id, at_bat_id, type, sequence_nr, inning |
| `at_bats` | id, game_id, player_id, slot, inning, result |
| `pitcher_appearances` | id, player_id, game_id, date, batters_faced |
| `opponent_lineup` | id, game_id, batting_order, jersey_number |
| `opponent_bench` | id, game_id, jersey_number |
| `own_lineup` | game_id, slot, player_id |
| `substitutions` | id, game_id, slot, player_out_id, player_in_id |
| `opponent_substitutions` | id, game_id, slot, jersey_out, jersey_in |
| `opponent_teams` | id, name |
| **`scoreboard_runs`** | **id, game_id, inning, is_home, runs** |

**Pitch-Typen:** `B` = Ball, `S` = Strike, `F` = Foul, `BF` = Batter Faced,
`H` = Hit, `HBP` = Hit by Pitch, `W` = Walk, `K` = Strikeout

**At-Bat Results:** `K`, `BB`, `H`, `HBP`, `OUT`

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
| 7 → 8 | `seasons`-Tabelle + `season_id` in `games` (#8) | geplant |
| 8 → 9 | `innings`, `sport_type`, `max_substitutes` in `teams`; `team_id` in `opponent_teams` (#2, #11, #12) | geplant |

**Hinweis:** Jede Migration hier eintragen und gleichzeitig `BackupManager` aktualisieren.
