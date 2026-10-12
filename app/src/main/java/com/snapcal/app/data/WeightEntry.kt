package com.snapcal.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One weigh-in per day. Morning weighing is recommended, so for automatic scale readings the first one
 * of the day is kept; manual entries always replace.
 */
@Entity(tableName = "weights")
data class WeightEntry(
    /** Local date `yyyy-MM-dd`. */
    @PrimaryKey val day: String,
    val kg: Double,
    val measuredAt: Long,
    /** Foot-to-foot impedance from a BIA scale, Ω (null for manual entries or socks). */
    val impedance50: Double? = null,
    val impedance250: Double? = null,
    val heartRate: Int? = null,
    /** "manual" | "scale" | "health_connect" */
    val source: String = "manual",
)

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)

    @Query("DELETE FROM weights WHERE day = :day")
    suspend fun delete(day: String)

    @Query("SELECT * FROM weights ORDER BY day")
    fun all(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY day")
    suspend fun allNow(): List<WeightEntry>

    @Query("SELECT * FROM weights WHERE day >= :fromDay ORDER BY day")
    suspend fun since(fromDay: String): List<WeightEntry>

    @Query("SELECT * FROM weights WHERE day = :day")
    suspend fun get(day: String): WeightEntry?

    @Query("SELECT * FROM weights ORDER BY day DESC LIMIT 1")
    suspend fun latest(): WeightEntry?
}
