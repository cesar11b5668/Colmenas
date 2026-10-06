package com.colmenas.app

import android.nfc.NfcAdapter
import android.app.Application
import android.content.pm.PackageManager
import org.robolectric.shadows.ShadowNfcAdapter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NfcTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiaryNfcFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetDatabase() {
        val db = BeeDb.get(compose.activity)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test fun associatesCardDuringApiaryCreationAndNeverOffersItOnHiveForm() {
        val dao = BeeDb.get(compose.activity).dao()
        val adapter = NfcAdapter.getDefaultAdapter(compose.activity)
        assertNotNull(adapter)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        shadowOf(adapter).setEnabled(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("+ Apiario").performClick()
        compose.onNodeWithText("Nombre del apiario").performTextInput("Apiario con tarjeta")
        compose.onNodeWithText("Asociar tarjeta NFC").assertIsEnabled().performClick()
        compose.runOnIdle { compose.activity.acceptScan(Scan(tagId = "04AABBCC")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tarjeta NFC agregada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { dao.apiaryByNfcTagId("04AABBCC") != null } }
        val apiary = runBlocking { dao.apiaryByNfcTagId("04AABBCC")!! }
        assertEquals("Apiario con tarjeta", apiary.name)
        compose.runOnIdle { compose.activity.acceptScan(Scan(tagId = "04AABBCC")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Colmenas de este apiario").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Apiario: Apiario con tarjeta").assertIsDisplayed()
        compose.onNodeWithText("+ Colmena").performScrollTo().performClick()
        compose.onNodeWithText("Asociar tarjeta NFC").assertDoesNotExist()
        compose.onNodeWithText("Nombre").performTextInput("Colmena sin NFC propio")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { dao.hives().first().any { it.name == "Colmena sin NFC propio" } } }
        assertEquals(apiary.id, runBlocking { dao.hives().first().single { it.name == "Colmena sin NFC propio" }.apiaryId })
        // QR keeps opening the individual hive, independently of the apiary's NFC card.
        val hive = runBlocking { dao.hives().first().single { it.name == "Colmena sin NFC propio" } }
        compose.runOnIdle { compose.activity.acceptScan(Scan(code = hive.code)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nueva inspección").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Asociar tarjeta NFC").assertDoesNotExist()
        compose.onNodeWithText("Cambiar tarjeta NFC").assertDoesNotExist()
    }
}


class NfcTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShadowNfcAdapter.setNfcHardwareExists(true)
        shadowOf(packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        shadowOf(NfcAdapter.getDefaultAdapter(this)).setEnabled(true)
    }
}
