# AI audio-to-score transcription

## Product behavior

The user selects an audio file or records a performance, confirms they own the
recording or have permission to process it, chooses an output type, and starts a
transcription job. The service returns editable MusicXML, MIDI, a rendered PDF,
tempo/key estimates, confidence warnings, and separated parts where supported.

## Architecture

1. Android copies the selected audio into a private, resumable job.
2. A backend issues a short-lived upload URL; API credentials are never stored
   in the APK.
3. The backend validates duration, size, codec and consent metadata.
4. A polyphonic transcription model creates timed notes and voice assignments.
5. Post-processing quantizes rhythm and exports MusicXML/MIDI.
6. An engraving worker renders PDF and preview images.
7. Android imports the result as a score and lets the user correct uncertain
   measures.
8. Uploaded audio and intermediate stems are deleted according to the selected
   retention setting.

## Required safeguards

- No catalog scraping, stream ripping or bypassing copy protection
- Process only recordings the user is authorized to upload
- Clear upload, retention, deletion and model-provider disclosures
- Explicit consent before network transfer
- Job cancellation and server-side deletion
- Never claim that machine transcription is note-perfect; surface confidence
  and require musical review

## Acceptance criteria

- Survives rotation, process death, network loss and retry without duplicate jobs
- Supports WAV, MP3, M4A and FLAC within published limits
- Produces valid MusicXML and MIDI that reopen after export
- Imports the rendered PDF into the library atomically
- Never embeds provider secrets in the Android package
- Deletes cloud artifacts when requested and records deletion status
