package com.colmenas.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity
data class Apiary(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val location: String = "", val notes: String = "")

@Entity(indices = [Index(value = ["code"], unique = true), Index(value = ["nfcTagId"], unique = true)])
data class Hive(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiaryId: Long,
    val code: String,
    val name: String,
    val queenType: String = "",
    val queenYear: String = "",
    val status: String = "Activa",
    val notes: String = "",
    val nfcTagId: String? = null,
    @ColumnInfo(defaultValue = "''") val photoPath: String = ""
)

@Entity
data class Inspection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hiveId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val strength: Int = 3,
    val brood: Boolean = true,
    val queenSeen: Boolean = false,
    val honey: String = "Clara",
    val health: String = "Buena",
    val feeding: String = "",
    val treatment: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "'[]'") val photoPaths: List<String> = emptyList()
)

class PhotoConverters {
    @TypeConverter fun encode(paths: List<String>): String = JSONArray(paths).toString()
    @TypeConverter fun decode(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }
}

@Dao
interface BeeDao {
    @Query("SELECT * FROM Apiary ORDER BY name") fun apiaries(): Flow<List<Apiary>>
    @Insert suspend fun addApiary(a: Apiary): Long
    @Delete suspend fun deleteApiary(a: Apiary)
    @Query("SELECT * FROM Hive ORDER BY name") fun hives(): Flow<List<Hive>>
    @Insert suspend fun addHive(h: Hive): Long
    @Update suspend fun updateHive(h: Hive)
    @Delete suspend fun deleteHive(h: Hive)
    @Query("SELECT * FROM Hive WHERE code=:code LIMIT 1") suspend fun hiveByCode(code: String): Hive?
    @Query("SELECT * FROM Hive WHERE nfcTagId=:tagId LIMIT 1") suspend fun hiveByNfcTagId(tagId: String): Hive?
    @Query("SELECT * FROM Inspection WHERE hiveId=:hiveId ORDER BY timestamp DESC") fun inspections(hiveId: Long): Flow<List<Inspection>>
    @Insert suspend fun addInspection(i: Inspection): Long
}

@Database(entities = [Apiary::class, Hive::class, Inspection::class], version = 2, exportSchema = false)
@TypeConverters(PhotoConverters::class)
abstract class BeeDb : RoomDatabase() {
    abstract fun dao(): BeeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Hive ADD COLUMN nfcTagId TEXT")
                db.execSQL("ALTER TABLE Hive ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE UNIQUE INDEX index_Hive_nfcTagId ON Hive(nfcTagId)")
                db.execSQL("ALTER TABLE Inspection ADD COLUMN photoPaths TEXT NOT NULL DEFAULT '[]'")
            }
        }
        @Volatile private var instance: BeeDb? = null
        fun get(context: Context): BeeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BeeDb::class.java, "colmenas.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
