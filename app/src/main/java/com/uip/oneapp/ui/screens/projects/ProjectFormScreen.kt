package com.uip.oneapp.ui.screens.projects

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.uip.oneapp.maps.OfflineMapManager
import com.uip.oneapp.maps.OfflineMapRenderer
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import android.content.Context
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormScreen(
    navController: NavController,
    editProjectId: Long? = null,
    viewModel: ProjectFormViewModel = koinViewModel()
) {
    LaunchedEffect(editProjectId) {
        editProjectId?.let { viewModel.loadProject(it) }
    }

    var leitungstypExpanded by remember { mutableStateOf(false) }
    var materialExpanded by remember { mutableStateOf(false) }
    var kameratypExpanded by remember { mutableStateOf(false) }
    var wetterExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val weatherPresets by viewModel.weatherPresets.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val formView = LocalView.current
    // Tastatur hart über das System schließen (clearFocus reicht auf der ONE-HW nicht).
    val hideKeyboard: () -> Unit = {
        val imm = formView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(formView.windowToken, 0)
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    // Wischen/Scrollen des Formulars (Feld „runtersliden") schließt die Tastatur.
    val dismissKeyboardOnScroll = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            pendingLocationAction?.invoke()
            pendingLocationAction = null
        }
    }

    val weatherErrorText = S("weather_fetch_error")
    val locationErrorText = S("location_disabled")
    LaunchedEffect(viewModel.weatherError) {
        viewModel.weatherError?.let { error ->
            val msg = if (error.contains("location", ignoreCase = true)) {
                locationErrorText
            } else {
                weatherErrorText
            }
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setFilesDir(context.filesDir)
    }

    val locationFetchError = S("location_disabled")
    val addressNotFoundMsg = S("address_not_found")
    val addressNoInternetMsg = S("address_search_no_internet")
    LaunchedEffect(viewModel.locationError) {
        viewModel.locationError?.let { code ->
            val msg = when (code) {
                "GPS_OK_NO_INTERNET" ->
                    "GPS-Position übernommen — Adresse/Karte ohne Internet nicht abrufbar."
                "LOCATION_FAILED" -> locationFetchError
                "ADDRESS_NOT_FOUND" -> addressNotFoundMsg
                "ADDRESS_SEARCH_NO_INTERNET" -> addressNoInternetMsg
                else -> code
            }
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearLocationError()
        }
    }

    val offlineMapManager: OfflineMapManager = koinInject()
    val offlineMapRenderer: OfflineMapRenderer = koinInject()

    val leitungstypen = listOf(S("pipe_type_sewer"), S("pipe_type_wastewater"), S("pipe_type_drainage"), S("pipe_type_other"))
    val materialien = listOf(S("material_pvc"), S("material_concrete"), S("material_stoneware"), S("material_cast_iron"), S("material_unknown"))
    val kameratypen = listOf(S("camera_c10"), S("camera_c13"))

    LaunchedEffect(viewModel.savedProjectId) {
        viewModel.savedProjectId?.let {
            navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) S("edit_project") else S("new_project_title")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = S("back"),
                            modifier = Modifier.size(Dimensions.NavRailIconSize))
                    }
                },
                actions = {
                    // Immer sichtbar: Tastatur einklappen (app-weite Regel, SA-Komponente)
                    com.uip.oneapp.ui.components.KeyboardHideButton(onHide = hideKeyboard)
                    TextButton(
                        onClick = { viewModel.saveProject() },
                        enabled = !viewModel.isSaving
                    ) {
                        if (viewModel.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.IconSizeMedium),
                                strokeWidth = Dimensions.StrokeWidthMedium
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                        Text(S("save"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                // Tipp auf freie Fläche schließt die Tastatur (Feedback #4).
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                // Wischen/Scrollen schließt die Tastatur (Feld „runtersliden").
                .nestedScroll(dismissKeyboardOnScroll)
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.PanelEdgePadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.PanelEdgePadding)
        ) {
            // === SECTION 1: Allgemeine Angaben ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                        Text(
                            S("general_info"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                    OutlinedTextField(
                        value = viewModel.auftraggeber,
                        onValueChange = { viewModel.auftraggeber = it },
                        label = { Text(S("field_project_client")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    val keyboardController = LocalSoftwareKeyboardController.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.standortAdresse,
                            onValueChange = { viewModel.standortAdresse = it },
                            label = { Text(S("field_location_address")) },
                            modifier = Modifier.weight(1f).heightIn(min = Dimensions.InputHeight),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                viewModel.searchAddress()
                            }),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.searchAddress()
                                    },
                                    enabled = !viewModel.isSearchingAddress &&
                                        viewModel.standortAdresse.isNotBlank()
                                ) {
                                    if (viewModel.isSearchingAddress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimensions.IconSizeLarge),
                                            strokeWidth = Dimensions.StrokeWidthMedium
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = S("search_address")
                                        )
                                    }
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                if (!hasLocationPermission) {
                                    pendingLocationAction = { viewModel.fetchLocationAndMap() }
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    viewModel.fetchLocationAndMap()
                                }
                            },
                            enabled = !viewModel.isLoadingLocation
                        ) {
                            if (viewModel.isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.LargeSpacing),
                                    strokeWidth = Dimensions.StrokeWidthMedium
                                )
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = S("get_gps_location"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.openMapPicker() }) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = S("pick_on_map"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    viewModel.mapImagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                            AsyncImage(
                                model = file,
                                contentDescription = S("map_preview"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimensions.MapPreviewHeight)
                                    .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { viewModel.openMapPicker() }
                                        )
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (viewModel.showMapPicker) {
                        MapPickerDialog(
                            initialLat = viewModel.capturedLat,
                            initialLon = viewModel.capturedLon,
                            offlineManager = offlineMapManager,
                            offlineRenderer = offlineMapRenderer,
                            onDismiss = { viewModel.closeMapPicker() },
                            onConfirm = { lat, lon -> viewModel.applyPickedLocation(lat, lon) }
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    OutlinedTextField(
                        value = viewModel.inspektionsdatum,
                        onValueChange = { input ->
                            viewModel.inspektionsdatum = input
                        },
                        label = { Text(S("field_inspection_date")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null,
                                    modifier = Modifier.size(Dimensions.NavRailIconSize))
                            }
                        }
                    )

                    if (showDatePicker) {
                        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        val initialDate = try {
                            LocalDate.parse(viewModel.inspektionsdatum, formatter)
                        } catch (_: Exception) {
                            LocalDate.now()
                        }
                        val initialMillis = initialDate
                            .atStartOfDay(ZoneId.of("UTC"))
                            .toInstant()
                            .toEpochMilli()
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = initialMillis
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val selected = Instant.ofEpochMilli(millis)
                                            .atZone(ZoneId.of("UTC"))
                                            .toLocalDate()
                                        viewModel.inspektionsdatum = selected.format(formatter)
                                    }
                                    showDatePicker = false
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text(S("cancel"))
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    OutlinedTextField(
                        value = viewModel.inspektor,
                        onValueChange = { viewModel.inspektor = it },
                        label = { Text(S("field_inspector")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = wetterExpanded,
                            onExpandedChange = { wetterExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = viewModel.wetter,
                                onValueChange = {
                                    viewModel.wetter = it
                                    wetterExpanded = true
                                },
                                label = { Text(S("field_weather")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wetterExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Dimensions.InputHeight)
                                    .menuAnchor(),
                                textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                                singleLine = true
                            )
                            val filtered = weatherPresets.filter {
                                viewModel.wetter.isEmpty() || it.contains(viewModel.wetter, ignoreCase = true)
                            }
                            if (filtered.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = wetterExpanded,
                                    onDismissRequest = { wetterExpanded = false }
                                ) {
                                    filtered.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                viewModel.wetter = item
                                                wetterExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                        IconButton(
                            onClick = {
                                if (!hasLocationPermission) {
                                    pendingLocationAction = { viewModel.fetchWeatherFromGps() }
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    viewModel.fetchWeatherFromGps()
                                }
                            },
                            enabled = !viewModel.isFetchingWeather
                        ) {
                            if (viewModel.isFetchingWeather) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.LargeSpacing),
                                    strokeWidth = Dimensions.StrokeWidthMedium
                                )
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = S("fetch_weather_gps"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // === SECTION 2: Leitungsdaten ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                        Text(
                            S("pipe_data"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                    ExposedDropdownMenuBox(
                        expanded = leitungstypExpanded,
                        onExpandedChange = { leitungstypExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = viewModel.leitungstyp,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S("field_pipe_type")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leitungstypExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.InputHeight)
                                .menuAnchor(),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                        )
                        ExposedDropdownMenu(
                            expanded = leitungstypExpanded,
                            onDismissRequest = { leitungstypExpanded = false }
                        ) {
                            leitungstypen.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.leitungstyp = item
                                        leitungstypExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    ExposedDropdownMenuBox(
                        expanded = materialExpanded,
                        onExpandedChange = { materialExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = viewModel.material,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S("field_material")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = materialExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.InputHeight)
                                .menuAnchor(),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                        )
                        ExposedDropdownMenu(
                            expanded = materialExpanded,
                            onDismissRequest = { materialExpanded = false }
                        ) {
                            materialien.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.material = item
                                        materialExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    OutlinedTextField(
                        value = viewModel.durchmesser,
                        onValueChange = { viewModel.durchmesser = it },
                        label = { Text(S("field_diameter")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    OutlinedTextField(
                        value = viewModel.inspektionslaenge,
                        onValueChange = { viewModel.inspektionslaenge = it },
                        label = { Text(S("field_inspection_length")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        trailingIcon = {
                            Icon(Icons.Default.Straighten, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
                    ) {
                        OutlinedTextField(
                            value = viewModel.startpunkt,
                            onValueChange = { viewModel.startpunkt = it },
                            label = { Text(S("field_start_point")) },
                            modifier = Modifier.weight(1f).heightIn(min = Dimensions.InputHeight),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        OutlinedTextField(
                            value = viewModel.endpunkt,
                            onValueChange = { viewModel.endpunkt = it },
                            label = { Text(S("field_end_point")) },
                            modifier = Modifier.weight(1f).heightIn(min = Dimensions.InputHeight),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, hintLocales = com.uip.oneapp.ui.components.appHintLocales()),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                }
            }

            // === SECTION 3: Inspektionsmethode ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                        Text(
                            S("inspection_method"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                    OutlinedTextField(
                        value = S("inspection_system_value"),
                        onValueChange = {},
                        label = { Text(S("field_inspection_system")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.InputHeight),
                        textStyle = TextStyle(fontSize = Dimensions.InputFontSize),
                        singleLine = true,
                        readOnly = true
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    ExposedDropdownMenuBox(
                        expanded = kameratypExpanded,
                        onExpandedChange = { kameratypExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = viewModel.kameratyp,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S("field_camera_type")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kameratypExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.InputHeight)
                                .menuAnchor(),
                            textStyle = TextStyle(fontSize = Dimensions.InputFontSize)
                        )
                        ExposedDropdownMenu(
                            expanded = kameratypExpanded,
                            onDismissRequest = { kameratypExpanded = false }
                        ) {
                            kameratypen.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.kameratyp = item
                                        kameratypExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Inspection form checkboxes removed per user request
                }
            }

            // === SECTION 4: Video-Einstellungen (nach Anlage nicht änderbar) ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (viewModel.isEditing) 0.6f else 1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Dimensions.PanelEdgePadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimensions.TouchSpacing))
                        Text(
                            S("video_settings"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (viewModel.isEditing) {
                        Spacer(modifier = Modifier.height(Dimensions.SmallSpacing))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSizeXSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                            Text(
                                S("setting_locked"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))

                    Text(
                        S("video_quality"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.TouchSpacing)
                    ) {
                        val qualities = listOf("SD" to S("video_quality_sd"), "HD" to S("video_quality_hd"))
                        qualities.forEach { (value, label) ->
                            OutlinedButton(
                                onClick = { if (!viewModel.isEditing) viewModel.videoQuality = value },
                                modifier = Modifier.weight(1f).height(Dimensions.TouchMedium),
                                enabled = !viewModel.isEditing,
                                shape = RoundedCornerShape(Dimensions.OverlayCornerRadius),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (viewModel.videoQuality == value)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(
                                    width = if (viewModel.videoQuality == value) Dimensions.StrokeWidthMedium else Dimensions.BorderWidthDefault,
                                    color = if (viewModel.videoQuality == value)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Icon(
                                    if (value == "HD") Icons.Default.HighQuality else Icons.Default.SdCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.IconSizeLarge)
                                )
                                Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                                Text(label)
                            }
                        }
                    }

                    // Video overlay toggle removed per user request
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.PanelEdgePadding))
        }
    }
}
