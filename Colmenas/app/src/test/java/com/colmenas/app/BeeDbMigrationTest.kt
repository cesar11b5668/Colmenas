package com.colmenas.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BeeDbMigrationTest {
    @Test fun upgradePreservesOldRecordsAndStoresNfcAndMultiplePhotos() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE Apiary (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, location TEXT NOT NULL, notes TEXT NOT NULL)")
            old.execSQL("CREATE TABLE Hive (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, apiaryId INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, queenType TEXT NOT NULL, queenYear TEXT NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_Hive_code ON Hive(code)")
            old.execSQL("CREATE TABLE Inspection (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hiveId INTEGER NOT NULL, timestamp INTEGER NOT NULL, strength INTEGER NOT NULL, brood INTEGER NOT NULL, queenSeen INTEGER NOT NULL, honey TEXT NOT NULL, health TEXT NOT NULL, feeding TEXT NOT NULL, treatment TEXT NOT NULL, notes TEXT NOT NULL)")
            old.execSQL("INSERT INTO Apiary VALUES (1, 'El campo', '', '')")
            old.execSQL("INSERT INTO Hive VALUES (1, 1, 'COL-OLD', 'Colmena anterior', '', '', 'Activa', '')")
            old.execSQL("INSERT INTO Inspection VALUES (1, 1, 1000, 3, 1, 0, 'Media', 'Buena', '', '', 'Registro anterior')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, BeeDb::class.java, name)
            .addMigrations(BeeDb.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            assertEquals("El campo", dao.apiaries().first().single().name)
            val hive = dao.hiveByCode("COL-OLD")!!
            assertNull(hive.nfcTagId)
            assertEquals("", hive.photoPath)
            val oldInspection = dao.inspections(hive.id).first().single()
            assertEquals("Registro anterior", oldInspection.notes)
            assertEquals("Media", oldInspection.honey)
            assertTrue(oldInspection.photoPaths.isEmpty())
            dao.updateHive(hive.copy(nfcTagId = "0480FF", photoPath = "hive-photo"))
            assertEquals(hive.id, dao.hiveByNfcTagId("0480FF")!!.id)
            dao.addInspection(Inspection(hiveId = hive.id, health = "Regular", honey = "Oscura", strength = 5,
                photoPaths = listOf("photo-one", "photo-two")))
            val recent = dao.inspections(hive.id).first().first()
            assertEquals(listOf("photo-one", "photo-two"), recent.photoPaths)
            assertEquals("Regular", recent.health)
            assertEquals("Oscura", recent.honey)
            assertEquals(5, recent.strength)
            // UID uniqueness prevents accidentally associating two hives with one card.
            try {
                dao.addHive(Hive(apiaryId = 1, code = "COL-OTHER", name = "Otra", nfcTagId = "0480FF"))
                fail("Duplicate NFC UID must be rejected")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            dao.addHive(Hive(apiaryId = 1, code = "COL-NO-NFC-1", name = "Sin tarjeta 1"))
            dao.addHive(Hive(apiaryId = 1, code = "COL-NO-NFC-2", name = "Sin tarjeta 2"))
            assertEquals(3, dao.hives().first().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
