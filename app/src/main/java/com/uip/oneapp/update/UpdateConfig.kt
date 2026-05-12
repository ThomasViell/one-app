package com.uip.oneapp.update

import android.content.Context
import com.uip.oneapp.BuildConfig

open class UpdateConfig(private val context: Context?) {

    private val prefs by lazy {
        context!!.getSharedPreferences("update", Context.MODE_PRIVATE)
    }

    open val mode: String
        get() = prefs.getString("mode", BuildConfig.UPDATE_MODE) ?: BuildConfig.UPDATE_MODE

    open val proxyUrl: String
        get() {
            val override = prefs.getString("proxyUrl", null)
            return if (!override.isNullOrBlank()) override else BuildConfig.UPDATE_PROXY_URL
        }

    open val channel: String
        get() = prefs.getString("channel", BuildConfig.UPDATE_CHANNEL) ?: BuildConfig.UPDATE_CHANNEL

    open val manifestUrl: String
        get() = "${proxyUrl}releases.$channel.json"

    fun overrideChannel(channel: String) {
        prefs.edit().putString("channel", channel).apply()
    }

    fun overrideProxyUrl(url: String) {
        prefs.edit().putString("proxyUrl", url).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
