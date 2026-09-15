package com.uip.oneapp.ui.screens.projectdetail

import com.uip.oneapp.export.UsbExportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Z-1 (Welle bedienbefunde-0915): Louis' Befund war, dass die Einzelauswahl beim Oeffnen
 * bereits ALLE Dateien vorauswaehlt. Reine Funktionen (kein Robolectric noetig — anders als
 * [UsbExportDialogRefreshTest], die eine echte Composition treibt).
 */
class UsbExportDialogTest {

    private fun file(path: String, category: String = "fotos") =
        UsbExportService.ExportFile(zipPath = path, file = File(path), category = category)

    private val threeFiles = listOf(file("fotos/a.jpg"), file("fotos/b.jpg"), file("videos/c.mp4", "videos"))

    @Test
    fun initialExportSelection_selectionStartsEmpty() {
        assertEquals(
            "Louis' Befund: die Einzelauswahl beginnt leer, nicht mit allen Dateien vorbelegt",
            emptyList<String>(),
            initialExportSelection(threeFiles),
        )
    }

    @Test
    fun exportStartEnabled_singleModeNothingSelected_false() {
        assertFalse(
            "ohne Haken ist der Exportknopf gesperrt",
            computeExportEnabled(fullProject = false, allFiles = threeFiles, selectedPaths = emptyList()),
        )
    }

    @Test
    fun exportStartEnabled_fullProjectNoFiles_false() {
        assertFalse(
            "Vollprojekt ohne Dateien sperrt den Knopf ebenso (E-2)",
            computeExportEnabled(fullProject = true, allFiles = emptyList(), selectedPaths = emptyList()),
        )
    }

    @Test
    fun exportStartEnabled_singleModeOneSelected_true() {
        assertTrue(
            computeExportEnabled(fullProject = false, allFiles = threeFiles, selectedPaths = listOf("fotos/a.jpg")),
        )
    }

    @Test
    fun exportStartEnabled_fullProjectWithFiles_true() {
        assertTrue(
            computeExportEnabled(fullProject = true, allFiles = threeFiles, selectedPaths = emptyList()),
        )
    }

    @Test
    fun selectionCount_countsOnlyKnownPaths() {
        // 2 bekannte Pfade + 1 fremder (verwaister) Pfad -> zaehlt nur die 2 bekannten.
        val selected = listOf("fotos/a.jpg", "videos/c.mp4", "fotos/verwaist.jpg")
        assertEquals(2, selectionCount(threeFiles, selected))
    }

    @Test
    fun selectionCount_allSelected_equalsFileCount() {
        assertEquals(
            threeFiles.size,
            selectionCount(threeFiles, threeFiles.map { it.zipPath }),
        )
    }
}
