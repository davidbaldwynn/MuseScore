# forScore-class Android parity plan

## Verified status

**Audit date:** 2026-09-20  
**Reference:** forScore 15.2.1 and its official
[user-guide table of contents](https://forscore.co/documentation/table-of-contents/).

Scoreleaf is **not currently at feature or UI parity**. A row is only marked
`Complete` after its behavior has automated coverage, a large-tablet UI check,
and a physical-device acceptance run. Labels such as “UI shell” do not count as
implemented functionality.

Scoreleaf targets workflow and capability parity with current forScore while retaining its own name, visual assets, source, and platform-appropriate Android behavior.

## Product surface

| Area | Required behavior | v0.1 status |
|---|---|---|
| Reading | Disappearing controls, edge taps, page scrubber, adaptive prefetch | Partial |
| Library | All scores, recents, composers, genres, tags, labels, list/grid, metadata sorting | Partial: metadata editing/search, category sorts, list/grid |
| Setlists | Folders, drag ordering, placeholders, navigation across score boundaries | Partial: membership, ordering, removal, score launch |
| Annotation | Pencil/highlighter/eraser, stamps, shapes, text, lasso, layers, undo/redo | Partial: pen, highlighter, vector eraser, undo/redo, persisted layer IDs |
| Bookmarks | Page flags, named bookmarks, ranges, indexed books, CSV index import | Page flags |
| Layout | Crop, best fit, two-up, half-page turns, vertical scroll, Reflow | Partial: fit modes, two-up, half-page, vertical scroll |
| Score editing | Rearrange, duplicate, rotate, insert, extract, merge | Planned |
| Navigation | Links for repeats; configurable on-page action buttons | Planned |
| Performance | Bluetooth pedals, keyboard shortcuts, MIDI in/out, remote page turns | Planned |
| Audio | Attach tracks, looping, pitch/tempo changes, recorded page turns | Planned |
| Utilities | Metronome, pitch pipe, tuner, piano, timer, practice log | UI shell |
| Import/export | Files/cloud providers, provider marketplaces, scan, annotated PDF export, archives | Partial: PDF file import/export; authenticated provider browsers in progress |
| Sync/backup | Local archive, WebDAV/Drive sync, conflicts, restore | Planned |
| Android platform | Stylus pressure/hover, multi-window, intents, widgets/shortcuts | Intents only |

### Additional audited gaps

- Zoomed display, page crop and Reflow
- Named and ranged bookmarks, indexes, and CSV index import
- Metadata editing, batch editing, filters, custom categories and sorting
- Audio attachment, looping, recording, pitch/tempo controls and page-turn cues
- PDF text/OCR search, scan capture, Reflow and accessibility reading
- Annotation presets, pressure, highlighter, eraser, stamps, shapes, text,
  selection/lasso, layers, redo and standard-format export
- Page crop, rotate, duplicate, insert, extract, rearrange and merge
- Configurable links/buttons, Bluetooth pedals, keyboard commands and MIDI
- Backup/restore, cloud-provider sync, conflict handling and recovery
- Multi-window, widgets/shortcuts, accessibility and large-library performance

The visual target is workflow and information-architecture parity using an
original Android design. It must not copy forScore's protected artwork,
branding, or source code.

## Release sequence

1. **Performance-reader alpha:** resilient PDF engine, adjacent-page bitmap cache, list/grid library, complete metadata, setlist playback, Bluetooth pedals, crop/two-up/half-page modes.
2. **Annotation beta:** vector layer model, pressure-aware ink, highlighter, eraser, selection, text, shapes, music stamps, named layers, flatten/export.
3. **Score-workflow beta:** bookmarks and indexes, page rearrangement, links/buttons, audio attachments, metronome/pitch/tuner/piano, backup archive.
4. **Parity release:** OCR/Reflow, Drive/WebDAV sync, MIDI, remote coordination, multi-window, automation shortcuts, accessibility and large-library performance testing.

The checkpointed provider, transfer, cloud, and format work is specified in
[IMPORT_SOURCES.md](IMPORT_SOURCES.md). A provider browser does not count as a
complete native service integration unless purchase discovery, transfer states,
duplicate handling, logout, and provider-specific acceptance tests also pass.

## Non-negotiable quality bars

- A cached page turn must render within one display frame on reference tablets.
- No network dependency during a performance.
- Every mutation must be crash-safe and recoverable from an automatic local snapshot.
- Stylus input must never trigger a page turn.
- Setlist playback must continue across document boundaries without exposing library UI.
- Imported PDFs and user annotations remain exportable in standard formats.

## Evidence required for a parity claim

1. Every row above has executable acceptance criteria and is `Complete`.
2. Unit, repository, Compose UI, accessibility, rotation, process-recreation,
   and Android emulator suites pass on every pull request.
3. Golden screenshots pass at phone, 10-inch tablet, and TCL NXTPAPER 14-class
   dimensions in portrait and landscape.
4. A release candidate passes the physical TCL checklist in
   [TEST_PLAN.md](TEST_PLAN.md), including stylus, Bluetooth pedal, long
   performance, low-memory recovery, and offline use.
5. No open blocker/critical defects and no known data-loss defects.

Testing can establish confidence and document known behavior; it cannot prove
that any non-trivial application contains zero bugs.
