package com.uip.oneapp.ui.screens.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.ui.localization.S
import com.uip.oneapp.ui.theme.Dimensions
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageDialog(
    photoPath: String,
    annotatedPhotoPath: String = "",
    currentMeter: Float,
    projectId: Long,
    onSave: (DamageEntity) -> Unit,
    onDismiss: () -> Unit,
    onOpenAnnotation: (String) -> Unit = {},
    existingDamage: DamageEntity? = null
) {
    val isEditing = existingDamage != null

    val damagePresetRepository: DamagePresetRepository = koinInject()
    val damageTypes by damagePresetRepository.presets.collectAsState()

    val defaultType = damageTypes.firstOrNull() ?: ""

    var selectedType by remember { mutableStateOf(existingDamage?.damageType ?: defaultType) }
    var description by remember { mutableStateOf(existingDamage?.description ?: "") }
    var meterText by remember { mutableStateOf(String.format("%.2f", existingDamage?.position ?: currentMeter)) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val dlgView = LocalView.current
    val hideKeyboard: () -> Unit = {
        val imm = dlgView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(dlgView.windowToken, 0)
        focusManager.clearFocus()
    }

    val hasOriginal = photoPath.isNotEmpty() &&
            File(photoPath).exists() && File(photoPath).length() > 0
    val hasAnnotated = annotatedPhotoPath.isNotEmpty() &&
            File(annotatedPhotoPath).exists() && File(annotatedPhotoPath).length() > 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(Dimensions.DialogCornerRadius)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                            Text(if (isEditing) S("edit_damage") else S("record_damage"))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S("close"),
                                modifier = Modifier.size(Dimensions.DialogCloseIconSize)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { hideKeyboard() }) {
                            Icon(Icons.Default.KeyboardHide, contentDescription = S("hide_keyboard"),
                                modifier = Modifier.size(Dimensions.NavRailIconSize))
                        }
                        TextButton(
                            onClick = {
                                val meter = meterText.replace(",", ".").toFloatOrNull() ?: currentMeter
                                val hasPhoto = photoPath.isNotEmpty() && File(photoPath).exists() && File(photoPath).length() > 0
                                if (selectedType !in damageTypes && description.isBlank() && !hasPhoto) return@TextButton
                                onSave(
                                    DamageEntity(
                                        id = existingDamage?.id ?: 0,
                                        projectId = projectId,
                                        position = meter,
                                        damageType = selectedType,
                                        description = description,
                                        photoPath = photoPath,
                                        annotatedPhotoPath = annotatedPhotoPath,
                                        createdAt = existingDamage?.createdAt ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimensions.SmallSpacing))
                            Text(S("save"))
                        }
                    }
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Tipp auf freie Fläche schließt die Tastatur (Feedback #4).
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(Dimensions.PanelEdgePadding),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.PanelEdgePadding)
                ) {
                    // Photo preview section
                    if (hasOriginal || hasAnnotated) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing)
                        ) {
                            // Original photo
                            if (hasOriginal) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Dimensions.DialogContentMinHeight)
                                        .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                        .border(Dimensions.BorderWidthDefault, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = { onOpenAnnotation(photoPath) }
                                            )
                                        }
                                ) {
                                    AsyncImage(
                                        model = File(photoPath),
                                        contentDescription = S("photo"),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Label
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(Dimensions.SmallSpacing)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
                                            )
                                            .padding(horizontal = Dimensions.MediumSpacing, vertical = Dimensions.SmallItemSpacing)
                                    ) {
                                        Text(
                                            text = S("original"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    // Edit hint
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(Dimensions.SmallSpacing)
                                    ) {
                                        Text(
                                            text = S("double_tap_to_edit"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Annotated photo
                            if (hasAnnotated) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Dimensions.DialogContentMinHeight)
                                        .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                        .border(Dimensions.BorderWidthDefault, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                ) {
                                    AsyncImage(
                                        model = File(annotatedPhotoPath),
                                        contentDescription = S("annotated"),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Label
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(Dimensions.SmallSpacing)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                RoundedCornerShape(Dimensions.ThumbnailCornerRadius)
                                            )
                                            .padding(horizontal = Dimensions.MediumSpacing, vertical = Dimensions.SmallItemSpacing)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.IconSizeXXSmall),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(Dimensions.SmallItemSpacing))
                                            Text(
                                                text = S("annotated"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimensions.MultilineInputHeight)
                                .clip(RoundedCornerShape(Dimensions.OverlayCornerRadius))
                                .border(Dimensions.BorderWidthDefault, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimensions.OverlayCornerRadius)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                S("no_screenshot"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Meter position
                    OutlinedTextField(
                        value = meterText,
                        onValueChange = { meterText = it },
                        label = { Text(S("field_position")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    // Damage type dropdown
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(S("field_damage_type")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            damageTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(S("field_description_optional")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.MultilineInputHeight),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    // Bottom save button (accessible when keyboard is shown)
                    Button(
                        onClick = {
                            val meter = meterText.replace(",", ".").toFloatOrNull() ?: currentMeter
                            val hasPhotoForSave = photoPath.isNotEmpty() && File(photoPath).exists() && File(photoPath).length() > 0
                            if (selectedType !in damageTypes && description.isBlank() && !hasPhotoForSave) return@Button
                            onSave(
                                DamageEntity(
                                    id = existingDamage?.id ?: 0,
                                    projectId = projectId,
                                    position = meter,
                                    damageType = selectedType,
                                    description = description,
                                    photoPath = photoPath,
                                    annotatedPhotoPath = annotatedPhotoPath,
                                    createdAt = existingDamage?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.DialogButtonHeight)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimensions.SectionSpacing))
                        Text(S("save"))
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SectionSpacing))
                }
            }
        }
    }
}
