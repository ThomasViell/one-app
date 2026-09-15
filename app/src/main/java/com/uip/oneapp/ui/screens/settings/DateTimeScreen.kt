package com.uip.oneapp.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.system.SystemTimeSetter
import com.uip.oneapp.ui.components.DqButton
import com.uip.oneapp.ui.components.DqCard
import com.uip.oneapp.ui.components.DqIcon
import com.uip.oneapp.ui.components.HideSystemBarsInDialog
import com.uip.oneapp.ui.components.KeyboardHideButton
import com.uip.oneapp.ui.components.rememberKeyboardHider
import com.uip.oneapp.ui.help.HelpButton
import com.uip.oneapp.ui.localization.LocalizationManager
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.screens.projects.InspectionDateGuard
import com.uip.oneapp.ui.theme.DrainQTheme
import com.uip.oneapp.ui.theme.Dimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.math.abs

// =====================================================================================
// Reine Logik (Plan Schritt 11) — bewusst nicht-komponierbar, damit DateTimeScreenTest
// sie ohne Compose pruefen kann (Compose-Semantik-Tests laufen im JVM-Test nicht).
// =====================================================================================

/**
 * Wanduhr aus Datum + Uhrzeit in der gewaehlten Zone als Epochenmillisekunden.
 * Beispiel (Plan): 10.09.2026 09:27 Europe/Berlin = 07:27 UTC.
 */
