package com.colmenas.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
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
class ApiaryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetDatabase() {
        val db = BeeDb.get(compose.activity)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test fun createsHiveInsideOpenedApiaryAndKeepsOtherApiariesSeparate() {
        val dao = BeeDb.get(compose.activity).dao()
        val secondId = runBlocking {
            val firstId = dao.addApiary(Apiary(name = "Apiario norte"))
            val secondId = dao.addApiary(Apiary(name = "Apiario sur"))
            dao.addHive(Hive(apiaryId = firstId, code = "COL-NORTH", name = "Colmena norte"))
            dao.addHive(Hive(apiaryId = secondId, code = "COL-SOUTH", name = "Colmena sur"))
            secondId
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("2 apiarios • 2 colmenas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+ Colmena").assertDoesNotExist()
        compose.onNodeWithText("Colmena norte").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Apiario sur"))
        compose.onNodeWithText("Apiario sur").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 colmenas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Colmena sur"))
        compose.onNodeWithText("Colmena sur").assertIsDisplayed()
        compose.onNodeWithText("Colmena norte").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("+ Colmena"))
        compose.onNodeWithText("+ Colmena").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Nueva colmena sur")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { dao.hives().first().any { it.name == "Nueva colmena sur" } } }
        val created = runBlocking { dao.hives().first().single { it.name == "Nueva colmena sur" } }
        assertEquals(secondId, created.apiaryId)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nueva colmena sur"))
        compose.onNodeWithText("Nueva colmena sur").performClick()
        compose.onNodeWithText("Nueva inspección").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Colmenas de este apiario"))
        compose.onNodeWithText("Colmenas de este apiario").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Apiario norte"))
        compose.onNodeWithText("Apiario norte").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Colmena norte"))
        compose.onNodeWithText("Colmena norte").assertIsDisplayed()
        compose.onNodeWithText("Colmena sur").assertDoesNotExist()
        compose.onNodeWithText("Nueva colmena sur").assertDoesNotExist()
    }
}
