# Retro Explorer (Android)

A native Android file manager that looks like Windows Explorer, with four switchable themes:
**Windows XP** (Luna blue, task pane), **Windows Vista** (dark Aero glass), **Windows 7** (light Aero)
and **Snaptu** (dark, orange accents, colourful tiles, Options / Back soft keys).

Written in plain Java with no third-party libraries. It runs on Android 5.0 (API 21) and newer.

## Features
- Browse internal storage and SD cards, with breadcrumb address bar, Back / Forward / Up and history
- Views: Large icons (with photo & video thumbnails), Tiles, List, Details (sortable columns)
- Sort by name, date, type, size (ascending / descending); show/hide hidden files
- New folder, new text document, rename, cut / copy / paste, copy-to / move-to folder picker
- Recycle Bin with restore, permanent delete and Empty Recycle Bin
- Multi-select (long-press, then tap), select all, invert selection
- Search (recursive, from the current folder)
- Properties (type, location, size, contents count, attributes, disk free/used space)
- Built-in Notepad (edit & save text/code files) and Photo Viewer (swipe, rotate)
- Open with / Share any file through other apps (also installs APKs)
- Quick access pins, folder tree "Places" panel (XP shows the classic task pane instead)
- Title-bar buttons: minimise (send to background), maximise (full screen), close

## Build the APK — option A: GitHub (works from a phone)
1. Create a new empty repository on github.com.
2. Upload **the contents** of this folder (not the zip itself) — keep the `.github` folder.
   On a phone, the GitHub web upload can't do folders, so use a desktop browser, GitHub Desktop,
   or `git push` from a computer / Termux.
3. Open the repo's **Actions** tab → **Build APK** → wait about 3–5 minutes.
4. Open the finished run and download **RetroExplorer-apk** from *Artifacts*. Unzip it to get
   `app-release.apk`.

## Build the APK — option B: Android Studio
Open this folder in Android Studio (Hedgehog or newer), let Gradle sync, then
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
Or from a terminal: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Install
Copy the APK to your phone, tap it and allow "Install unknown apps" for your browser/file app.
On first launch, tap **Grant access** and turn on **Allow access to manage all files**.

The release APK is signed with the standard debug key so it installs directly. Use your own
signing key before publishing it anywhere.

---
Fan-made tribute. Not affiliated with Microsoft or Snaptu.
