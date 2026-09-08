package com.uip.oneapp.export

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.uip.oneapp.data.local.entity.ProjectEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Service-Glue des USB-Exports (N-1, Runde 2).
 *
 * `getExternalFilesDir` liefert laut Android-Dokumentation `null`, wenn der
 * externe Speicher nicht eingehaengt ist. Vor dieser Welle lief dieser Fall
 * still in eine leere Liste (`File(null, ...)` existierte nicht, der Ordner
 * wurde uebersprungen); das `!!` in [UsbExportService.collectProjectFiles]
 * haette daraus einen Absturz im Feld gemacht. Dieser Test faehrt den Service
 * gegen einen Kontext ohne externen Speicher und verlangt die leere Liste
 * statt einer Exception.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UsbExportServiceTest {

    /** Kontext, dessen externer Speicher nicht eingehaengt ist (wie Doku-Fall). */
    private class NoExternalStorageContext(base: Context) : ContextWrapper(base) {
        override fun getExternalFilesDir(type: String?): File? = null
        override fun getExternalFilesDirs(type: String?): Array<File> = emptyArray()
        override fun getExternalCacheDir(): File? = null
        override fun getExternalMediaDirs(): Array<File> = emptyArray()
    }

    @Test
    fun collectProjectFiles_externerSpeicherNichtEingehaengt_liefertLeereListe() {
        val context = NoExternalStorageContext(ApplicationProvider.getApplicationContext())
        val service = UsbExportService(context)
        val files = service.collectProjectFiles(
            ProjectEntity(id = 42, projectNumber = "REF0904-H1"),
            emptyList(),
            emptyList()
        )
        assertTrue(
            "Ohne eingehaengten externen Speicher muss der Exportbestand leer sein " +
                "statt abzustuerzen, war: ${files.map { it.zipPath }}",
            files.isEmpty()
        )
    }
}
