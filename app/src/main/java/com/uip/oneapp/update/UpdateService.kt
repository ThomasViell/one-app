package com.uip.oneapp.update

import kotlinx.coroutines.flow.Flow

interface UpdateService {
    suspend fun checkForUpdate(): UpdateCheckResult
    suspend fun downloadAndInstall(release: ReleaseInfo)
    fun getUpdateEvents(): Flow<List<UpdateEvent>>
}

data class UpdateEvent(
    val timestamp: Long,
    val type: UpdateEventType,
    val message: String = ""
)

enum class UpdateEventType {
    CHECK,
    DOWNLOAD_START,
    DOWNLOAD_PROGRESS,
    DOWNLOAD_OK,
    DOWNLOAD_FAIL,
    INSTALL_INITIATED,
    INSTALL_DONE
}
