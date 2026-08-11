package com.uip.oneapp.ui.components

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * „Stash-Impuls" gegen die launcher3-System-Taskbar (ITYPE_EXTRA_NAVIGATION_BAR) auf der ONE.
 *
 * WARUM (On-Device-Befund 10.08.2026, e92df62d, Kette taskbar-balken): Jedes fremde Systemfenster
 * (Power-Dialog, Berechtigungsdialog, Benachrichtigungsleiste, Soft-Tastatur) „unstasht" die
 * Taskbar; sie bleibt als 87-px-Balken am unteren Rand stehen und verdeckt Bedienelemente.
 * Weder `WindowInsetsController.hide()` noch die Legacy-Immersive-Flags ziehen sie danach wieder
 * ein (Befund 0.4.1). Und sie ist für die App in den Insets unsichtbar: ITYPE_EXTRA_NAVIGATION_BAR
 * bleibt visible=false, auch `tappableElement()` ändert sich zwischen „Balken sichtbar" und
 * „gestasht" nicht — der Sekundenwächter/Insets-Listener in MainActivity kann sie prinzipiell
 * nicht detektieren (adb-Beleg via `dumpsys window` InsetsState, 10.08.2026).
 *
 * Einziger bekannter Heiler: Ein App-eigenes FOKUSSIERBARES Fenster geht auf und wieder zu
 * (Sprachauswahl in den Einstellungen; am 11.08. zusätzlich mit dem Notiz-Dialog belegt).
 * Ein NICHT fokussierbares Popup reicht dagegen nicht (am 11.08. am Gerät widerlegt, Variante A).
 * Genau den belegten Weg bildet diese Klasse nach: ein 1×1-px großer, transparenter, fokussierbarer
 * Dialog mit den Legacy-Immersive-Flags (5894, Technik aus [HideSystemBarsInDialog]) geht kurz auf
 * und sofort wieder zu.
 *
 * EINGABE: Das Impuls-Fenster nimmt zwar den Fensterfokus (das ist der wirksame Teil), ist aber
 * per FLAG_NOT_TOUCHABLE + FLAG_NOT_TOUCH_MODAL für Berührungen vollständig durchlässig — ein
 * Tippen während der ~150 ms erreicht weiterhin die Activity und geht nicht verloren (N2,
 * am 11.08. am Gerät gegengeprüft: heilt mit diesen Kennzeichen unverändert). Zusätzlich
 * unterdrückt MainActivity den Impuls, solange die Soft-Tastatur steht (N3).
 *
 * SELBST-RETRIGGER (N1): Der Impuls entzieht der Activity selbst den Fensterfokus und würde sich
 * über die Fokus-Rückkehr endlos neu auslösen. Gesperrt wird das strukturell, nicht zeitlich:
 * [isImpulseShowing] ist genau während des eigenen Fensters wahr; MainActivity macht einen
 * Fokusverlust, der in dieses Fenster fällt, gar nicht erst zum Auslöser. Der Debounce
 * ([DEBOUNCE_MS]) bleibt als zweite Ebene, trägt aber nicht mehr allein.
 */
class TaskbarRestash(private val anchor: View) {

    private var lastImpulseMs = 0L
    private var impulseShowing = false
    private var current: Dialog? = null
    private val dismissRunnable = Runnable { closeImpulse() }

    /**
     * Wahr, solange das eigene Impuls-Fenster steht. MainActivity fragt das im Fokus-Callback ab:
     * Ein Fokusverlust, der auftritt, während der eigene Impuls steht, ist der eigene und darf den
     * Auslöser „Fokus-Rückkehr" nicht scharf machen.
     */
    val isImpulseShowing: Boolean get() = impulseShowing

    /** Löst einen Stash-Impuls aus, sofern Debounce und Sperre es zulassen. */
    fun fire() {
        val now = SystemClock.uptimeMillis()
        if (impulseShowing || now - lastImpulseMs < DEBOUNCE_MS) return
        lastImpulseMs = now
        impulseShowing = true

        val size = (anchor.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val px = View(anchor.context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val dialog = Dialog(anchor.context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(px)
        dialog.setCancelable(false)
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.attributes = w.attributes.apply {
                width = size
                height = size
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                windowAnimations = 0 // keine Fenster-Animation → kein sichtbares Flackern
                // LAYOUT_IN_SCREEN: Fenster im Bildschirm-, nicht im Inhaltsbereich.
                // NOT_TOUCH_MODAL + NOT_TOUCHABLE: Das Fenster verschluckt keine Berührung —
                // Tippen während des Impulses erreicht die Activity (N2). Der Fensterfokus,
                // auf den es ankommt, bleibt davon unberührt (kein FLAG_NOT_FOCUSABLE).
                flags = flags or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        }
        current = dialog
        Log.d(TAG, "Stash-Impuls")
        dialog.show()
        // Legacy-Immersive-Flags auf das Impuls-Fenster selbst (5894, Original-App-Technik).
        @Suppress("DEPRECATION")
        dialog.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE // = 5894
        anchor.postDelayed(dismissRunnable, SHOW_MS)
    }

    /**
     * Beim Abbau der Activity aufrufen: nimmt das ausstehende Schließen zurück und schließt ein
     * noch stehendes Impuls-Fenster. Ohne das kann das Runnable nach dem Abbau laufen, `dismiss()`
     * werfen und die Sperre dauerhaft gesetzt lassen — der Mechanismus wäre stumm tot.
     */
    fun release() {
        anchor.removeCallbacks(dismissRunnable)
        closeImpulse()
    }

    private fun closeImpulse() {
        val dialog = current
        current = null
        impulseShowing = false
        if (dialog == null) return
        try {
            dialog.dismiss()
        } catch (e: Exception) {
            // z. B. „View not attached to window manager" nach Activity-Abbau: Zustand ist oben
            // bereits freigegeben, der Mechanismus bleibt arbeitsfähig.
            Log.w(TAG, "Impuls-Fenster ließ sich nicht schließen: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TaskbarRestash"
        private const val DEBOUNCE_MS = 500L
        private const val SHOW_MS = 150L
    }
}
