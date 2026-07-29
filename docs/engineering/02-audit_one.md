# Audit: Entwurf — one

| Feld           | Wert                              |
| -------------- | --------------------------------- |
| Datei          | 02-project_one.md                 |
| Dokumentdatum  | 2026-07-14                        |
| Auditdatum     | 2026-07-14                        |
| Auditor        | **Opus — unabhängiges, nicht-bauendes Modell** (Bau: Sonnet). AI-first-Rollentrennung nach [[feedback_audit_model]]. |

---

| Abschnitt                     | Kriterium                                                                 | Status |
| ----------------------------- | ------------------------------------------------------------------------- | ------ |
| 2. Entwicklungsumgebung       | Realer Stack (Kotlin/Compose/Room/Koin/Media3/iText7/FFmpeg/NDK/Gradle) belegt | bestanden |
| 3. Architektur                | MVVM/Clean; Schichten mit konkreten Klassen                               | bestanden |
| 4. Datenmodell                | ENT-01..04 = reale Room-Entities; Persistenz + Migration belegt            | bestanden |
| 5. Module (MOD-01..13)        | Jedes Prio-A-REF (REF-01..12) einem MOD zugeordnet; Phantom-Klasse MOD-10 aufgelöst | bestanden (nach Nachbesserung) |
| 6. UI (SCR-01..13)            | Deckt sich 1:1 mit ui/screens/*                                           | bestanden |
| 7. Integrationen              | RTSP/V4L2, Meter, Update-Portal, Karten, USB; MQTT-Fakt korrigiert         | bestanden (nach Nachbesserung) |
| 8. Sicherheit                 | REN-09/10/11/13 abgebildet; konsistent mit Code; KRITIS nicht einschlägig  | bestanden |
| 10. Zusammenfassung           | Tech-Stack, Schlüsselklassen, Reihenfolge, Modulabhängigkeiten             | bestanden |

**Feststellungen:**
- **Faktenfehler v1 (gefangen + korrigiert):** §7/§2 behaupteten fälschlich, Paho-MQTT fehle in `build.gradle.kts`. Opus-Faktencheck: Paho ist deklariert (`mqttv3:1.2.5` + `android.service:1.1.1`), aber im Quellcode/Manifest ungenutzt → tote Dependency. Dokument korrigiert; als CEO-Entscheid (entfernen) markiert.
- **Phantom-Klasse (gefangen + korrigiert):** MOD-10 nannte `FallbackHotspotStarter` ohne Quelldatei → ersetzt durch reales Zwei-Schichten-Modell `AndroidSoftApStarter` + `AndroidLohsStarter`.

**Auditergebnis:** Nach Nachbesserung **BESTANDEN**. Freigabe erteilt. Offener Tech-Debt-Fund (tote MQTT-Dependency) → als CHG in 06-maintenance zu führen.
