# Scoreleaf

Scoreleaf is an original, offline-first Android implementation targeting forScore-class UI and feature parity. It does not use forScore code, assets, or branding.

## Current alpha features

- Import PDFs from Files, Drive, email, or any Android document provider
- Full-screen, tablet-friendly PDF reader
- Tap page edges for fast page turning; tap the center to hide controls
- Single-page, two-page, half-page-turn, and vertical-scrolling layouts
- Fit-page, fit-width, and fit-height settings saved per score
- Bounded page cache with adjacent-page prefetch
- Freehand annotations stored in normalized coordinates
- Undo annotations per page
- Bookmarks and automatic return to the last-read page
- Searchable local library
- Edit title, composer, genre, tags, key, tempo, duration, rating, and difficulty
- Create ordered setlists, remove/reorder entries, and open scores directly from them
- Open PDFs shared into the app
- Offline-only storage; no account and no analytics

## Run

1. Open this directory in Android Studio Ladybug or newer.
2. Let Android Studio install Android SDK 35 and sync Gradle.
3. Run the `app` configuration on an Android 8.0+ device or emulator.

## Produce a release

Create a signing key in Android Studio under **Build → Generate Signed Bundle / APK**, select **Android App Bundle**, and build the `release` variant. Upload the resulting `.aab` to a closed Google Play testing track before production.

Before public release, change `applicationId` to a domain you control, add a privacy-policy URL, app icon/screenshots, crash reporting consent if desired, automated device tests, and Play Store data-safety answers. Because v0.1 stores data locally, its baseline data-safety declaration is “no data collected or shared.” Validate that statement again if telemetry or sync is added.

## Next parity milestones

- Bluetooth pedal and MIDI actions
- Crop/Reflow margins and zoom controls
- Annotation colors, highlighter, eraser, shapes, and text
- Export annotated PDFs
- Backup/restore archive and optional WebDAV/Drive sync
- Metadata editing, tags, composers, and setlist reordering

The full parity scope and release gates are tracked in [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Architecture

The app uses Kotlin and Jetpack Compose. Android `PdfRenderer` renders local PDFs without sending music off-device. Metadata and annotation JSON are kept in app-private storage; imported PDFs are copied there so document-provider permissions cannot expire.
