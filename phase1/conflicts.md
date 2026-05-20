# Phase 1 — Key-Mapping Konflikte

**Datum:** 2026-05-20  
**Regel:** Konflikt = gleicher oder ähnlicher DE-Wert, aber abweichender EN-Wert ODER abweichende semantische Bedeutung.

---

## Harte Konflikte (scope=one, aus Shared-Kandidaten ausgeschlossen)

### CONFLICT-01: `pdf_company_label`

| Attribut | ONE | Windows |
|---|---|---|
| old_key | `pdf_company_label` | — |
| new_key | `PDF_COMPANY_LABEL` | `MANDANT_SECTION_COMPANY` |
| scope | **one** | shared |
| DE | "Firma" | "FIRMA" (Großbuchstaben) |
| EN | "Company" | unbekannt |

**Begründung:** DE-Wert unterscheidet sich durch Großschreibung ("Firma" vs. "FIRMA"). In Windows steht `MANDANT_SECTION_COMPANY` im Kontext des Mandanten-/Unternehmens-Abschnitts. Im ONE-App-Kontext ist es die Firmenbezeichnung im PDF-Header. Unterschiedlicher semantischer Kontext + DE-Mismatch → scope=one.

**Entscheidung:** `PDF_COMPANY_LABEL` als neuer ONE-Key.

---

### CONFLICT-02: `position_label`

| Attribut | ONE | Windows |
|---|---|---|
| old_key | `position_label` | — |
| new_key | `POSITION_LABEL` | `MANHOLE_FINDING_SECTION_POSITION` |
| scope | **one** | shared |
| DE | "Position:" (mit Doppelpunkt) | "Position" (ohne Doppelpunkt) |
| EN | "Location" | unbekannt (vermutlich "Position") |

**Begründung:** ONE DE-Wert enthält einen Doppelpunkt ("Position:"), Windows nicht ("Position"). Außerdem weicht der EN-Wert ab: ONE="Location", Windows (für `MANHOLE_FINDING_SECTION_POSITION`) vermutlich "Position". Beides deutet auf unterschiedliche Verwendung hin. Hinweis: `pdf_position_label` (DE="Position", ohne Doppelpunkt) ist korrekt auf `MANHOLE_FINDING_SECTION_POSITION` gemappt.

**Entscheidung:** `POSITION_LABEL` als neuer ONE-Key.

---

## Weiche Konflikte (scope=shared behalten, aber dokumentiert)

### SOFT-01: `not_reachable`

| Attribut | ONE | Windows |
|---|---|---|
| old_key | `not_reachable` | — |
| new_key | `WELCOME_PORTAL_OFFLINE` | `WELCOME_PORTAL_OFFLINE` |
| scope | **shared** | shared |
| DE | "Nicht erreichbar" | "Nicht erreichbar" |
| EN ONE | "Not accessible" | — |
| EN WIN (erwartet) | — | vermutlich "Offline" oder "Not reachable" |

**Begründung:** DE-Wert stimmt exakt überein. EN-Wert von ONE ist "Not accessible" — der Windows-EN-Wert ist aus dieser Phase nicht bekannt. In Phase 7 beim Cutover prüfen, ob EN-Wert in Windows verschieden ist. Wenn ja: ONE-spezifischer Key anlegen.

**Entscheidung:** scope=shared (vorläufig). Cutover-Check in Phase 7.

---

### SOFT-02: `scan`

| Attribut | ONE | Windows |
|---|---|---|
| old_key | `scan` | — |
| new_key | `WIFI_SCAN` | `WIFI_SCAN` |
| scope | **shared** | shared |
| DE | "Scannen" | "Scannen" |
| EN ONE | "Scanning" | — |
| EN WIN (erwartet) | — | vermutlich "Scan" (Imperativ) |

**Begründung:** `WIFI_SCAN` ist in Windows ein WiFi-spezifischer Key. In ONE wird `scan` allgemeiner verwendet (Netzwerk-Scan-Button). DE stimmt exakt überein. EN-Mismatch (ONE="Scanning" Partizip vs. Windows vermutlich "Scan" Imperativ) ist möglich. Kontextunterschied ist semantisch akzeptabel — beide beschreiben einen Scan-Vorgang.

**Entscheidung:** scope=shared (vorläufig). EN-Wert in Phase 5 beim Bundle-Build prüfen.

---

## Statistik

| Typ | Anzahl |
|---|---|
| Harte Konflikte (scope=one) | 2 |
| Weiche Konflikte (scope=shared, dokumentiert) | 2 |
| **Gesamt Konflikte** | **4** |

PHASE1-CONFLICTS: 4
