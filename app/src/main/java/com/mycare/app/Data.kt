package com.mycare.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val phone: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val chronic: String = "",
    val notes: String = ""
)

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val doctor: String,
    val specialty: String = "",
    val place: String = "",
    val address: String = "",
    val dateMillis: Long,
    val time: String = "",
    val reason: String = "",
    val notes: String = ""
)

@Entity(tableName = "therapy_sessions")
data class TherapySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val center: String = "",
    val total: Int,
    val done: Int = 0,
    val nextDateMillis: Long = 0,
    val time: String = "",
    val notes: String = ""
)

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String = "",
    val timesPerDay: Int = 1,
    val firstTime: String = "",
    val startMillis: Long = 0,
    val endMillis: Long = 0,
    val continuous: Boolean = true,
    val instructions: String = "",
    val notes: String = ""
)

@Entity(tableName = "complaints")
data class Complaint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startMillis: Long = 0,
    val description: String = "",
    val severity: Int = 0,
    val continuous: Boolean = true,
    val notes: String = "",
    val wantDiscuss: Boolean = false,
    val discussed: Boolean = false
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val answered: Boolean = false
)

@Dao
interface CareDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun observeProfile(): Flow<Profile?>
    @Upsert suspend fun saveProfile(profile: Profile)

    @Query("SELECT * FROM appointments ORDER BY dateMillis ASC") fun observeAppointments(): Flow<List<Appointment>>
    @Insert suspend fun insertAppointment(item: Appointment): Long
    @Delete suspend fun deleteAppointment(item: Appointment)
    @Query("SELECT * FROM appointments WHERE dateMillis >= :now") suspend fun upcomingAppointments(now: Long): List<Appointment>

    @Query("SELECT * FROM therapy_sessions ORDER BY nextDateMillis ASC") fun observeSessions(): Flow<List<TherapySession>>
    @Insert suspend fun insertSession(item: TherapySession): Long
    @Update suspend fun updateSession(item: TherapySession)
    @Delete suspend fun deleteSession(item: TherapySession)
    @Query("SELECT * FROM therapy_sessions WHERE nextDateMillis >= :now") suspend fun upcomingSessions(now: Long): List<TherapySession>

    @Query("SELECT * FROM medications ORDER BY name ASC") fun observeMedications(): Flow<List<Medication>>
    @Query("SELECT * FROM medications") suspend fun allMedications(): List<Medication>
    @Insert suspend fun insertMedication(item: Medication): Long
    @Delete suspend fun deleteMedication(item: Medication)

    @Query("SELECT * FROM complaints ORDER BY startMillis DESC") fun observeComplaints(): Flow<List<Complaint>>
    @Insert suspend fun insertComplaint(item: Complaint): Long
    @Update suspend fun updateComplaint(item: Complaint)
    @Delete suspend fun deleteComplaint(item: Complaint)

    @Query("SELECT * FROM questions ORDER BY answered ASC, id DESC") fun observeQuestions(): Flow<List<Question>>
    @Insert suspend fun insertQuestion(item: Question): Long
    @Update suspend fun updateQuestion(item: Question)
    @Delete suspend fun deleteQuestion(item: Question)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `birthDate` TEXT NOT NULL, `gender` TEXT NOT NULL, `phone` TEXT NOT NULL, `bloodType` TEXT NOT NULL, `allergies` TEXT NOT NULL, `chronic` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `appointments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `doctor` TEXT NOT NULL, `specialty` TEXT NOT NULL, `place` TEXT NOT NULL, `address` TEXT NOT NULL, `dateMillis` INTEGER NOT NULL, `time` TEXT NOT NULL, `reason` TEXT NOT NULL, `notes` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `therapy_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `center` TEXT NOT NULL, `total` INTEGER NOT NULL, `done` INTEGER NOT NULL, `nextDateMillis` INTEGER NOT NULL, `time` TEXT NOT NULL, `notes` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `timesPerDay` INTEGER NOT NULL, `firstTime` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `continuous` INTEGER NOT NULL, `instructions` TEXT NOT NULL, `notes` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `complaints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `description` TEXT NOT NULL, `severity` INTEGER NOT NULL, `continuous` INTEGER NOT NULL, `notes` TEXT NOT NULL, `wantDiscuss` INTEGER NOT NULL, `discussed` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `answered` INTEGER NOT NULL)")
    }
}

@Database(
    entities = [CareRecord::class, Profile::class, Appointment::class, TherapySession::class, Medication::class, Complaint::class, Question::class],
    version = 2,
    exportSchema = false
)
abstract class CareDatabase : RoomDatabase() {
    abstract fun careDao(): CareDao
    companion object {
        @Volatile private var INSTANCE: CareDatabase? = null
        fun get(context: Context): CareDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CareDatabase::class.java, "my-care.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
