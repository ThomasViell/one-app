# Louis-Antwort — FINAL 12.07.2026 (Stand 0.5.13)

Antwort auf Louis' Feedback-Mail vom 10.07. KI-Hinweis oben lassen oder für die persönliche Mail entfernen (deine Entscheidung).

---

*(erstellt mit und/oder von KI)*

Hi Louis,

first of all — a big thank you. Your test on 0.5.5 was exactly the kind of thorough, hands-on feedback we need, and every single point you raised is now fixed. I built and tested all of it myself on our ONE; details below.

**Please update first:** Settings → check for updates → the version must read **0.5.13**.

**What we fixed — all your findings:**
1. **Date jumping back to 01.01.2021 / the "auto" switch doing nothing** — the app no longer silently writes a wrong date. If the clock is off you now get a red warning and the date field stays empty, plus a shortcut straight to the date settings. (Truly automatic time needs the provisioned device image — that comes with the next hardware-prep round.)
2. **"Schnellaufnahme" shown in English** — now "Quick capture" (report + file name). And while we were at it: the language now switches **live, no restart needed**.
3. **Camera type missing in the PDF** — fixed; it now fills automatically from the detected head. We also removed the manual camera-type and inspection-system selection entirely: the ONE always auto-detects the head, so picking it by hand made no sense.
4. **Fields stuck at diameter / inspector** — fixed; you can type straight through now, no C18→C10 trick.
5. **Route arrow** — the "→" is back in the report, and I verified the font is properly embedded in the PDF.
6. **Second playback running too fast** — the recording path is rebuilt; every playback now runs in real time (important for your DK/SE real-time requirement).
7. **The meter value on a photo/damage taken from the video — your most important point.** We fixed the root cause (the value was being interpolated) so the offered number is now exactly the value burned into the frame. I tested it at three positions on our ONE — exact match every time.

**On top of your list:**
- **Hard buttons:** Light and Sonde work properly now. Light button opens the slider, each further press +10 % (after 100 it goes back to 0). Sonde button opens the frequency list, each further press steps through the frequencies (Off → 33 kHz → 640 Hz → 512 Hz → Off). Both close after 3 seconds without a press.
- **Storage display:** new — internal and USB storage are shown as fill-level bars on the home screen (with the stick's name and free/total), and it updates automatically when you plug or unplug a stick.

**Tomorrow, when you're here — please bring your ONE.** I'd like to go through everything together on your rig, especially the two things I can only fully confirm with your equipment:
- The **meter counter with your cable drum / Haspel** (I tested it on ours, but I want to confirm it on yours).
- The **camera-type auto-fill when swapping heads (C18 → C10)** — I only have one head here.

And please bring your **USB-export findings from the office** (the cryptic file names and the photos/videos that wouldn't open) — we'll sit down and fix that in one go.

In my view nothing else is open. The only items still to build/test are exactly the ones we'll do together tomorrow: the meter counter on your rig, the head-swap camera scenario, and the USB export once I have your office findings.

Thanks again — really. See you tomorrow.

Best regards,
Thomas
