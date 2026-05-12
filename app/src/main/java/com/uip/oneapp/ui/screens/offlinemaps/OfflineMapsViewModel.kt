package com.uip.oneapp.ui.screens.offlinemaps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uip.oneapp.maps.OfflineMapCatalog
import com.uip.oneapp.maps.OfflineMapDownloadWorker
import com.uip.oneapp.maps.OfflineMapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfflineMapsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = OfflineMapManager(application)
    private val workManager = WorkManager.getInstance(application)

    data class UiState(
        val installed: List<OfflineMapManager.InstalledMap> = emptyList(),
        val totalSizeBytes: Long = 0L,
        val pickerOpen: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = manager.listInstalled()
            _state.value = _state.value.copy(
                installed = installed,
                totalSizeBytes = installed.sumOf { it.sizeBytes }
            )
        }
    }

    fun openPicker()  { _state.value = _state.value.copy(pickerOpen = true) }
    fun closePicker() { _state.value = _state.value.copy(pickerOpen = false) }

    fun startDownload(entry: OfflineMapCatalog.Entry) {
        val dest = manager.fileFor(entry)
        OfflineMapDownloadWorker.enqueue(getApplication(), entry, dest)
        _state.value = _state.value.copy(pickerOpen = false)
    }

    fun cancelDownload(entry: OfflineMapCatalog.Entry) {
        OfflineMapDownloadWorker.cancel(getApplication(), entry)
    }

    fun delete(entry: OfflineMapCatalog.Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            manager.delete(entry)
            refresh()
        }
    }

    /** Per-entry live work info so the screen can show progress while a download runs. */
    fun workInfo(entry: OfflineMapCatalog.Entry): LiveData<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkLiveData(OfflineMapDownloadWorker.WORK_TAG_PREFIX + entry.id)

    fun catalog(): List<OfflineMapCatalog.Entry> = OfflineMapCatalog.all

    fun isInstalled(entry: OfflineMapCatalog.Entry): Boolean = manager.isInstalled(entry)
}
