package com.uip.oneapp.ui.screens.offlinemaps

import android.app.Application
import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URL

class OfflineMapsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = OfflineMapManager(application)
    private val workManager = WorkManager.getInstance(application)

    /**
     * State of the HEAD probe that runs before each download starts.
     * The picker keeps the user on this state until they confirm or cancel,
     * so we never enqueue a 3 GB download by accident.
     */
    sealed class Verify {
        abstract val entry: OfflineMapCatalog.Entry
        data class Probing(override val entry: OfflineMapCatalog.Entry) : Verify()
        data class Ready  (override val entry: OfflineMapCatalog.Entry, val realBytes: Long) : Verify()
        data class Failed (override val entry: OfflineMapCatalog.Entry, val message: String) : Verify()
    }

    data class UiState(
        val installed: List<OfflineMapManager.InstalledMap> = emptyList(),
        val totalSizeBytes: Long = 0L,
        val pickerOpen: Boolean = false,
        val verify: Verify? = null
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

    /**
     * Verify the actual Content-Length from download.mapsforge.org before
     * starting the download. The screen blocks the download button while
     * this runs and shows a confirmation dialog when Ready.
     */
    fun requestSizeCheck(entry: OfflineMapCatalog.Entry) {
        _state.value = _state.value.copy(verify = Verify.Probing(entry))
        viewModelScope.launch(Dispatchers.IO) {
            val result = headContentLength(OfflineMapCatalog.url(entry))
            val next = result.fold(
                onSuccess = { bytes -> Verify.Ready(entry, bytes) },
                onFailure = { e -> Verify.Failed(entry, e.message ?: "unbekannter Fehler") }
            )
            _state.value = _state.value.copy(verify = next)
        }
    }

    /** Called from the confirmation dialog after the user has seen the real size. */
    fun confirmVerifiedDownload() {
        val v = _state.value.verify as? Verify.Ready ?: return
        val dest = manager.fileFor(v.entry)
        OfflineMapDownloadWorker.enqueue(getApplication(), v.entry, dest)
        _state.value = _state.value.copy(verify = null, pickerOpen = false)
    }

    fun dismissVerify() { _state.value = _state.value.copy(verify = null) }

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

    private fun headContentLength(url: String): Result<Long> = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DrainQ ONE/1.0 (offline-map-size-probe)")
        }
        val rc = conn.responseCode
        val len = conn.contentLengthLong
        conn.disconnect()
        when {
            rc !in 200..299 -> Result.failure(Exception("HTTP $rc"))
            len <= 0L       -> Result.failure(Exception("kein Content-Length im Header"))
            else            -> Result.success(len)
        }
    } catch (e: Exception) {
        Log.w("OfflineMapsVM", "HEAD failed for $url: ${e.message}")
        Result.failure(e)
    }
}
