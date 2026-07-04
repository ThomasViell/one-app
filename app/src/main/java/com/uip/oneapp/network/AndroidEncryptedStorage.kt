package com.uip.oneapp.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android-Schicht des [SecretKeyValueStore] (Auto-Reconnect W1): EncryptedSharedPreferences
 * mit AES256-Master-Key im Android Keystore. Die Passphrasen bekannter ONEs liegen damit
 * NIE im Klartext auf dem Dateisystem; ein App-Backup enthält nur Ciphertext ohne Schlüssel
 * (der Keystore-Key verlässt das Gerät nicht).
 *
 * Robustheit: Ist der Keystore-Eintrag korrupt (z. B. nach Factory-Reset-Restore), wird die
 * Prefs-Datei einmalig verworfen und neu angelegt — die ONE muss dann einmal neu per QR
 * gekoppelt werden. Scheitert auch das, degradiert der Store zum No-op (Auto-Reconnect
 * schlicht inaktiv, kein Crash).
 */
class AndroidEncryptedStorage(
    context: Context,
    private val fileName: String = FILE_KNOWN_ONES,
) : SecretKeyValueStore {

    private val appContext = context.applicationContext

    // Lazy: Keystore-/Crypto-Init erst beim ersten Zugriff, nicht zur DI-Konstruktion.
    private val prefs: SharedPreferences? by lazy {
        try {
            create()
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences defekt (${e.javaClass.simpleName}) — setze zurück")
            try {
                appContext.deleteSharedPreferences(fileName)
                create()
            } catch (e2: Exception) {
                Log.e(TAG, "Verschlüsselter Speicher nicht verfügbar (${e2.javaClass.simpleName}) — Auto-Reconnect inaktiv")
                null
            }
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    override fun get(key: String): String? = try {
        prefs?.getString(key, null)
    } catch (_: Exception) {
        null
    }

    override fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    override fun keys(): Set<String> = try {
        prefs?.all?.keys?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    companion object {
        private const val TAG = "EncryptedStorage"
        private const val FILE_KNOWN_ONES = "known_ones"
    }
}
