package com.uip.oneapp.ui.screens.projectdetail

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.export.ProjectExportService
import com.uip.oneapp.ui.screens.settings.settingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ProjectDetailVM"

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val damageRepository: DamageRepository,
    private val noteRepository: NoteRepository,
    private val exportService: ProjectExportService,
    application: Application
) : AndroidViewModel(application) {

    private val _projectId = MutableStateFlow<Long?>(null)

    val project: StateFlow<ProjectEntity?> = _projectId.flatMapLatest { id ->
        if (id != null) projectRepository.getProjectFlow(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val damages: StateFlow<List<DamageEntity>> = _projectId.flatMapLatest { id ->
        if (id != null) damageRepository.getDamagesForProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = _projectId.flatMapLatest { id ->
        if (id != null) noteRepository.getNotesForProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recordingFiles = MutableStateFlow<List<File>>(emptyList())
    val recordingFiles: StateFlow<List<File>> = _recordingFiles.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun loadProject(projectId: Long) {
        _projectId.value = projectId
        scanRecordingFiles(projectId)
    }

    private fun scanRecordingFiles(projectId: Long) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val dir = File(ctx.getExternalFilesDir("recordings"), "project_$projectId")
            val files = if (dir.exists()) {
                dir.listFiles()?.filter { it.isFile && it.length() > 0 }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else emptyList()
            _recordingFiles.value = files
            Log.d(TAG, "Found ${files.size} recordings for project $projectId")
        }
    }

    private suspend fun getDamagesNewestFirst(): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val prefs = ctx.settingsStore.data.first()
            prefs[booleanPreferencesKey("damages_newest_first")] ?: true
        } catch (_: Exception) { true }
    }

    fun exportPdf(includePhotos: Boolean = true, includeXml: Boolean = false, includeMap: Boolean = false) {
        val proj = project.value ?: return
        val dmgs = damages.value
        val nts = notes.value
        viewModelScope.launch {
            _exportProgress.value = 0f
            try {
                // Use persisted sort order from InspectionScreen
                val newestFirst = getDamagesNewestFirst()
                val sortedDmgs = if (newestFirst) dmgs else dmgs.reversed()
                val file = exportService.generatePdf(proj, sortedDmgs, nts, includePhotos, reversed = false, includeMap = includeMap)
                // Generate XML alongside PDF if requested
                if (includeXml) {
                    exportService.generateXmlExport(proj, sortedDmgs, nts)
                }
                _exportProgress.value = null
                _exportResult.value = ExportResult(file, ExportType.PDF)
            } catch (e: Exception) {
                Log.e(TAG, "PDF export failed", e)
                _exportProgress.value = null
                _exportResult.value = ExportResult(null, ExportType.PDF, e.message)
            }
        }
    }

    fun exportZip(includePhotos: Boolean = true, includeXml: Boolean = true, includeMap: Boolean = false) {
        val proj = project.value ?: return
        val dmgs = damages.value
        val nts = notes.value
        viewModelScope.launch {
            _exportProgress.value = 0f
            try {
                // Use persisted sort order from InspectionScreen
                val newestFirst = getDamagesNewestFirst()
                val sortedDmgs = if (newestFirst) dmgs else dmgs.reversed()
                val file = exportService.generateZipWithXml(
                    proj, sortedDmgs, nts, includePhotos, includeXml, reversed = false, includeMap = includeMap
                ) { progress ->
                    _exportProgress.value = progress
                }
                _exportProgress.value = null
                _exportResult.value = ExportResult(file, ExportType.ZIP)
            } catch (e: Exception) {
                Log.e(TAG, "ZIP export failed", e)
                _exportProgress.value = null
                _exportResult.value = ExportResult(null, ExportType.ZIP, e.message)
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun deleteDamage(damage: DamageEntity) {
        viewModelScope.launch {
            // Delete associated photo files
            if (damage.photoPath.isNotEmpty()) {
                File(damage.photoPath).let { if (it.exists()) it.delete() }
            }
            if (damage.annotatedPhotoPath.isNotEmpty()) {
                File(damage.annotatedPhotoPath).let { if (it.exists()) it.delete() }
            }
            damageRepository.deleteDamage(damage)
        }
    }

    fun updateDamage(damage: DamageEntity) {
        viewModelScope.launch {
            damageRepository.updateDamage(damage)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            // Delete associated audio file
            if (note.audioPath.isNotEmpty()) {
                File(note.audioPath).let { if (it.exists()) it.delete() }
            }
            noteRepository.deleteNote(note)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.updateNote(note)
        }
    }

    fun deleteRecording(file: File) {
        viewModelScope.launch {
            if (file.exists()) file.delete()
            _projectId.value?.let { scanRecordingFiles(it) }
        }
    }
}

data class ExportResult(
    val file: File?,
    val type: ExportType,
    val error: String? = null
)

enum class ExportType { PDF, ZIP }
