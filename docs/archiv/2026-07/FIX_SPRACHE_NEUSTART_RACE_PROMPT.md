# Reparaturauftrag — Sprach-Neustart-Race: gewählte Sprache geht beim Neustart verloren

Quelle: Thomas-Selbst-Testrunde 12.07.2026 (0.5.7 Beta, Thomas-ONE). M3-Kern (Umschaltung + „Quick capture") OK, aber **„Jetzt neu starten" verwirft die neue Sprache**.
Branch: `feature/dual-mode`. Kein Merge, kein Tag. **Merge-Gate-relevant.**
Empfohlenes Modell: **Sonnet / mittel** (kleiner, lokaler Fix; Diagnose erledigt).

---

## Befund (am Gerät gemessen)

Sprachwechsel auf Englisch über den App-Dropdown (Einstellungen → Anzeige & Bedienung → Sprache).
Danach erscheint der Dialog „Neustart erforderlich".
- Weg **„Später"**: funktioniert — die Oberfläche kippt sofort live auf Englisch. [verifiziert]
- Weg **„Jetzt neu starten"**: nach dem Neustart ist die App wieder **komplett Deutsch**. [verifiziert]

## Ursache [Sicher — Code verifiziert]

**Race zwischen asynchronem Persistieren und sofortigem Prozess-Kill.**

`ui/localization/LocalizationManager.kt`:
```kotlin
fun setLanguage(context: Context, langCode: String) {
    _currentLanguage.value = langCode
    CoroutineScope(Dispatchers.IO).launch {          // ASYNC — fire-and-forget
        context.langStore.edit { it[KEY_LANGUAGE] = langCode }
    }
}
```

`ui/screens/settings/SettingsScreen.kt`, `pendingLangCode?.let { … }`-AlertDialog, `confirmButton`:
```kotlin
TextButton(onClick = {
    LocalizationManager.setLanguage(context, langCode)   // stößt async-Write nur an
    pendingLangCode = null
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
    intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())   // KILLT SOFORT
}) { Text(restartNow) }
```
`setLanguage` startet den DataStore-Write auf einem eigenen IO-Scope und kehrt sofort zurück. Die
folgenden Zeilen killen den Prozess per `Process.killProcess(myPid())`, bevor der Write durabel auf
der Platte liegt. Beim nächsten Start liest `LocalizationManager.init()` den **alten** Wert:
```kotlin
val saved = prefs[KEY_LANGUAGE] ?: "de"   // Write kam nie an → „de"
```
Der „Später"-Weg killt nicht → der Write läuft normal durch → dort ist alles korrekt. Das erklärt
auch, warum die Abnahme am 11.07. „M3 ok" war: getestet wurde nur der Live-Wechsel, nie der
Kill-Weg. (→ Default-/Messen-statt-Annehmen-Regel.)

---

## Fix [eindeutig]

Die Sprache **persistieren und den Write abwarten**, BEVOR der Prozess gekillt wird. Kein
Main-Thread-`runBlocking` — die bereits vorhandene Coroutine-Scope der Komposition nutzen.

**Datei 1:** `ui/localization/LocalizationManager.kt` — suspend-Variante ergänzen (bestehendes
`setLanguage` für die Live-/„Später"-Pfade unverändert lassen):
```kotlin
/** Wie setLanguage, aber suspendet bis der DataStore-Write durabel ist. Vor einem
 *  anschließenden Prozess-Kill (Neustart) verwenden, sonst geht die Sprache verloren. */
suspend fun setLanguageAwait(context: Context, langCode: String) {
    _currentLanguage.value = langCode
    context.langStore.edit { it[KEY_LANGUAGE] = langCode }   // suspendet bis geschrieben
}
```

**Datei 2:** `ui/screens/settings/SettingsScreen.kt` — im `confirmButton` den Kill in die
vorhandene `scope`-Coroutine verlagern und ERST nach `setLanguageAwait` neu starten
(`val scope = rememberCoroutineScope()` existiert bereits in `SettingsScreen`):
```kotlin
TextButton(onClick = {
    scope.launch {
        LocalizationManager.setLanguageAwait(context, langCode)   // erst persistieren + abwarten
        pendingLangCode = null
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}) { Text(restartNow) }
```
- Der `dismissButton` („Später") und `onDismissRequest` bleiben auf `setLanguage` (kein Kill → async ok).
- Nichts an `init()`, `getString`, `S()`, `translations` ändern — die sind korrekt.

---

## Verifikation

**Am Gerät (Pflicht — der Race ist zeitabhängig):**
1. Deutsch → Sprache → English → **„Jetzt neu starten"** → nach dem Neustart ist die App **Englisch** und bleibt es.
2. Zurück: English → Sprache → Deutsch → „Jetzt neu starten" → App ist Deutsch.
3. Gegenprobe „Später": Umschalten kippt weiterhin sofort live, und ein manueller Neustart hält die Sprache.
4. 3–4× DE↔EN mit „Jetzt neu starten" hin und her — jeder Neustall hält die zuletzt gewählte Sprache.

**Unit-Test (ergänzend):**
- `setLanguageAwait` schreibt und ein anschließendes Lesen aus `langStore` liefert `langCode` zurück
  (beweist Durabilität; der Kill-Pfad selbst ist nicht unit-testbar).

---

## Nicht anfassen
`init()` (liest korrekt), `getString`/`S` (Recompose ok), `translations`-Map (de/en vollständig),
die Live-/„Später"-Pfade (funktionieren). BETA_LANGUAGE_GATE (de/en) bleibt.
