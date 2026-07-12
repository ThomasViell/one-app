# Louis-Antwort — Entwurf 12.07.2026

Antwort auf Louis' Mail vom 10.07. („AW: DrainQ.ONE 0.5.5-beta — please update and test").
**VERSENDEN ERST NACH GRÜNEM M2-NACHTEST am Gerät (0.5.7).** KI-Hinweis oben einfügen (Regel: „(erstellt mit und/oder von KI)", fett/kursiv/klein/gelb).
ms365-Token war abgelaufen → Entwurf konnte nicht in Outlook angelegt werden; entweder Login erneuern oder Text manuell als Antwort einfügen.

---

Hi Louis,

thank you — all five findings were real bugs, and every one of them is addressed. **Please update first:** Settings → Check for updates → the version must read **0.5.7**.

**1. Date back at 01.01.2021, auto switch doing nothing.** The app no longer silently uses a wrong clock. If the device clock is off, you now get a red warning banner and the date field stays **empty** instead of quietly writing 2021 into the report. Truly automatic time needs the provisioned device image — that comes with the next hardware preparation round.

**2. "Schnellaufnahme" in English.** Fixed. New quick captures are named "Quick capture" in EN, including report and file name. Entries created before the update keep their stored old name — intentional, we never rewrite saved project data.

**3. Camera type empty in the PDF after quick capture.** Fixed in 0.5.7. The cause: the app read the camera head only once, at the moment the quick capture was created — usually before detection had finished. Now the field fills in as soon as the head is recognized, and a manually chosen type is never overwritten.

**4. Fields stuck at diameter / inspector.** Fixed and verified here on the device with three fresh HD projects, typed straight through without the C18→C10 trick. To your question: there are **no** min/max limits on diameter or length that block input — the blocker was purely a keyboard/focus bug.

**5. Route arrow.** Fixed — the report font now contains the "→".

Your other points:
— **Second playback faster:** the recording path is rebuilt (real time, ~27 pictures per second). Please check on 0.5.7 whether the second playback still speeds up. Your DK/SE real-time note is important and will be respected for the TWO as well.
— **Hard buttons** (light levels, probe value, recording choice, damage button opening the gallery, gallery button inactive): agreed, this becomes its own work package — the damage button will go to damage creation.
— **Storage display** (internal + USB): on the list, not built yet; until then it goes clearly into the manual.
— **USB export:** as agreed — your office findings next week, then we fix it in one go.

**The most important test is still the meter counter.** Record a video with the counter running, then take a photo and a damage from the finished video at three different positions. The value offered in the dialog must be **exactly the number burned into the picture** at that moment. Empty = tell us. A wrong number = tell us immediately — that would be worse than empty.

Thanks again — the reports are exactly what we need.

Best regards,
Thomas
