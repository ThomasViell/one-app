# CHG-05 eintragen + feature/dual-mode sichern (drainq.one)

Rolle: Software-Ingenieur/Produktowner von drainq.one (Repo C:\Projekte\drainq.one).
Arbeite nach dem Repo-Regelwerk (lies CLAUDE.md + docs/engineering/06-maintenance_one.md
zuerst). Genau zwei Aufgaben, KEIN Merge nach master.

KONTEXT:
Auf Branch feature/dual-mode liegen bereits 3 Commits vom heutigen Video-Node-Fix
(ADR docs/adr/0003-device-node-permissions.md, docs/PROVISIONING_GOLDEN_IMAGE.md,
RESULT_VIDEO_PERMISSION_PERSIST.md). Der CEO hat die zwei folgenden Schritte freigegeben.

AUFGABE A — CHG-05 in docs/engineering/06-maintenance_one.md eintragen:
Ersetze die leere CHG-05-Zeile in §3 durch (CEO-bestätigte Einordnung):
- ID: CHG-05
- Datum: 2026-07-16
- Typ: Fehler (Zielumgebung)
- Beschreibung: Fabrikneu geflashte bzw. neu gestartete ONE zeigt kein Kamerabild —
  /dev/video0-Node-Rechte (crw-rw---- media camera) nicht persistent, App (untrusted_app)
  kann Node nicht oeffnen. Behoben durch ueventd-Regel /dev/video* 0666 root root im
  Vendor-Image (Overlay auf diesem Geraet reboot-verifiziert; nativ ins Golden-Image der
  vendor/super-Partition + OEM-Basisfirmware ausstehend). Kein App-Code geaendert.
  Siehe ADR-0003 + PROVISIONING_GOLDEN_IMAGE.md.
- Ausloeser/Quelle: eigener Geraetetest (fabrikneue ONE, Serial cc1615f07da5e76f)
- Entscheidung: Angenommen; Einzelgeraet device-verifiziert; Flotten-Fix via Vendor
  (Anfrage vom CEO bereits veranlasst)
- Zielversion: keine App-Version (Aenderung betrifft Golden-Image/Vendor, nicht die APK)

Impact-Matrix (§4): Zeile "Paketierung, Bereitstellungsweg, Zielumgebung -> 05-deployment",
Re-Audit 05. WICHTIG: 05-deployment_one.md existiert noch nicht. Lege es in DIESEM Lauf
NICHT an. Vermerke stattdessen sichtbar (z. B. als Fussnote unter der Tabelle oder in der
Entscheidung-Spalte), dass CHG-05 der Ausloeser ist, 05-deployment_one.md anzulegen und
Re-Audit 05 nachzuholen — als offener Folgeschritt, nicht still uebergangen.

Commit AUFGABE A allein (ein Commit, kein add -A): "docs(maintenance): CHG-05 Video-Node-Freigabe eintragen".

AUFGABE B — Branch sichern (Backup, KEIN Merge):
- git push feature/dual-mode nach origin (setze Upstream falls noetig).
- Falls .git/index.lock oder aehnliche Sperren stoeren: lokal entfernen (bekannter Gotcha),
  dann erneut.
- KEIN Merge nach master, KEIN Tag.

VERIFIKATION (messen, nicht annehmen):
- git log --oneline zeigt die 3 Vorgaenger-Commits + den neuen CHG-05-Commit auf feature/dual-mode.
- git status: Branch ist mit origin/feature/dual-mode synchron (nichts ahead nach Push).
- Kurzer Ergebnisbericht im Chat: was eingetragen, Commit-Hash, Push bestaetigt, plus die
  zwei offenen Folgepunkte (05-deployment anlegen + Re-Audit 05 / adb-root-CRA).

CONSTRAINTS: ein Commit je logischer Aenderung, kein add -A, kein Merge, keine weiteren
Dateien anfassen als 06-maintenance_one.md.
