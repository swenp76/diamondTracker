# diamond9 – Claude Code Projektbeschreibung

## Was ist diese App?
diamond9 ist eine Android-App für Baseball Coaches.
Sie hilft beim Verwalten von Teams, Erstellen von Aufstellungen und Tracken von Statistiken – direkt während des Spiels.

## Technologie
- **Sprache:** Kotlin
- **Ziel:** Android (minSdk 26, targetSdk 34)
- **Datenbank:** Room (SQLite) über DAOs + DatabaseHelper-Wrapper
- **UI:** XML Layouts, AppCompat, RecyclerView, CardView, Material ExtendedFAB
- **Package:** de.baseball.diamond9
- **Lokalisierung:** Deutsch (Fallback, `values/`), Englisch (`values-en/`)

## Projektstruktur
```
app/src/main/
├── java/de/baseball/diamond9/
│   ├── DatabaseHelper.kt          ← Wrapper um Room-DAOs (alle DB-Zugriffe)
│   ├── CoachAct.kt                ← Einstieg: Team auswählen
│   ├── GameListActivity.kt        ← Spiele eines Teams verwalten
│   ├── GameHubActivity.kt         ← Spiel-Hub: Offense / Defense / Lineup
│   ├── PitcherListActivity.kt     ← Pitcher pro Spiel
│   ├── PitchTrackActivity.kt      ← Live Pitch-Tracking während des Spiels
│   ├── StatsActivity.kt           ← Statistik-Ansicht
│   ├── OwnLineupActivity.kt       ← Eigene Aufstellung verwalten
│   ├── OpponentLineupActivity.kt  ← Gegner-Aufstellung verwalten
│   ├── SettingsActivity.kt        ← Einstellungen-Hub
│   ├── TeamListActivity.kt        ← Teams verwalten
│   ├── TeamDetailActivity.kt      ← Team-Detail: Positionen + Roster
│   └── db/
│       ├── AppDatabase.kt         ← Room-Datenbank (Version 1)
│       ├── GameDao.kt
│       ├── PitcherDao.kt
│       ├── PlayerDao.kt
│       ├── TeamDao.kt
│       └── LineupDao.kt
└── res/
    ├── layout/                    ← XML Layouts (activity_*, item_*, dialog_*)
    ├── values/strings.xml         ← Deutsch (Fallback)
    ├── values-en/strings.xml      ← Englisch
    └── drawable/                  ← Shapes, Badges
```

## Datenbank (Room)
**AppDatabase Version: 1**

### Entities / Tabellen:
- `games` – Spiele (id, date, opponent, team_id)
- `pitchers` – Pitcher pro Spiel (id, game_id, name, player_id) – player_id=0 für Freitext
- `pitches` – Einzelne Pitches (id, pitcher_id, type, sequence_nr)
- `teams` – Teams (id, name)
- `team_positions` – Aktive Positionen pro Team (team_id, position 1-10)
- `players` – Spieler/Roster (id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year)
- `pitcher_appearances` – Pitcheinsätze (id, player_id, game_id, date, batters_faced)
- `opponent_lineup` – Gegner Batting Order (game_id, batting_order, jersey_number)
- `opponent_bench` – Gegner Bank (id, game_id, jersey_number)
- `opponent_substitutions` – Gegner-Wechsel
- `own_lineup` – Eigene Aufstellung (game_id, slot, player_id)
- `substitutions` – Eigene Wechsel

### Baseball Positionen:
1=Pitcher, 2=Catcher, 3=1B, 4=2B, 5=3B, 6=SS, 7=LF, 8=CF, 9=RF, 10=DH

## App-Flow
```
CoachAct  →  GameListActivity (gefiltert nach Team)
                              ↓
                        GameHubActivity
                       ↙      ↓       ↘
            PitcherList  OwnLineup  OpponentLineup
                ↓
          PitchTrackActivity
```

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

## Nächste geplante Features
- Batting Statistiken (Hits, RBI, AVG)
- Spielstand tracken (Inning by Inning)
- Export Spielstatistiken (PDF oder CSV)
- Export/Import App-Einstellungen (JSON)

## Coding-Konventionen
- Alle UI-Texte in `strings.xml` (nie hardcoded), beide Sprachdateien pflegen
- Alle Texte auf **Deutsch** (UI) und **Englisch** (Code/Variablen)
- DB-Version bei neuen Tabellen/Spalten erhöhen + Migration in `AppDatabase` ergänzen
- Neue Activities immer im `AndroidManifest.xml` eintragen
- RecyclerView mit eigenem Adapter und ViewHolder
- FABs als `ExtendedFloatingActionButton` mit Icon + Label
- Farben: Primär #1a5fa8 (Blau), Akzent #c0392b (Rot)

## Wichtige Hinweise
- Die App soll **einfach während eines Spiels bedienbar** sein → große Buttons, wenig Tipp-Aufwand
- Kein Internet erforderlich, alles lokal auf dem Gerät
- Zielgruppe: Baseball Coaches (nicht zwingend technikaffin)

## Entwicklungsregeln

### Backup-Migration (PFLICHT)
Jede neue Room-Migration (`MIGRATION_X_Y` in `AppDatabase.kt`) zieht
immer auch eine entsprechende Backup-Migration in `BackupManager` nach
sich. Beide müssen in demselben Commit landen.

Checkliste bei neuer DB-Version:
- [ ] Neue Migration in `AppDatabase.kt` angelegt
- [ ] Entsprechende Backup-Migration in `BackupManager` ergänzt
- [ ] `dbVersion` im Backup-Export aktualisiert
- [ ] Restore-Logik für die neue Version getestet
