# Chess Match

Chess Match is a Kotlin and Jetpack Compose chess game with a full board, legal-move hints, and an on-device opponent that plays a legal move and takes a piece when it can.
Moves animate across the board, and the game calls check, checkmate, and stalemate from the same rules the unit tests cover.
Each new game is a match on a small Node API; Retrofit posts every move, and the server checks it against the SQLite position before it is stored.
The app is MVVM with Hilt and coroutines, written as portfolio work by Geetha M (github.com/geethagee02) on the Android stack she uses for production apps.

You play white. Device plays black. A capture is preferred over a quiet move. The phone highlights legal squares, slides the piece to its destination, and rolls a move back if the server refuses it.

## API

Node 22 or newer. No packages to install. The database file is created at `server/data/chess.sqlite`.

```bash
cd server
npm test
npm start
```

The API listens on `http://127.0.0.1:8791`.

```bash
curl -s http://127.0.0.1:8791/health
curl -s -X POST http://127.0.0.1:8791/matches \
  -H 'content-type: application/json' \
  -d '{"white":"Geetha","black":"Device"}'
curl -s http://127.0.0.1:8791/matches
```

`POST /matches` starts a game. `POST /matches/:id/moves` with `{"from":"e2","to":"e4"}` appends a move, or returns 422 when the move is illegal. A move after checkmate or stalemate returns 409. `GET /matches` lists recent games.

## App

Android Studio, JDK 17, and Android SDK 35. The emulator reaches the computer at `http://10.0.2.2:8791/`, which is `API_BASE` in the app. Cleartext HTTP is only for that local API.

On a device, forward the port and point the app at localhost:

```bash
adb reverse tcp:8791 tcp:8791
```

Then add `API_BASE=http://127.0.0.1:8791/` to `gradle.properties` and sync the project.

```bash
sh ./gradlew :rules:test testDebugUnitTest assembleDebug
```

`:rules:test` covers pawn moves, knight moves, check, checkmate, and stalemate. It does not need a device. `testDebugUnitTest` is the app module and needs the Android SDK. Open the project in Android Studio and run `app` on an emulator with the API already started.
