package com.noloxtreme.tts.reader.ui.translation

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noloxtreme.tts.reader.R
import com.noloxtreme.tts.reader.domain.DocumentId
import kotlin.math.roundToInt

/** A device-checked, explicit download and translation flow for a whole document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(
    state: TranslationUiState,
    onSourceChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onTranslate: (allowMobileData: Boolean) -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
    onOpenDocument: (DocumentId) -> Unit,
    onDismiss: () -> Unit
) {
    var allowMobileData by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Outlined.Translate,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(24.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.translation_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.translation_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.translation_close))
                    }
                }

                when {
                    state.checking -> TranslationChecking()
                    state.unavailable != null -> TranslationUnavailable(
                        reason = state.unavailable,
                        onRefresh = onRefresh,
                        onOpenSpeechSettings = onOpenSpeechSettings
                    )
                    state.phase == TranslationPhase.SUCCESS -> TranslationSuccess(
                        onOpen = { state.translatedDocumentId?.let(onOpenDocument) },
                        canOpen = state.translatedDocumentId != null
                    )
                    state.busy -> TranslationProgress(state, onCancel)
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TranslationLanguagePicker(
                                label = stringResource(R.string.translation_from),
                                languages = state.languages,
                                selectedCode = state.sourceCode,
                                onSelect = onSourceChange
                            )
                            TranslationLanguagePicker(
                                label = stringResource(R.string.translation_to),
                                languages = state.languages.filter { it.code != state.sourceCode },
                                selectedCode = state.targetCode,
                                onSelect = onTargetChange
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    stringResource(R.string.translation_voice_assurance),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onOpenSpeechSettings) {
                                Text(stringResource(R.string.translation_add_voice_languages))
                            }
                        }

                        if (state.checkingModels) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.translation_checking_packs),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (state.modelsNeeded && state.sourceCode != null && state.targetCode != null) {
                            TranslationDownloadOptions(
                                allowMobileData = allowMobileData,
                                onAllowMobileDataChange = { allowMobileData = it }
                            )
                        } else if (state.sourceCode != null && state.targetCode != null) {
                            Text(
                                stringResource(R.string.translation_packs_ready),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Text(
                            stringResource(R.string.translation_copy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!state.checking && state.unavailable == null && !state.busy && state.phase != TranslationPhase.SUCCESS) {
                    state.error?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                stringResource(translationErrorMessage(error)),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp).semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { onTranslate(allowMobileData) },
                        enabled = state.canTranslate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.Translate, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.translation_action))
                    }
                }
                TranslationAttribution(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TranslationChecking() {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
        Text(
            stringResource(R.string.translation_checking_device),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun TranslationUnavailable(
    reason: TranslationUnavailableReason,
    onRefresh: () -> Unit,
    onOpenSpeechSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.translation_unavailable_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(translationUnavailableMessage(reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (reason != TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) {
            OutlinedButton(onClick = onOpenSpeechSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.translation_voice_settings))
            }
        }
        TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.translation_check_again))
        }
    }
}

@Composable
private fun TranslationSuccess(onOpen: () -> Unit, canOpen: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(32.dp))
                Text(
                    stringResource(R.string.translation_ready_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Text(stringResource(R.string.translation_ready_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = onOpen, enabled = canOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.translation_open_copy))
        }
    }
}

@Composable
private fun TranslationProgress(state: TranslationUiState, onCancel: () -> Unit) {
    val phaseLabel = when (state.phase) {
        TranslationPhase.DOWNLOADING -> R.string.translation_downloading
        TranslationPhase.SAVING -> R.string.translation_saving
        else -> R.string.translation_translating
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(phaseLabel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (state.phase == TranslationPhase.TRANSLATING) {
                LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.translation_percent, (state.progress.coerceIn(0f, 1f) * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(
                    if (state.phase == TranslationPhase.DOWNLOADING) R.string.translation_download_progress_hint
                    else R.string.translation_progress_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.translation_cancel))
            }
        }
    }
}

@Composable
private fun TranslationDownloadOptions(allowMobileData: Boolean, onAllowMobileDataChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.translation_pack_title), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.translation_pack_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = allowMobileData,
                    role = Role.Switch,
                    onValueChange = onAllowMobileDataChange
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.translation_mobile_data),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = allowMobileData, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun TranslationLanguagePicker(
    label: String,
    languages: List<TranslationLanguage>,
    selectedCode: String?,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = languages.firstOrNull { it.code == selectedCode }
    OutlinedCard(
        onClick = { expanded = true },
        enabled = languages.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    selected?.displayName ?: stringResource(R.string.translation_choose_language),
                    style = MaterialTheme.typography.titleMedium
                )
                selected?.nativeName?.takeIf { it != selected.displayName }?.let { nativeName ->
                    Text(nativeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(label) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(languages, key = { it.code }) { language ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = language.code == selectedCode,
                                role = Role.RadioButton,
                                onClick = {
                                    expanded = false
                                    onSelect(language.code)
                                }
                            ).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = language.code == selectedCode, onClick = null)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (language.nativeName != language.displayName) {
                                    Text(language.nativeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.translation_close)) }
            }
        )
    }
}

/** Approved, unmodified Google attribution, also shown beside a saved translated document. */
@Composable
fun TranslationAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var infoVisible by rememberSaveable { mutableStateOf(false) }
    val badge = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        R.drawable.google_translate_attribution_dark
    } else {
        R.drawable.google_translate_attribution
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp)
                .clickable(role = Role.Button) { runCatching { uriHandler.openUri("https://translate.google.com") } }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(badge),
                contentDescription = stringResource(R.string.translation_attribution),
                modifier = Modifier.width(176.dp).height(16.dp)
            )
        }
        IconButton(onClick = { infoVisible = true }) {
            Icon(Icons.Outlined.Info, stringResource(R.string.translation_information), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (infoVisible) {
        AlertDialog(
            onDismissRequest = { infoVisible = false },
            title = { Text(stringResource(R.string.translation_information)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.translation_information_body))
                    Text(stringResource(R.string.translation_disclaimer), fontWeight = FontWeight.Medium)
                    TextButton(onClick = {
                        runCatching { uriHandler.openUri("https://docs.cloud.google.com/translate/attribution#disclaimer") }
                    }) { Text(stringResource(R.string.translation_provider_terms)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoVisible = false }) { Text(stringResource(R.string.translation_close)) }
            }
        )
    }
}

