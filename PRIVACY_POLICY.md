# Vocatim Privacy Policy

*Last updated: 8 July 2026*

Vocatim is built so that your data stays yours. By default, everything runs on your phone and we never see your recordings or transcripts.

## What we collect

**We collect nothing ourselves.** Vocatim has no servers and no user accounts. We never receive your audio, transcripts, or notes.

**Ads are the exception, and they are not ours.** The free version shows ads served by Google AdMob. To do that, the Google Mobile Ads SDK running inside the app collects device and usage information — including your device's advertising ID, approximate location derived from your IP address, and ad interaction data — and sends it to Google. This happens inside the app, on Google's terms, not ours; we do not receive that data and cannot see it.

- In regions where consent is required, you are asked before any personalised ad is served, and declining is respected.
- Buying **Remove ads** stops this entirely: no ads load, and the ads SDK makes no requests.
- Google explains what it collects here: https://policies.google.com/technologies/partner-sites

Your recordings, transcripts, summaries, and notes are never part of this. They stay on your device.

## How your data is handled

### On your device (default)

- **Audio recordings and transcripts** are processed and stored only on your device, in the app's private storage. They never leave your phone unless you explicitly share, export, or back them up yourself.
- **Speech recognition** runs entirely on-device using the Whisper model. No audio or text is sent to any server for transcription.
- **AI Summary (local)** runs on-device when you use the built-in summarizer. No transcript text is sent to us or to any third party.
- **Encrypted backup & restore** (optional) writes an AES-256 encrypted file to a folder you choose (for example Google Drive or local storage). The backup is protected by a password you set. We do not receive backup files or passwords. Audio files are not included in backups — only transcript metadata and text.
- **Android Auto Backup** (the system feature that copies app data to your Google account) is limited to your app settings. Your transcripts, recordings, and API key are explicitly excluded, so they are never uploaded to Google's servers by the app. Moving transcripts to a new device is done with the encrypted backup above, on purpose and under your control.

### Internet access

Internet is used only for:

1. **Downloading speech models** from Hugging Face on first use (model files only — not your recordings).
2. **Ads**, in the free version — see above. Removed permanently by the one-time purchase.
3. **Google Play Billing** when you choose to buy Remove ads.
4. **Optional Cloud AI (BYOK)** — only if you turn this on in Settings (see below).

Purchases are processed by Google Play. We receive no personal information from the transaction.

Every feature of Vocatim — transcription, AI summaries, meeting minutes, exports, backups — is free. The only thing the purchase changes is that ads stop.

### Optional Cloud AI (Bring Your Own Key)

Cloud AI is **off by default** and entirely optional. If you choose to enter your own API key and provider in Settings, Vocatim can send **transcript text you select** (for example for AI summary, Q&A, or translation) **directly from your device** to the third-party provider you configured (such as OpenAI, MiniMax, DeepSeek, or Groq).

- Your API key is stored only in the app's private storage on your device.
- Vocatim **never** sends your API key, audio, or transcript text to us.
- We have **no access** to data sent between your device and your chosen provider.
- You can remove your key at any time in Settings → Cloud AI → Clear.
- Cloud AI settings are excluded from encrypted backups and from Android Auto Backup.

If you do not configure Cloud AI, no transcript or audio data is sent to any third-party AI service.

## Permissions

- **Microphone** — to record audio, only while you use the record feature.
- **Notifications** — to show recording, transcription, and summary progress.
- **Biometric / device credential** (optional) — only if you enable app lock in Settings.

## Data deletion

Deleting a transcript in the app permanently removes it and its audio file. Clearing Cloud AI settings removes your stored API key. Uninstalling the app removes all app data from your device.

## Children

Vocatim itself does not collect data from anyone, including children. Note that the free version serves ads through Google AdMob, which does collect the advertising data described above; the paid version serves no ads at all.

## Contact

Questions: fajar.mreza@gmail.com

## Changes

If this policy changes, the update will be reflected in this document and, when appropriate, noted in the app before it takes effect.
