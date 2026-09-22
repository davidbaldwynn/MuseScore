# Scoreleaf import and services plan

This workstream targets the workflows documented in forScore's official
[Adding Files](https://forscore.co/documentation/adding-files/) and
[Services](https://forscore.co/documentation/services/) guides while using an
original Android interface and supported provider flows.

## Checkpoint 1 — authenticated site browsers

- A cancellable Scoreleaf import-source dialog before entering Android system UI
- Dedicated authenticated browsers with persistent site cookies
- Music-Scores, MuseScore, IMSLP, Musicnotes, Virtual Sheet Music, 8notes and Free-scores
- HTTPS-only top-level navigation, blocked local-file/content/javascript URLs
- Intercept authorized PDF downloads and import them into app-private storage
- Validate PDF signatures, cap downloads at 250 MB and sanitize filenames
- Never forward site cookies to a different redirect host
- Unit tests for providers, navigation, download detection and filenames
- Compose flow tests for cancellation, provider selection and browser controls

## Checkpoint 2 — resilient transfers

- Download progress, cancellation and retry
- Duplicate detection by content hash with Keep both, Replace and Cancel choices
- Multiple simultaneous downloads and a persistent transfer queue
- ZIP and multi-file imports with safe archive extraction
- Recover interrupted transfers without creating partial library entries
- Corrupt, HTML-disguised, oversized, redirect-loop and out-of-storage tests

## Checkpoint 3 — provider services

- Provider adapter interface with capability discovery
- Purchase/library views when an official provider API or partnership permits them
- Title, composer, key and purchase-date sorting
- Batch and setlist-targeted downloads
- Transposition choices when exposed by the provider
- Optional automatic download polling with explicit user consent
- Logout, cookie clearing and provider-specific error recovery

No adapter may scrape protected endpoints, bypass DRM, defeat subscription rules,
or download content the signed-in user is not authorized to access.

## Checkpoint 4 — cloud services

- Android Storage Access Framework for installed Drive, Dropbox, OneDrive and Box providers
- Native WebDAV, SFTP and compatible self-hosted storage adapters
- Browse, upload, download, move and delete supported files
- Offline local copies and explicit cloud-update handling
- Conflict-safe overwrite and recovery tests

## Checkpoint 5 — expanded formats

- Images and camera scans combined into PDFs
- TXT, RTF, DOC and DOCX conversion with documented fidelity limitations
- MusicXML/MXL and MIDI import through the notation workstream
- CSV/TSV bookmark-index imports
- Audio attachments and Scoreleaf archive formats

## Exit criteria

Each checkpoint is released only after its unit, repository, Compose UI, lint,
build and large-tablet emulator gates pass. Provider flows also require a manual
licensed-account test because CI must not contain marketplace credentials.
