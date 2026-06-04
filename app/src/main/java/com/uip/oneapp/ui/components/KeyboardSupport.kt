package com.uip.oneapp.ui.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S

/**
 * GLOBALE REGEL: Überall, wo eine Software-Tastatur eingeblendet werden kann, gehört ein
 * sichtbarer „Tastatur einklappen"-Knopf hin (typischerweise in die TopAppBar-`actions`).
 *
 * Hintergrund: Auf der ONE-Hardware schließt `focusManager.clearFocus()` allein die
 * Tastatur nicht zuverlässig — daher wird hier zusätzlich hart über den
 * InputMethodManager geschlossen.
 */

/** Liefert eine Funktion, die die Software-Tastatur hart über das System schließt. */
@Composable
fun rememberKeyboardHider(): () -> Unit {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    return {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        focusManager.clearFocus()
    }
}

/**
 * Tastatur-Sprachhinweis (#14): liefert die aktuelle App-Sprache als LocaleList, damit die
 * Software-Tastatur die passende Sprache/Layout vorschlägt. An `KeyboardOptions(hintLocales = …)`
 * der Textfelder hängen. (Wirkt ab Compose 1.7; die Tastatur muss die Sprache installiert haben.)
 */
@Composable
fun appHintLocales(): LocaleList {
    val lang by LocalizationManager.currentLanguage.collectAsState()
    return remember(lang) { LocaleList(Locale(lang)) }
}

/** Standard-„Tastatur einklappen"-Button (40 dp). In TopAppBar-`actions` o. ä. einsetzen. */
@Composable
fun KeyboardHideButton(onHide: () -> Unit = rememberKeyboardHider()) {
    IconButton(onClick = onHide) {
        Icon(
            Icons.Default.KeyboardHide,
            contentDescription = S("hide_keyboard"),
            modifier = Modifier.size(40.dp)
        )
    }
}
