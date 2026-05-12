package com.uip.oneapp.update

import com.google.gson.annotations.SerializedName

data class ReleaseManifest(
    @SerializedName("channel")  val channel: String,
    @SerializedName("latest")   val latest: ReleaseInfo,
    @SerializedName("history")  val history: List<ReleaseHistoryEntry> = emptyList()
)

data class ReleaseInfo(
    @SerializedName("version")     val version: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("minSdk")      val minSdk: Int,
    @SerializedName("url")         val url: String,
    @SerializedName("sha256")      val sha256: String,
    @SerializedName("size")        val size: Long,
    @SerializedName("releasedAt")  val releasedAt: String,
    @SerializedName("notes")       val notes: String,
    @SerializedName("mandatory")   val mandatory: Boolean = false
)

data class ReleaseHistoryEntry(
    @SerializedName("version")     val version: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("releasedAt")  val releasedAt: String
)

sealed class UpdateCheckResult {
    object NoUpdate : UpdateCheckResult()
    data class Available(val release: ReleaseInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
    object NotConfigured : UpdateCheckResult()
}
