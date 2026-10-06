package com.colmenas.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiaryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createsHiveInsideOpenedApiaryAndKeepsOtherApiariesSeparate() {
        val dao = BeeDb.get(compose.activity).dao()
        val secondId = runBlocking {
            val firstId = dao.addApiary(Apiary(name = "Apiario norte"))
            val secondId = dao.addApiary(Apiary(name = "Apiario sur"))
            dao.addHive(Hive(apiaryId = firstId, code = "COL-NORTH", name = "Colmena norte"))
            dao.addHive(Hive(apiaryId = secondId, code = "COL-SOUTH", name = "Colmena sur"))
            secondId
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Apiario sur").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+ Colmena").assertDoesNotExist()
        compose.onNodeWithText("Colmena norte").assertDoesNotExist()
        compose.onNodeWithText("Apiario sur").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Colmena sur").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Colmena sur").assertIsDisplayed()
        compose.onNodeWithText("Colmena norte").assertDoesNotExist()
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
        compose.onNodeWithText("Colmenas de este apiario").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Apiario norte").performClick()
        compose.onNodeWithText("Colmena norte").assertIsDisplayed()
        compose.onNodeWithText("Colmena sur").assertDoesNotExist()
        compose.onNodeWithText("Nueva colmena sur").assertDoesNotExist()
    }
}
