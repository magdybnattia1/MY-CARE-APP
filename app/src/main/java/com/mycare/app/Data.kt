package com.mycare.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records")
data class CareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val details: String = "",
    val dateMillis: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CareDao {
    @Query("SELECT * FROM records ORDER BY dateMillis ASC, createdAt DESC") fun observeAll(): Flow<List<CareRecord>>
    @Insert suspend fun insert(record: CareRecord): Long
    @Delete suspend fun delete(record: CareRecord)
}

@Database(entities = [CareRecord::class], version = 1, exportSchema = false)
abstract class CareDatabase : RoomDatabase() {
    abstract fun careDao(): CareDao
    companion object {
        @Volatile private var INSTANCE: CareDatabase? = null
        fun get(context: Context): CareDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CareDatabase::class.java, "my-care.db").build().also { INSTANCE = it }
        }
    }
}
