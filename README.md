# Scoreleaf

Scoreleaf is an original, offline-first Android implementation targeting forScore-class UI and feature parity. It does not use forScore code, assets, or branding.

## Current alpha features

- Import PDFs from Files, Drive, email, or any Android document provider
- Full-screen, tablet-friendly PDF reader
- Tap page edges for fast page turning; tap the center to hide controls
- Freehand annotations stored in normalized coordinates
- Undo annotations per page
- Bookmarks and automatic return to the last-read page
- Searchable local library
- Create setlists and add scores to them
- Open PDFs shared into the app
- Offline-only storage; no account and no analytics

## Build and test

Every push runs unit tests, Android lint, and assembles a sideloadable debug APK. The workflow uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, and Android SDK 35.

For a local build, open the repository in Android Studio Ladybug or newer and run the `app` configuration on Android 8.0 or newer.

The full parity scope and release gates are tracked in [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Architecture

The app uses Kotlin and Jetpack Compose. Android `PdfRenderer` renders local PDFs without sending music off-device. Metadata and annotation JSON are kept in app-private storage; imported PDFs are copied there so document-provider permissions cannot expire.
