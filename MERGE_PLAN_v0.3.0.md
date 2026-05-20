# Merge-Plan v0.3.0 — OSD Live-Burn-In (Phasen 1-7)

## Ausgangslage
Sieben Feature-Branches im linearen Stack:
```
master
  -> feature/osd-phase-1
       -> feature/osd-phase-2
            -> feature/osd-phase-3
                 -> feature/osd-phase-4
                      -> feature/osd-phase-5
                           -> feature/osd-phase-6
                                -> feature/osd-phase-7   <- aktueller Spitzen-Branch
```
Phase 7 enthaelt alle Vorgaenger-Commits. **Empfehlung: nur Phase 7 mergen.** Damit landet die gesamte OSD-Pipeline in einem Schritt auf master, ohne dass tote Zwischen-Artefakte (z.B. der in Phase 7 wieder geloeschte VlcVideoPlayer) den master-Verlauf belasten.

## Voraussetzungen vor dem Merge

```powershell
cd C:\Projekte\drainq.one

# 1. Working Tree muss clean sein
git status
# erwartet: "nothing to commit, working tree clean"

# 2. Stand der Branches sichten
git log --oneline master..feature/osd-phase-7

# 3. Build und Tests auf dem Phase-7-Branch nochmal lokal gruen?
git checkout feature/osd-phase-7
.\gradlew clean test assembleRelease
# erwartet: BUILD SUCCESSFUL, 57/57 Tests passed, APK 144 MB
```

## Merge-Sequenz (Fast-Path)

```powershell
# 1. Backup-Tag auf aktuellen master (Rollback-Anker)
git checkout master
git tag backup/pre-osd-merge

# 2. Phase 7 als Merge-Commit nach master (kein Fast-Forward, History sichtbar)
git merge --no-ff feature/osd-phase-7 -m "feat: OSD Live-Burn-In Pipeline v0.3.0 (Phasen 1-7)

- FFmpeg drawtext OSD Burn-In fuer Recording (MP4)
- Compose-Canvas OSD-Overlay fuer Live-Display
- OsdRenderer pixelgenau fuer Screenshot-JPEG
- libVLC entfernt, ExoPlayer/Media3 als Live-Player
- APK Release: 144 MB (-86 MB vs v0.2.0)
- 57/57 Unit-Tests gruen"

# 3. Release-Tag setzen
git tag -a v0.3.0 -m "DrainQ ONE v0.3.0 - OSD Live-Burn-In"

# 4. Master und Tag pushen
git push origin master
git push origin v0.3.0
git push origin backup/pre-osd-merge
```

## Release-APK signieren und ablegen

```powershell
# Release-APK ist nach assembleRelease unter:
#   app\build\outputs\apk\release\app-release.apk
# Signatur-Keystore: oneapp-release.keystore (im Repo-Root)

# APK in den Repo-Root kopieren mit klarem Namen
Copy-Item app\build\outputs\apk\release\app-release.apk -Destination "DrainQ_ONE_v0.3.0.apk"

git add DrainQ_ONE_v0.3.0.apk
git commit -m "chore: release APK v0.3.0"
git push origin master
```

## Cleanup (nach erfolgreichem Merge + Hardware-Test)

```powershell
# Feature-Branches loeschen — Phase 7 enthaelt alle History
foreach ($n in 1..7) {
    git branch -d "feature/osd-phase-$n"
    git push origin --delete "feature/osd-phase-$n"
}
```

**Achtung:** Loesch-Schritt erst nach erfolgreichem Hardware-Test (siehe `HARDWARE_TEST_PLAN_v0.3.0.md`). Bis dahin Branches als Sicherheitsnetz behalten.

## Rollback-Plan

Falls master nach Merge nicht mehr baut oder die Hardware-Tests kritisch failen:

```powershell
git checkout master
git reset --hard backup/pre-osd-merge
git push --force-with-lease origin master
git tag -d v0.3.0
git push origin --delete v0.3.0
```

## Akzeptanzkriterien nach Merge

- [ ] `git log master` zeigt den Merge-Commit mit klarer Message
- [ ] Tag `v0.3.0` ist gesetzt und auf Remote
- [ ] `gradlew assembleRelease` baut auf master ohne Aenderungen
- [ ] `DrainQ_ONE_v0.3.0.apk` liegt im Repo-Root
- [ ] Hardware-Tests aus `HARDWARE_TEST_PLAN_v0.3.0.md` sind durchgelaufen
