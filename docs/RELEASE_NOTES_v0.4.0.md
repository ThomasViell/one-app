# DrainQ.ONE v0.4.0 Release-Notes

**Veröffentlichung:** 2026-05-21  
**Basis:** v0.3.0 + L10N Phasen 0–7

---

## 🎯 Highlights

### Zentrale Lokalisierung über DrainQ.Web Portal
Die Übersetzungsverwaltung für DrainQ.ONE wurde vollständig modernisiert. Strings werden nicht mehr im App-Code hardcoded, sondern zentral im DrainQ.Web Portal gepflegt. Das ermöglicht:

- **Schnellere Übersetzungs-Zyklen:** Neue Sprachen werden ohne App-Update freigegeben
- **Partner-Review:** Jede Übersetzung wird von Landes-Experten überprüft
- **DeepL-Integration:** Maschinelle Vorbefüllung + Glossar-Support für Fachbegriffe
- **Bessere Qualität:** Keine automatischen MT-Übersetzungen mehr ohne Review
- **Kleinere APK:** ≈ 940 kB Größenersparnis (35 Sprachen → 2 Sprachen gebündelt)

---

## 📝 Was hat sich für Nutzer geändert?

### 1. Spracheinstellung nach Update
**Aktion erforderlich:** Falls Sie eine andere Sprache als Deutsch oder Englisch nutzen:
1. Nach dem Update wird die App auf **Deutsch zurückgesetzt**
2. Öffnen Sie **Einstellungen → Sprache & Übersetzungen**
3. Wählen Sie Ihre Sprache aus
4. Klicken Sie auf **„Herunterladen"** (einmalig über WLAN empfohlen)
5. Die Sprache wird gecacht und ist danach auch offline verfügbar

**Warum?** Alte Übersetzungen wurden durch Partner-Review-Prozess ersetzt. Die neue Qualität ist deutlich besser.

### 2. Neue Sprachen-Management-Funktion
- **Verfügbare Sprachen sehen:** Einstellungen → Sprache & Übersetzungen
- **Sprachen löschen:** Nicht benötigte Sprachen können gelöscht werden (Speicher sparen)
- **Automatischer Refresh:** Die aktive Sprache wird regelmäßig aktualisiert (nur mit WLAN)

### 3. Internet erforderlich für neue Sprachen
- DE und EN funktionieren auch **ohne Internet** (gebündelt in der App)
- Weitere Sprachen benötigen **einmalig** WLAN zum Download
- Nach Download: vollständig **offline verfügbar**

---

## 🔧 Technische Details

### LocalizationManager Refactor
- **Alte Methode:** ~300 Keys × 35 Sprachen als Kotlin-Map (≈10.000 Zeilen Code)
- **Neue Methode:** JSON-Bundles (DE+EN) + on-demand Download aus Portal-API

### Bundle-Struktur
```
app/src/main/res/raw/
├── l10n_de.json     (411 Keys, ~17 kB)
└── l10n_en.json     (411 Keys, ~18 kB)
```

### API-Integration
```
GET /api/locales?app=one
  → Liste verfügbarer Sprachen mit Status
  
GET /api/translations/{locale}.json?scope=one,shared
  → Sprachpaket zum Download
```

### Hardcoded-String-Audit
- **Phase 6:** 69 hardcoded Strings in UI-Code gefunden, 65 behobenen
- **Benchmark:** Alle Composables nutzen jetzt `S("KEY")` oder `LocalizationManager.t("KEY")`
- **Bericht:** `docs/LOCALIZATION_AUDIT_REPORT_ONE.md`

---

## 📊 Statistiken

| Metrik | Wert |
|--------|------|
| Keys in Phase 6 gefunden | 69 |
| Keys behoben | 65 |
| Neue Keys in Bundle | 57 |
| Gesamte Keys im Bundle | 411 (DE + EN) |
| APK-Größen-Ersparnis | ≈940 kB (Release-Build) |
| Build-Status | ✅ Erfolgreich |
| Test-Status | ✅ 226 Unit-Tests grün |
| KRITIS-Check | ✅ Bestanden |

---

## 🐛 Bekannte Einschränkungen

1. **Keine Hintergrund-Updates:** Nur die aktuell gewählte Sprache wird automatisch aktualisiert
2. **Glossar begrenzt:** DeepL-Glossar mit ~50 Fachbegriffen (wird erweitert)
3. **Neue Partner-Onboarding erforderlich:** Für jede neue Sprache muss ein Übersetzungs-Partner benannt werden
4. **Keine Offline-Download-Liste:** Sprachen müssen einzeln heruntergeladen werden (nicht als Paket)

---

## 🔄 Migrations-Schritte für Admin/DevOps

### Einmaliger Datenbankschritt (Portal)
```sql
-- Im DrainQ.Web Portal durchführen:
INSERT INTO Locales (code, displayName, status, partnerId)
  VALUES ('de', 'Deutsch', 'core', NULL),
         ('en', 'English', 'core', NULL);

-- Alle anderen Sprachen auf 'inactive' setzen:
UPDATE Locales SET status = 'inactive' 
  WHERE code NOT IN ('de', 'en');
```

### Rollout-Reihenfolge
1. **Portal:** Migrations-Skript ausführen (DE+EN importieren, Rest deaktivieren)
2. **App:** APK mit v0.4.0 ausrollen
3. **Kommunikation:** Nutzer informieren (siehe Abschnitt „Was hat sich geändert?")
4. **Monitoring:** App-Logs auf Download-Fehler überwachen

---

## 🚀 Zukunftsschritte (Phase 8+)

- [ ] Partner pro Land benennen (Translator/Reviewer-Rollen)
- [ ] Glossar mit ~200 weiteren Fachbegriffen aufbauen
- [ ] Weitere Sprachen schrittweise aktivieren (PL, FR, ES, NL, …)
- [ ] Hintergrund-Update-Mechanismus erweitern (mit Notification)
- [ ] Offline-Download-Paket-Funktion hinzufügen

---

## ⚠️ Wichtige Hinweise

### Für Bestands-Nutzer
- **Update-Prozess:** Speichern Sie alle Projekte, bevor Sie aktualisieren
- **Sprachauswahl:** Nutzer mit nicht-DE/EN Sprache müssen diese neu laden
- **Netzwerk:** Erstes Laden der Sprache benötigt WLAN (nach Laden offline möglich)

### Für IT-Administratoren
- **Portal-Vorbereitung:** Stellen Sie sicher, dass DE+EN im Portal aktiv sind
- **Kommunikation:** Nutzer vor Update über Sprachauswahl informieren
- **Support:** Erwarten Sie Fragen zu Sprachen-Downloads

---

## 📞 Unterstützung

### Häufig gestellte Fragen

**F: Meine Sprache ist weg, was machen ich jetzt?**  
A: Siehe Abschnitt „Spracheinstellung nach Update" — Sprache neu herunterladen.

**F: Brauche ich Internet zum Download?**  
A: Ja, einmalig über WLAN. Nach dem Download ist die Sprache offline verfügbar.

**F: Kann ich alte Sprachen zurückbekommen?**  
A: Nur wenn der Partner die neue Übersetzung freigegeben hat. Kontaktieren Sie Ihren Admin.

**F: Wird es mehr Sprachen geben?**  
A: Ja, sobald Partner pro Land benannt werden. Aktuell: DE + EN aktiv.

---

**Version:** 0.4.0  
**Gültig ab:** 2026-05-21  
**Lokalisierungs-Referenz:** `docs/concepts/L10N_PORTAL_KONZEPT.md` (v1.1)  
**ADR:** `docs/adr/0010-l10n-portal-as-sot.md`
