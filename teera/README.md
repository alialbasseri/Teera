# Dominoes

An Android dominoes game for playing with friends: one person creates a room, the
others join with a four-letter code, and the table plays by whatever rules the host
sets.

```
dominoes/
├── core/     pure Kotlin: the rules, the room logic, the wire protocol, board geometry
├── server/   Ktor websocket server that referees online rooms
└── app/      the Android client (Kotlin + Jetpack Compose)
```

## The point of the layout

`core` has no Android and no network code in it. That is what lets the **same engine**
run in three places: on the server as the referee, inside the app when playing bots with
no signal, and in unit tests that play thousands of matches out. A rule cannot behave one
way online and another way offline, because there is only one copy of it.

## Changing the rules

Every rule lives in `RuleSet` as data. There are no `if (variant == ALL_FIVES)` branches
in the engine — a variant is just a set of values, and so is a house rule:

```kotlin
val ourTable = RuleSet.CLASSIC_DRAW.copy(
    handSize = 6,                    // deal six instead of seven
    pegStep = 3,                     // score on threes, not fives
    doubleBlankIsWild = true,        // 0|0 goes anywhere
    bonusForGoingOutOnDouble = 25,   // extra for finishing on a double
    blocked = BlockedResolution.LOWEST_PIPS_TAKES_DIFFERENCE,
    winMustBeExact = true,           // must land on the target exactly
)
```

The host edits the same fields on the **House rules** screen in the app, and the values
travel to the server with the room. Adding a rule of your own means: add a field to
`RuleSet`, read it in `Engine`, add a row to `RulesScreen`. Nothing else needs to know.

What is adjustable today:

| Group | Rules |
|---|---|
| Table | pack (double six/nine/twelve), 2–4 players, partners, tiles dealt |
| Drawing | boneyard on or off, must-draw-until-playable, tiles kept back, draw limit |
| Opening | highest double, heaviest bone, a fixed tile, or dealer's left with anything; and who leads later rounds |
| Scoring | count the losers' spots or peg on the ends, the multiple pegged on, doubles counting twice, rounding, counting only the opposing team |
| Blocked | lightest hand takes all, takes the difference, or nobody scores |
| Match | target score, exact finish, fixed number of rounds, seconds per turn |
| House | double blank as a wild, bonus for going out on a double |

Three presets ship as starting points: **Classic Draw**, **Block** and **All Fives**.

## Building

```bash
./gradlew :core:test          # the rules, with full matches played out
./gradlew :server:test        # the room server, over real websockets
./gradlew :app:assembleDebug  # the APK
```

The app needs an Android SDK; point at it with `local.properties`:

```
sdk.dir=/path/to/Android/sdk
```

## Running the server

```bash
./gradlew :server:run          # listens on :8080, or $PORT
```

Then set the server address in the app (`DEFAULT_SERVER` in `GameViewModel.kt`, or the
field on the menu screen) to `wss://your-host/ws`. Any host that terminates TLS and
proxies websockets will do; the server keeps everything in memory and needs no database.

The server is **authoritative**. A phone asks to move; the server runs the engine and
sends each player a `PlayerView` containing their own hand and nothing else. A modified
client cannot see another hand, play a tile it does not hold, or move out of turn.

Rooms hold a seat open for a player who drops, and a bot covers until they reconnect, so
walking through a tunnel does not cost anyone the match.

## Artwork

Every bone is drawn from geometry in `DominoTile.kt` rather than loaded from an image
file: it stays sharp at any size, adds nothing to the download, and the whole set can be
restyled by editing the colours in `Felt`. The launcher icon is a vector for the same
reason.

None of the art, layout or assets are taken from any existing dominoes app. The
conventions are the ones players expect — green felt, ivory bones, your hand along the
bottom, opponents around the table — but the pixels are this project's own.

## Where it stands

Working: the full rules engine, the room server, bots at three strengths, offline play,
the lobby, the house-rules editor, the table with hand, board and scoring.

Not done yet: sound, tile-laying animations, a spinner board (the line folds into rows
rather than branching four ways off the first double), player accounts, and reconnect
handling in the UI beyond what the socket does on its own.
