package com.uip.oneapp.export

import android.content.Context
import com.uip.oneapp.R
import java.io.File

/**
 * Auflösung des Report-Logos für PDF-Berichte (CEO-Entscheid 2026-06-07):
 * Das mitgelieferte NSP3CT-Logo ist der Standard, solange der Benutzer nichts
 * anderes wählt. Wertekonvention des Settings `company_logo_path`:
 *  - ""      → Standard: mitgeliefertes NSP3CT-Logo (Default)
 *  - "none"  → bewusst kein Logo im Bericht
 *  - <Pfad>  → vom Benutzer gewähltes eigenes Logo
 */
object ReportLogo {

    const val PREF_NONE = "none"

    /** Liefert die zu druckende Logo-Datei oder null (= kein Logo). */
    fun resolve(context: Context, pref: String): File? = when {
        pref == PREF_NONE -> null
        pref.isNotEmpty() ->
            File(pref).takeIf { it.exists() && it.length() > 0 }
                ?: builtin(context) // eigener Pfad kaputt/gelöscht → Standard statt gar nichts
        else -> builtin(context)
    }

    /** Materialisiert das mitgelieferte NSP3CT-Logo einmalig nach filesDir (iText braucht einen Pfad). */
    private fun builtin(context: Context): File {
        val f = File(context.filesDir, "builtin_report_logo.png")
        if (!f.exists() || f.length() == 0L) {
            context.resources.openRawResource(R.raw.logo_nsp3ct_report).use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
        }
        return f
    }
}
