package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "heart_rate_entries")
data class HeartRateEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bpm: Int,
    val timestamp: Long,
    val activityType: String, // "Resting", "Walking", "Cardio", "Sleep"
    val deviceSource: String // "Fitbit", "Apple Watch", "Manual"
)

@Entity(tableName = "sleep_entries")
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateLabel: String, // "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    val timestamp: Long, // For sorting
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val deviceSource: String // "Fitbit", "Apple Watch", "Manual"
)

@Entity(tableName = "sync_states")
data class SyncState(
    @PrimaryKey val deviceName: String, // "Fitbit", "Apple Watch"
    val isConnected: Boolean,
    val lastSyncedTime: Long
)

@Entity(tableName = "weekly_insights")
data class WeeklyInsight(
    @PrimaryKey val id: Int = 1,
    val insightText: String,
    val generatedTime: Long,
    val summaryNotes: String
)

@Dao
interface HealthDao {
    // Heart Rate
    @Query("SELECT * FROM heart_rate_entries ORDER BY timestamp DESC")
    fun getAllHeartRates(): Flow<List<HeartRateEntry>>

    @Query("SELECT * FROM heart_rate_entries ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHeartRates(): Flow<List<HeartRateEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(entry: HeartRateEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRates(entries: List<HeartRateEntry>)

    @Query("DELETE FROM heart_rate_entries")
    suspend fun deleteAllHeartRates()

    // Sleep
    @Query("SELECT * FROM sleep_entries ORDER BY timestamp ASC")
    fun getAllSleepEntries(): Flow<List<SleepEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(entry: SleepEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepEntries(entries: List<SleepEntry>)

    @Query("DELETE FROM sleep_entries")
    suspend fun deleteAllSleep()

    // Sync States
    @Query("SELECT * FROM sync_states")
    fun getAllSyncStates(): Flow<List<SyncState>>

    @Query("SELECT * FROM sync_states WHERE deviceName = :deviceName LIMIT 1")
    suspend fun getSyncStateForDevice(deviceName: String): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncState(state: SyncState)

    // Weekly Insights
    @Query("SELECT * FROM weekly_insights WHERE id = :id LIMIT 1")
    fun getWeeklyInsight(id: Int = 1): Flow<WeeklyInsight?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyInsight(insight: WeeklyInsight)
}

@Database(
    entities = [HeartRateEntry::class, SleepEntry::class, SyncState::class, WeeklyInsight::class],
    version = 1,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_sync_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
