# MQ Player

One interface to control every sound on your phone — right from Android Auto.

MQ Player is an Android app that bridges the phone's active media sessions to Android Auto, giving
you seamless control over whatever is playing without switching between apps.

## What it does

- Detects active media sessions on the phone using a notification listener
- Chooses the best current session (usually the one actively playing)
- When multiple sessions are active, lets you pick which one to control directly from the Android Auto interface
- Shows simple transport controls in the phone UI
- Exposes a media browser service so Android Auto can show MQ Player in the media app list
- Forwards play, pause, skip, and seek commands to the currently active player

## Important limitation

Android does not let one app become the real owner of another app's media queue/artwork/catalog.
This project works as a **bridge**:

- MQ Player discovers the highest-priority active session on the phone
- Android Auto connects to MQ Player
- MQ Player forwards transport commands to that active session

This is best for playback control, not for full browsing of another app's library.

## Download

GitHub Releases is the **only official source** for MQ Player. Always download the latest APK
from the [Releases page](https://github.com/MohmmadQunibi/mq-player/releases) to make sure you
have a trusted, up-to-date build.

## Run

Install the APK on your phone, then:

1. Open **MQ Player**
2. Tap **Open notification access**
3. Enable notification access for **MQ Player**
   > If Android shows a **"Restricted setting"** warning, go to **Settings → Apps → MQ Player →
   > three-dot menu → Allow restricted settings**, then return and enable notification access.
4. Start a podcast or any media in another app on the phone
5. Open Android Auto and choose **MQ Player** as the media app
6. If multiple apps are playing, tap the one you want to control from the session list

## Privacy

MQ Player does not collect, store, or transmit any personal data or usage information.
Everything stays on your device.

## Note

MQ Player started as a vibe-coded prototype for personal usage. Bugs are expected — if you run
into one, please open an issue on GitHub.
