package com.uip.oneapp.debugrig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.data.local.dao.DamageDao
import com.uip.oneapp.data.local.dao.NoteDao
import com.uip.oneapp.data.local.dao.ProjectDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * ADB-controllable BroadcastReceiver for the screenshot harness (W-H1).
 *
 * Registered ONLY in the debug source-set manifest — never present in release builds.
 * Additionally guarded by [DEBUG_ONLY] so that even an accidental registration in a
 * mis-configured build does nothing.
 *
 * Actions (prefix [ACTION_PREFIX]):
 *   DEMO_SEED           — wipes + re-inserts deterministic demo project
 *   NAVIGATE            — navigates NavGraph to --es route <route>
 *   SET_LOCALE          -- switches app language to --es lang de|en
 *   UI_STATE            — opens a named dialog/state via --es state <name>
 *
 * Each action broadcasts a local result Intent with the sticky extra "result" = "OK" or
 * "FAIL: <reason>" so capture.ps1 can poll for completion.
 */
class ScreenshotRigReceiver : BroadcastReceiver() {

    companion object {
        /** Always true — this entire class is debug-only by design. */
        const val DEBUG_ONLY = true

        const val ACTION_PREFIX = "com.uip.drainq.one.rig."

        /** Allowed NavGraph routes — must match NavGraph.kt composable declarations exactly. */
        val ALLOWED_ROUTES: Set<String> = setOf(
            "home",
            "connection",
            "projects",
            "inspection",
            "reports",
            "settings",
            "offline_maps",
            "network",
            "pairing",
            "cloud_login",
            "project_form",
            "inspection/{projectId}",
            "project_detail/{projectId}",
            "project_form/{projectId}"
        )

        /** Route templates that accept a Long parameter substitution. */
        private val PARAMETERISED_ROUTES = setOf(
            "inspection",
            "project_detail",
            "project_form"
        )

        fun isAllowedRoute(route: String): Boolean {
            if (route.isBlank()) return false
            if (route in ALLOWED_ROUTES) return true
            // Accept "inspection/123", "project_detail/456", "project_form/789"
            val prefix = route.substringBefore("/")
            val suffix = route.substringAfter("/", "")
            return prefix in PARAMETERISED_ROUTES && suffix.toLongOrNull() != null
        }

        private const val TAG = "ScreenshotRig"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) {
            Log.e(TAG, "RIG called in non-debug build — ignoring.")
            return
        }

        val action = intent.action ?: return
        Log.d(TAG, "onReceive: $action extras=${intent.extras?.keySet()?.joinToString()}")

        when {
            action == "${ACTION_PREFIX}DEMO_SEED" -> handleSeed(context, intent)
            action == "${ACTION_PREFIX}NAVIGATE" -> handleNavigate(context, intent)
            action == "${ACTION_PREFIX}SET_LOCALE" -> handleLocale(context, intent)
            action == "${ACTION_PREFIX}UI_STATE" -> handleUiState(context, intent)
            else -> {
                Log.w(TAG, "Unknown rig action: $action")
                setResultData("FAIL: unknown action $action")
            }
        }
    }

    private fun handleSeed(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val projectId = DemoDataSeeder.seed(
                    context,
                    koin.get<ProjectDao>(),
                    koin.get<DamageDao>(),
                    koin.get<NoteDao>()
                )
                ScreenshotRigBus.emitNavigate("home")
                pending.setResultData("OK:projectId=$projectId")
            } catch (e: Exception) {
                Log.e(TAG, "DEMO_SEED failed", e)
                pending.setResultData("FAIL: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleNavigate(context: Context, intent: Intent) {
        val route = intent.getStringExtra("route")
        if (route.isNullOrBlank()) {
            setResultData("FAIL: missing --es route")
            return
        }
        if (!isAllowedRoute(route)) {
            Log.w(TAG, "NAVIGATE blocked — route not in whitelist: $route")
            setResultData("FAIL: route not allowed: $route")
            return
        }
        ScreenshotRigBus.emitNavigate(route)
        setResultData("OK")
    }

    private fun handleLocale(context: Context, intent: Intent) {
        val lang = intent.getStringExtra("lang")
        if (lang.isNullOrBlank()) {
            setResultData("FAIL: missing --es lang")
            return
        }
        if (lang !in setOf("de", "en")) {
            setResultData("FAIL: unsupported lang $lang (only de|en in beta)")
            return
        }
        Handler(Looper.getMainLooper()).post {
            com.uip.oneapp.ui.localization.LocalizationManager.setLanguage(context, lang)
        }
        setResultData("OK")
    }

    private fun handleUiState(context: Context, intent: Intent) {
        val state = intent.getStringExtra("state")
        if (state.isNullOrBlank()) {
            setResultData("FAIL: missing --es state")
            return
        }
        ScreenshotRigBus.emitUiState(state)
        setResultData("OK")
    }
}
