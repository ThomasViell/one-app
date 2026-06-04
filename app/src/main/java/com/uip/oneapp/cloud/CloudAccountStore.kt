package com.uip.oneapp.cloud

import android.content.Context

/**
 * Platzhalter für die DrainQ-Cloud-Kontoanbindung.
 *
 * Aktuell bewusst LEER — es gibt noch keine echte Authentifizierung und keinen Endpunkt.
 * Die Struktur ist so vorbereitet, dass später ein OAuth-/Token-Flow gegen drainq.web
 * andocken kann:
 *  - [saveSession] / [clearSession] persistieren das Access-/Refresh-Token.
 *  - Die Persistenz soll dann über Android Keystore bzw. EncryptedSharedPreferences
 *    (androidx.security:security-crypto) erfolgen — KEINE Secrets im Code, kein Klartext.
 *
 * Bis dahin meldet [isLoggedIn] stets false und es wird nichts gespeichert.
 */
class CloudAccountStore(@Suppress("unused") private val context: Context) {

    /** Eingeloggt? Solange kein Auth-Flow existiert: immer false. */
    fun isLoggedIn(): Boolean = false

    /** Zuletzt verwendete E-Mail (für Vorbelegung). Noch nicht persistiert. */
    fun savedEmail(): String? = null

    /**
     * TODO(cloud): Token sicher ablegen, sobald der OAuth-Flow steht.
     * Vorgesehen: EncryptedSharedPreferences mit MasterKey aus dem Android Keystore.
     */
    @Suppress("UNUSED_PARAMETER")
    fun saveSession(email: String, accessToken: String, refreshToken: String?) {
        // absichtlich leer — Platzhalter
    }

    /** TODO(cloud): Token/Session löschen (Logout). */
    fun clearSession() {
        // absichtlich leer — Platzhalter
    }
}
