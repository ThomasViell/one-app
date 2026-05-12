package com.uip.oneapp.update

interface UpdateService {
    suspend fun checkForUpdate(): UpdateCheckResult
    suspend fun downloadAndInstall(release: ReleaseInfo)
}