fun composeEpoch(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
    ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

/**
 * Zonenliste filtern (E-2: Liste „vom Geraet gemeldet", sortiert, mit Suchfeld):
 * Teilzeichenkette, gross/klein egal, Reihenfolge stabil (sortiert), leere Eingabe = volle Liste.
 */
fun filterZones(all: Collection<String>, query: String): List<String> {
    val q = query.trim().lowercase()
    return all.sorted().filter { q.isEmpty() || it.lowercase().contains(q) }
}

/**
 * Anzeigeformat der Geraetezeit: de „10.09.2026, 09:27", en „09/10/2026, 09:27" — 24-h.
 */
fun formatForDisplay(epochMs: Long, zone: ZoneId, lang: String): String {
    val pattern = if (lang == "de") "dd.MM.yyyy, HH:mm" else "MM/dd/yyyy, HH:mm"
    return Instant.ofEpochMilli(epochMs).atZone(zone)
        .format(DateTimeFormatter.ofPattern(pattern))
}

/**
 * N-1 (B-7): Folgehandlungen laufen sofort nach dem Ergebnis, unabhaengig davon, wie lange
 * `showMessage` braucht (Snackbar-Anzeigedauer) — deshalb wird sie nebenlaeufig ueber `scope`
 * gestartet statt abgewartet. Reine Funktion (kein Compose), damit ein Test ohne
 * Instrumentierung die Reihenfolge pruefen kann (siehe DateTimeScreenTest).
 */
fun handleDateTimeResult(
    scope: CoroutineScope,
    result: SystemTimeSetter.Result,
    msg: String,
    showMessage: suspend (String) -> Unit,
    autoTimeActive: () -> Boolean,
    onShowAutoDialog: () -> Unit,
    onApplied: () -> Unit,
) {
    scope.launch { showMessage(msg) }
    when (result) {
        is SystemTimeSetter.Result.NotApplied -> if (autoTimeActive()) onShowAutoDialog()
        SystemTimeSetter.Result.Applied -> onApplied()
        else -> Unit
    }
}

/**
 * Versatzlabel „UTC+02:00" — Sommer/Winter ueber die Zonenregeln zum jeweiligen Zeitpunkt.
 */
fun zoneOffsetLabel(zone: ZoneId, epochMs: Long): String {
    val offset = zone.rules.getOffset(Instant.ofEpochMilli(epochMs))
    val sign = if (offset.totalSeconds < 0) "-" else "+"
    val hours = abs(offset.totalSeconds) / 3600
    val minutes = abs(offset.totalSeconds) % 3600 / 60
    return "UTC$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

// =====================================================================================
// Bildschirm
// =====================================================================================

/** Aktuelle Systemzone, Cache geleert (Plan-Risiko „Cache": TimeZone.getDefault haelt
 *  den Prozessstart-Stand fest und wuerde eine frisch gesetzte Zone verschweigen).
 *  ANNAHME — am Geraet nicht gemessen, Feldlauf Louis 14.09. */
private fun systemZone(): ZoneId {
    TimeZone.setDefault(null)
    return ZoneId.systemDefault()
}

/** Heute als Vorbelegung — aber NUR wenn plausibel; beim 2021-Reset bleibt das Feld leer,
 *  damit der Bediener das Datum ausdruecklich korrigieren muss (E-3). */
private fun todayIfPlausible(): LocalDate? {
    val today = LocalDate.now()
    return if (InspectionDateGuard.isSystemClockPlausible(today, BuildConfig.BUILD_YEAR)) today else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeScreen(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val c = DrainQTheme.colors
    val currentLang by LocalizationManager.currentLanguage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val hideKeyboard = rememberKeyboardHider()
    val coroutineScope = rememberCoroutineScope()

    // --- Texte (im Composable-Body, damit sie im LaunchedEffect/onClick liegen duerfen) ---
    val currentLabel = S("datetime_current")
    val dateLabel = S("datetime_date")
    val timeLabel = S("datetime_time")
    val zoneLabel = S("datetime_zone")
    val zoneSearchLabel = S("datetime_zone_search")
    val applyLabel = S("datetime_apply")
    val pickHint = S("datetime_pick_hint")
    val okLabel = S("ok")
    val cancelLabel = S("cancel")
    val appliedMsg = S("datetime_applied")
    val deniedMsg = S("datetime_denied")
    val notAppliedMsg = S("datetime_not_applied")
    val invalidZoneMsg = S("datetime_invalid_zone")
    val invalidTimeMsg = S("datetime_invalid_time")
        .replace("{year}", InspectionDateGuard.minPlausibleYear(BuildConfig.BUILD_YEAR).toString())
    val autoTitle = S("datetime_auto_title")
    val autoDesc = S("datetime_auto_desc")
    val autoConfirm = S("datetime_auto_confirm")
    val autoIncompleteMsg = S("datetime_auto_incomplete")

    // --- Eingaben (E-3: Datum nur vorbelegt, wenn plausibel) ---
    var selectedDate by remember { mutableStateOf<LocalDate?>(todayIfPlausible()) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(LocalTime.now()) }
    var selectedZone by remember { mutableStateOf<ZoneId?>(systemZone()) }

    // --- Laufende Anzeige „Aktuell" (1-s-Takt) ---
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shownZone by remember { mutableStateOf(systemZone()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // --- Picker-Zustaende ---
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var zoneQuery by remember { mutableStateOf("") }
    var showAutoDialog by remember { mutableStateOf(false) }

    // --- Ergebnis → Snackbar, danach zuruecknehmen (Muster weatherError) ---
    val dateTimeResult by viewModel.dateTimeResult.collectAsState()
    LaunchedEffect(dateTimeResult) {
        val result = dateTimeResult ?: return@LaunchedEffect
        val msg = when (result) {
            SystemTimeSetter.Result.Applied -> appliedMsg
            is SystemTimeSetter.Result.Denied -> deniedMsg
            is SystemTimeSetter.Result.NotApplied -> notAppliedMsg
            is SystemTimeSetter.Result.InvalidZone -> invalidZoneMsg
            SystemTimeSetter.Result.InvalidTime -> invalidTimeMsg
        }
        // N-1 (B-7): Die Meldung laeuft nebenlaeufig (bis zu ~10 s, SnackbarDuration.Long) —
        // Folgehandlungen (Automatik-Dialog anbieten, shownZone nachfuehren) warten NICHT darauf.
        handleDateTimeResult(
            scope = coroutineScope,
            result = result,
            msg = msg,
            showMessage = { text ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Long)
            },
            autoTimeActive = { viewModel.autoTimeActive() },
            // Nachtrag 2 Punkt 5: Nur wenn die Automatik wirklich an ist, wird das Abschalten
            // ANGEBOTEN — mit Erklaerung und nur auf ausdrueckliche Bestaetigung.
            onShowAutoDialog = { showAutoDialog = true },
            onApplied = { shownZone = systemZone() },
        )
        viewModel.clearDateTimeResult()
    }

    Scaffold(
        containerColor = c.bgWindow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DateTimeTopBar(
                title = S("datetime_title"),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Space12),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Space12),
        ) {
            // Karte „Aktuell": laufende Geraetezeit + Zone + Versatz
            DqCard {
                Column {
                    Text(
                        currentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                    Text(
                        formatForDisplay(nowMillis, shownZone, currentLang),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textPrimary,
                    )
                    Text(
                        "${shownZone.id} (${zoneOffsetLabel(shownZone, nowMillis)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            }

            // Karte „Datum"
            DqCard(modifier = Modifier.clickable {
                hideKeyboard()
                showDatePicker = true
            }) {
                PickRow(
                    title = dateLabel,
                    value = selectedDate?.format(dateFmt) ?: pickHint,
                    valueIsHint = selectedDate == null,
                )
            }

            // Karte „Uhrzeit"
            DqCard(modifier = Modifier.clickable {
                hideKeyboard()
                showTimePicker = true
            }) {
                PickRow(
                    title = timeLabel,
                    value = selectedTime?.format(timeFmt) ?: pickHint,
                    valueIsHint = selectedTime == null,
                )
            }

            // Karte „Zeitzone": Suchfeld + Geraeteliste (E-2)
            DqCard {
                Column {
                    Text(zoneLabel, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    Spacer(Modifier.height(Dimensions.Space8))
                    OutlinedTextField(
                        value = zoneQuery,
                        onValueChange = { zoneQuery = it },
                        placeholder = { Text(zoneSearchLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Dimensions.Space8))
                    val zones = remember(zoneQuery) {
                        filterZones(ZoneId.getAvailableZoneIds(), zoneQuery)
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(zones, key = { it }) { zoneId ->
                            ZoneRow(
                                zoneId = zoneId,
                                selected = selectedZone?.id == zoneId,
                                onClick = {
                                    hideKeyboard()
                                    selectedZone = ZoneId.of(zoneId)
                                },
                            )
                        }
                    }
                }
            }

            // Uebernehmen — erst wenn alle drei Eingaben stehen
            DqButton(
                text = applyLabel,
                enabled = selectedDate != null && selectedTime != null && selectedZone != null,
                onClick = {
                    val date = selectedDate ?: return@DqButton
                    val time = selectedTime ?: return@DqButton
                    val zone = selectedZone ?: return@DqButton
                    // Zone vor Zeit erledigt setZoneAndTime (E-4).
                    viewModel.applyDateTime(composeEpoch(date, time, zone), zone.id)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (selectedDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text(okLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(cancelLabel)
                }
            },
        ) {
            HideSystemBarsInDialog()
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initial = selectedTime ?: LocalTime.now()
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text(okLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(cancelLabel)
                }
            },
            text = {
                HideSystemBarsInDialog()
                TimePicker(state = timePickerState)
            },
        )
    }

    // Nachtrag 2 Punkt 5: Angebot, die Zeitautomatik abzuschalten — mit Erklaerung,
    // was das bedeutet, und nur auf ausdrueckliche Bestaetigung.
    if (showAutoDialog) {
        AlertDialog(
            onDismissRequest = { showAutoDialog = false },
            title = { Text(autoTitle) },
            text = { Text(autoDesc) },
            confirmButton = {
                TextButton(onClick = {
                    showAutoDialog = false
                    val date = selectedDate
                    val time = selectedTime
                    val zone = selectedZone
                    if (date != null && time != null && zone != null) {
                        viewModel.disableAutoTimeAndRetry(composeEpoch(date, time, zone), zone.id)
                    } else {
                        // N-2 (B-6): Heute unerreichbar (siehe Pruefbericht), aber kein stummer
                        // Zweig — ein Bedienelement, das gedrueckt aussieht und schweigt, ist
                        // genau die Fehlerklasse, die diese Welle ausgeloest hat.
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(autoIncompleteMsg, duration = SnackbarDuration.Long)
                        }
                    }
                }) {
                    Text(autoConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDialog = false }) {
                    Text(cancelLabel)
                }
            },
        )
    }
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Zeile einer Auswahlkarte: Titel links, Wert rechts (Hint in Sekundaerfarbe). */
@Composable
private fun PickRow(title: String, value: String, valueIsHint: Boolean) {
    val c = DrainQTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (valueIsHint) c.textSecondary else c.textPrimary,
        )
        Spacer(Modifier.width(Dimensions.Space8))
        DqIcon("chevron_right", tint = c.textSecondary)
    }
}

/** Zeile der Zonenliste: Name + Versatz, Haekchen an der gewaehlten. */
@Composable
private fun ZoneRow(zoneId: String, selected: Boolean, onClick: () -> Unit) {
    val c = DrainQTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimensions.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(zoneId, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(
                zoneOffsetLabel(ZoneId.of(zoneId), System.currentTimeMillis()),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = c.amber,
            )
        }
    }
}

/** Kopfzeile mit Zurueck (Muster NetworkTopBar) + Hilfe + Tastatur-Ausblenden. */
@Composable
private fun DateTimeTopBar(title: String, onBack: () -> Unit) {
    val c = DrainQTheme.colors
    Surface(color = c.bgPanel, modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.HeaderHeight)
                    .padding(horizontal = Dimensions.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    DqIcon("back", size = Dimensions.DqIconToolbar, tint = c.textPrimary)
                }
                Spacer(Modifier.width(Dimensions.Space8))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                HelpButton(route = "datetime")
                KeyboardHideButton()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.borderSubtle)
                    .align(Alignment.BottomStart),
            )
        }
    }
}
