package com.uip.oneapp.ui.screens.pairing

import androidx.lifecycle.ViewModel
import com.uip.oneapp.network.AccessPointController
import com.uip.oneapp.network.ApState
import kotlinx.coroutines.flow.StateFlow

/**
 * Dünne VM über den [AccessPointController] (Dual-Modus, Welle 3a) — reicht den Hotspot-
 * Zustand an den [PairingScreen] durch und schaltet ihn an/aus.
 *
 * **Bewusst KEIN Auto-Stop in [onCleared]:** Der Operator aktiviert den Hotspot, das Tablet
 * tritt bei, und der Operator wechselt zum Arbeiten in andere Screens (Inspektion). Der
 * Hotspot muss dabei aktiv bleiben — er ist der Träger der Tablet-Verbindung. Der einzige
 * Aus-Schalter ist der Toggle (CEO-Entscheid: on-demand per Schalter). Der Controller ist ein
 * Singleton, sein Zustand überlebt den Screen.
 */
class PairingViewModel(
    private val controller: AccessPointController,
) : ViewModel() {

    val state: StateFlow<ApState> = controller.state

    fun start() = controller.start()

    fun stop() = controller.stop()
}
