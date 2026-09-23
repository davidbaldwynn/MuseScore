# Scoreleaf quality and device test plan

## Automated gates

Every pull request must pass:

- Kotlin unit tests for navigation, filtering, metadata, setlists, bookmarks,
  annotation serialization, transcription job state, and error handling
- Android lint and release/debug compilation
- Compose UI tests for empty, populated, loading, error and large-font states
- Instrumented tests at the minimum supported API and current target API
- 2400 × 1600 large-tablet portrait and landscape UI runs
- Accessibility checks for labels, touch targets, contrast and font scaling
- Rotation, activity recreation and process-death restoration tests

The advanced-annotation checkpoint additionally executes a single large-tablet
whole-flow test covering PDF open, annotation mode, rectangle creation, text,
music stamp, layer creation/selection/locking/visibility/rename, lasso selection
and movement. Repository tests reopen typed annotations and named layers, and
export tests render the resulting PDF to verify visible typed elements are
flattened while hidden layers are excluded.

## Performance and resilience

- Import and page through representative 10, 100 and 1,000-page PDFs
- Exercise 100, 1,000 and 10,000-item libraries
- Verify adjacent-page prefetch and bound bitmap memory
- Verify half-page turns visit top and bottom exactly once in both directions
- Verify fit-page, fit-width and fit-height settings survive reader re-entry
- Scroll every page of representative portrait and landscape documents
- Run a continuous two-hour page-turn and annotation soak
- Interrupt imports, exports and transcription jobs at every state
- Fill storage, revoke document access, provide corrupt PDFs and lose network
- Confirm backups restore files, metadata, setlists, bookmarks and annotations

## TCL NXTPAPER 14 physical acceptance

Record the OS/build number before testing.

- Install, upgrade and uninstall/reinstall the signed APK
- Verify portrait, landscape, split-screen and display/font scaling
- Verify NXTPAPER display modes do not obscure controls or reduce contrast
- Import PDFs from Files, Drive, email and USB storage
- Test finger, active stylus, palm rejection and long annotation sessions
- Pair and map a Bluetooth page-turn pedal
- Test speakers, headphones and Bluetooth audio for music utilities
- Enable airplane mode and complete a setlist without network access
- Background/foreground the app, lock the screen and rotate during reading
- Test low battery, low memory and low storage behavior
- Complete a live-performance rehearsal and record every unexpected behavior

## Release rule

A build may be called an alpha when build, lint and unit tests pass. It may be
called a beta only after emulator UI tests pass. It may be described as
TCL-compatible only after the physical checklist passes. “Full parity” is
reserved for completion of every audited feature and evidence gate.
