# پر — Par (Android client for SHABAKEH PAR) v1.1

Package `com.par.chat`, app name **پر**. A **join-only** client (no hosting), like Telegram / Google Meet: paste an invitation link, join, chat, share files, send voice notes, and call.
It talks to the SHABAKEH PAR host (Windows app v0.3) with the same protocol as the desktop client, so Android and Windows can chat and call each other.

> **Status (honest): this source has NOT been compiled or run.** It was written without an Android SDK or internet access. Expect a few compile errors on the first build (library versions, small API differences). Send the build output and they will be fixed. The WebRTC library (`io.getstream:stream-webrtc-android`) and every Gradle version are unverified guesses.

## Build the APK (Android Studio)
1. Install **Android Studio** (latest stable). Open this folder (`File > Open`). Let Gradle sync (needs internet the first time).
   If it complains about the Gradle wrapper, use `Settings > Build Tools > Gradle` and choose Gradle 8.9 (it can download it), or run `gradle wrapper` once.
2. `Build > Build Bundle(s) / APK(s) > Build APK(s)` → `app/build/outputs/apk/debug/app-debug.apk`.
   For a release APK: `Build > Select Build Variant > release`, then build again → `app/build/outputs/apk/release/app-release.apk`
   (signed with the debug key so it installs directly; make your own keystore before publishing to a store).
3. Copy the APK to the phone and open it (allow "Install unknown apps" for your file manager/browser).
## Build the APK on GitHub (no Android Studio)
1. Create a GitHub repository and push **the contents of this folder** (the `.github` folder must be at the repository root):
   `git init && git add . && git commit -m "Par" && git branch -M main && git remote add origin <repo-url> && git push -u origin main`
2. Open the repository's **Actions** tab. The *Build APK* workflow runs on every push; you can also start it with **Run workflow**.
3. When it turns green (about 5-10 minutes), open the run and download **par-apk** from *Artifacts* (a zip containing `par.apk`).
4. For a download link without logging in: `git tag v1.0.0 && git push origin v1.0.0` publishes `par.apk` in the repository's **Releases**.
If the build fails, open the failed step in the log (or download the `build-reports` artifact) and send me the error text.

## Use it
1. The host (Windows app) copies its **invitation link** (`SPINV1:…`) and sends it to you (Telegram, SMS…).
2. Open **پر**, tap **Paste** (or share the link to the app), type your name, **Test connection**, **Join**.
   The app tests every address in the link at the same time (LAN, IPv6, public IPv4, relay) and uses the first that works.
3. Chats: private and group messages, replies, edit/delete (long-press), emoji, typing indicator, online status, sent/delivered/read ticks, search, files, images, voice notes. Calls: voice and video (People tab, or the buttons in a chat).

## What is NOT included
- **Push through Google (FCM).** Instead, a **foreground service** keeps the connection alive in the background and shows message and incoming-call notifications. Phone makers (Xiaomi, Samsung, Huawei…) kill background apps aggressively: open *Settings > Apps > پر > Battery* and choose **Unrestricted / no optimisation**, and allow notifications, or messages may arrive late.
- **Encryption.** The host protocol is plain HTTP/WebSocket for now (Android is told to allow cleartext in `network_security_config.xml`). Use with people you trust.
- **Hosting**, group calls, and host administration (use the Windows app).
- Calls are peer to peer (STUN). Behind strict NAT on both sides add a TURN server in Settings. IPv6 on both sides usually just works.

## Layout
- `data/` invitation decoding, HTTP/WebSocket engine (reconnect, sync by sequence number), uploads/downloads
- `call/CallManager.kt` WebRTC with the same signalling messages as the desktop app
- `ui/` Jetpack Compose screens (Join, Home, Chat, Call), Persian (RTL) and English strings

## v1.1
- **Telegram-style interface:** chat list sorted by latest activity with time and unread badges, filter pill (All / People / Groups), floating bottom navigation, round new-chat button; chat screen with wallpaper, floating header, date chips, bubbles with tails, ticks, rounded input and a round mic/send button.
- **Notifications and background connection** (foreground service), incoming calls can open over the lock screen.
- **Calls:** the host shares its TURN server automatically; Settings can override it.
- **Small APK:** arm64 only, R8 shrinking, only English and Persian resources (about 15-20 MB instead of 60 MB+). If a release build crashes at start (R8), set `isMinifyEnabled = false` in `app/build.gradle.kts` and tell me.
