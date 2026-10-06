package com.colmenas.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InspectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetDatabase() {
        val db = BeeDb.get(compose.activity)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test fun nfcOpensApiaryThenHiveInspectionPersistsSelections() {
        val dao = BeeDb.get(compose.activity).dao()
        val hiveId = runBlocking {
            val apiaryId = dao.addApiaryWithTag(Apiary(name = "El campo"), "0480FF")
            dao.addHive(Hive(apiaryId = apiaryId, code = "COL-TEST", name = "Colmena NFC"))
        }
        compose.runOnIdle { compose.activity.acceptScan(Scan(tagId = "0480FF")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Colmenas de este apiario").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Apiario: El campo").assertIsDisplayed()
        compose.onNodeWithText("Nueva inspección").assertDoesNotExist()
        compose.onNodeWithText("Colmena NFC").performScrollTo().performClick()
        compose.onNodeWithText("Nueva inspección").performClick()
        compose.onNodeWithText("Muerta").performScrollTo().performClick()
        compose.onNodeWithText("Oscura").performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.onNodeWithText("Descripción / observaciones").performScrollTo().performTextInput("Revisión de prueba")
        compose.onNodeWithText("Fotos de la inspección (0/6)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { dao.inspections(hiveId).first().isNotEmpty() } }
        val inspection = runBlocking { dao.inspections(hiveId).first().single() }
        assertEquals("Muerta", inspection.health)
        assertEquals("Oscura", inspection.honey)
        assertEquals(5, inspection.strength)
        assertEquals("Revisión de prueba", inspection.notes)
    }
}
