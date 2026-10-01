package com.colmenas.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Apiary(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val location: String = "", val notes: String = "")
@Entity(indices=[Index(value=["code"], unique=true)]) data class Hive(@PrimaryKey(autoGenerate = true) val id: Long = 0, val apiaryId: Long, val code: String, val name: String, val queenType: String = "", val queenYear: String = "", val status: String = "Activa", val notes: String = "")
@Entity data class Inspection(@PrimaryKey(autoGenerate = true) val id: Long = 0, val hiveId: Long, val timestamp: Long = System.currentTimeMillis(), val strength: Int = 3, val brood: Boolean = true, val queenSeen: Boolean = false, val honey: String = "Media", val health: String = "Buena", val feeding: String = "", val treatment: String = "", val notes: String = "")

@Dao interface BeeDao {
    @Query("SELECT * FROM Apiary ORDER BY name") fun apiaries(): Flow<List<Apiary>>
    @Insert suspend fun addApiary(a: Apiary): Long
    @Delete suspend fun deleteApiary(a: Apiary)
    @Query("SELECT * FROM Hive ORDER BY name") fun hives(): Flow<List<Hive>>
    @Insert suspend fun addHive(h: Hive): Long
    @Update suspend fun updateHive(h: Hive)
    @Delete suspend fun deleteHive(h: Hive)
    @Query("SELECT * FROM Hive WHERE code=:code LIMIT 1") suspend fun hiveByCode(code:String): Hive?
    @Query("SELECT * FROM Inspection WHERE hiveId=:hiveId ORDER BY timestamp DESC") fun inspections(hiveId:Long): Flow<List<Inspection>>
    @Insert suspend fun addInspection(i: Inspection): Long
}

@Database(entities=[Apiary::class,Hive::class,Inspection::class], version=1, exportSchema=false)
abstract class BeeDb: RoomDatabase(){ abstract fun dao():BeeDao
    companion object { @Volatile private var I:BeeDb?=null
        fun get(c:Context)=I?: synchronized(this){ I?:Room.databaseBuilder(c.applicationContext,BeeDb::class.java,"colmenas.db").build().also{I=it} }
    }
}
