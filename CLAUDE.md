# diamondTracker – Claude Code Projektbeschreibung

## Was ist diese App?
diamondTracker ist eine Android-App für Baseball Coaches.
Sie hilft beim Verwalten von Teams, Erstellen von Aufstellungen und Tracken von Statistiken – direkt während des Spiels.

Die App ist eine Weiterentwicklung der "PitcherApp" und wird schrittweise zu diamondTracker ausgebaut.

## Technologie
- **Sprache:** Kotlin
- **Ziel:** Android (minSdk 24, targetSdk 34)
- **Datenbank:** SQLite über eigene DatabaseHelper-Klasse
- **UI:** XML Layouts, AppCompat, RecyclerView, CardView, Material FAB
- **Package:** de.baseball.pitcher

## Projektstruktur
```
app/src/main/
├── java/de/baseball/pitcher/
│   ├── DatabaseHelper.kt        ← Zentrale DB-Klasse (alle Tabellen & Methoden)
│   ├── GameListActivity.kt      ← Hauptscreen: Spiele verwalten
│   ├── GameDetailActivity.kt    ← Spiel-Detail: Pitcher pro Spiel
│   ├── PitchTrackActivity.kt    ← Live Pitch-Tracking während des Spiels
│   ├── StatisticsActivity.kt    ← Statistik-Ansicht
│   ├── SettingsActivity.kt      ← Einstellungen-Hub (NEU)
│   ├── TeamListActivity.kt      ← Teams verwalten (NEU)
│   └── TeamDetailActivity.kt    ← Team-Detail: Positionen + Roster (NEU)
└── res/
    ├── layout/                  ← XML Layouts (activity_*, item_*, dialog_*)
    └── drawable/                ← Shapes, Badges (badge_position.xml, etc.)
```

## Datenbank (DatabaseHelper.kt)
**Aktuelle Version: 7**

### Tabellen:
- `games` – Spiele (id, date, opponent, team_id)
- `pitchers` – Pitcher pro Spiel (id, game_id, name, player_id) – player_id=0 für Freitext-Einträge
- `pitches` – Einzelne Pitches (id, pitcher_id, type, timestamp)
- `teams` – Teams (id, name) ← NEU
- `team_positions` – Aktive Positionen pro Team (team_id, position 1-10) ← NEU
- `players` – Spieler/Roster (id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year)
- `pitcher_appearances` – Pitcheinsätze (id, player_id, game_id, date, batters_faced) – UNIQUE(player_id, game_id) ← NEU

### Baseball Positionen:
1=Pitcher, 2=Catcher, 3=1B, 4=2B, 5=3B, 6=SS, 7=LF, 8=CF, 9=RF, 10=DH

## Aktuelle Features
- ✅ Spiele anlegen und verwalten
- ✅ Pitcher pro Spiel erfassen
- ✅ Live Pitch-Tracking (Ball, Strike, Batter Faced) mit Undo
- ✅ Statistiken (BF, Balls, Strikes, Strike%)
- ✅ Einstellungen-Screen
- ✅ Teams anlegen mit Name und aktiven Positionen (1-9 + DH)
- ✅ Spieler/Roster pro Team verwalten (Name, Trikotnummer, Hauptposition)

## Nächste geplante Features
- Lineup/Aufstellung pro Spiel (Spieler auf Positionen einteilen)
- Batting Statistiken (Hits, RBI, AVG)
- Spielstand tracken (Inning by Inning)
- Export/Import Teams & Roster (JSON oder CSV) – getrennt von App-Einstellungen
- Export/Import App-Einstellungen (JSON) – getrennt von Teams/Roster
- Export Spielstatistiken (PDF oder CSV)

## Coding-Konventionen
- Alle Texte auf **Deutsch** (UI) und **Englisch** (Code/Variablen)
- DB-Version bei neuen Tabellen/Spalten immer erhöhen + onUpgrade() anpassen
- Neue Activities immer im AndroidManifest.xml eintragen
- RecyclerView mit eigenem Adapter und ViewHolder
- Farben: Primär #1a5fa8 (Blau), Akzent #c0392b (Rot)

## Wichtige Hinweise
- Die App soll **einfach während eines Spiels bedienbar** sein → große Buttons, wenig Tipp-Aufwand
- Kein Internet erforderlich, alles lokal auf dem Gerät
- Zielgruppe: Baseball Coaches (nicht zwingend technikaffin)