@StringRes
fun translationUnavailableMessage(reason: TranslationUnavailableReason): Int = when (reason) {
    TranslationUnavailableReason.ENGINE_UNAVAILABLE -> R.string.translation_unavailable_engine
    TranslationUnavailableReason.NO_SUPPORTED_VOICES -> R.string.translation_unavailable_voices
    TranslationUnavailableReason.NEED_ANOTHER_VOICE -> R.string.translation_unavailable_another_voice
    TranslationUnavailableReason.TRANSLATION_UNAVAILABLE -> R.string.translation_unavailable_device
}

@StringRes
private fun translationErrorMessage(error: TranslationError): Int = when (error) {
    TranslationError.VOICE_UNAVAILABLE -> R.string.translation_error_voice
    TranslationError.LANGUAGE_UNAVAILABLE -> R.string.translation_error_language
    TranslationError.NETWORK_REQUIRED -> R.string.translation_error_network
    TranslationError.WIFI_REQUIRED -> R.string.translation_error_wifi
    TranslationError.STORAGE_LOW -> R.string.translation_error_storage
    TranslationError.DOWNLOAD_FAILED -> R.string.translation_error_download
    TranslationError.TRANSLATION_FAILED -> R.string.translation_error_translation
    TranslationError.SAVE_FAILED -> R.string.translation_error_save
    TranslationError.DOCUMENT_MISSING -> R.string.translation_error_missing
}
