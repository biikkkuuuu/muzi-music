# Muzi Brain (AI Guidelines & Memory)

This file (`brain.md`) acts as the permanent memory and strict rulebook for all AI agents working on this repository. 
**ALWAYS READ THIS FILE before making any changes.**

## 1. Project Identity & Branding
- **Name:** The app is **Muzi** (formerly EchoMusic).
- **Rule:** NEVER use the word "Echo" or "EchoMusic" in any new packages, strings, or UI elements. The app is entirely Muzi now.
- **InnerTube:** We use **InnerTube** for media decryption and backend integration, NOT BravePipe. 

## 2. DANGEROUS ACTION WARNING (Regex/Replacements)
- **NEVER** run blind global Find & Replace for the word `echo`. 
- In the past, doing this blindly destroyed Kotlin code because it replaced `echo` inside camelCase words:
  - `createChooser` became `creatoser`
  - `SingleChoice` became `Singlice`
- If you ever need to replace text, ALWAYS use strict word boundaries (`\becho\b`) or inspect changes before committing.

## 3. Build & Memory Management (CRITICAL)
- **JVM OOM Crashes:** The project is massive. If you allocate too much RAM in `gradle.properties` (e.g., `-Xmx6g` or higher), the OS will kill the Gradle Daemon during the Dexing phase, causing a mysterious `disappeared unexpectedly` crash.
- **Rule:** Keep `org.gradle.jvmargs` and `kotlin.daemon.jvmargs` at `-Xmx3g` maximum to ensure stability on average machines.

## 4. UI / UX Design
- **Muzi Aesthetic:** DO NOT strictly use default Material 3 (M3) components. We use a **Modern Expressive UI**.
- Characteristics include: Frosted glass (glassmorphism), translucency (`surfaceVariant.copy(alpha = 0.3f)`), rounded corners, and dynamic fairy-light glow effects.
- If making a new UI component, study existing ones (like the Library or Settings) instead of importing default M3 cards.

## 5. Releasing and Git
- Always compile the **Universal GMS** variant (`assembleUniversalGmsRelease` / `installUniversalGmsDebug`).
- GitHub Actions handles release tagging automatically via `release.yml`.
- We bypass linting (`-x lintVitalAnalyzeUniversalGmsRelease`) in GitHub Actions if Google servers throw `502 Bad Gateway`.
- Always push cleanly without exposing old branding in commit messages (e.g., use "feat: migrate to MUZI with Modern Expressive UI").

---
*Agent Note: You are bound by these rules. Any deviation is considered a severe bug.*

## 6. Active Tasks / Backlog
- **Lyrics Animation:** Implement a "buttery smooth" word-by-word active glow animation (similar to Apple Music style as shown in the reference video `WhatsApp Video 2026-09-19 at 10.57.36 AM.mp4`). The words should fill with color smoothly as they are sung, rather than snapping.
  - *Rule:* **DO NOT PUSH** any changes related to this until the user explicitly says "push".


## 7. Recent Updates (Sept 27, 2026)
- **Branding & OOM:** Successfully migrated Echo to Muzi. Fixed R8 OOM crashes by setting gradle.properties heap to 3072m.
- **Data Restore Crash:** Fixed crash on reinstall by disabling ndroid:allowBackup and ndroid:hasFragileUserData.
- **Ghost Notification:** Fixed 'Music Player is running' empty notification on app launch by removing manual startForeground from MusicService.onCreate(). Media3 handles this automatically.
- **UI Cleanups:** Removed 'Create Playlist with AI' and 'Import from Spotify Account'. Streamlined dropdown menu options.
- **YouTube Mix Import:** Fixed importer bug for YouTube Mixes (RD... playlists). The app now uses YouTube.queue API instead of YouTube.playlist for RD links.
- **Update Mechanism Trap:** If a repository has a historically higher version tag (e.g. `v1.4.0`), releasing a lower version (like `v1.2.7`) will cause the app's update checker to trap users in an infinite update loop. Always ensure the `versionName` is strictly greater than the highest existing GitHub Release tag (fixed by bumping to `v1.4.1`).
