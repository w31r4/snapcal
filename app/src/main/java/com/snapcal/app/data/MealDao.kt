package com.snapcal.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert
    suspend fun insert(meal: Meal): Long

    @Update
    suspend fun update(meal: Meal)


    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): Meal?

    @Query("SELECT * FROM meals WHERE id = :id")
    fun observe(id: Long): Flow<Meal?>

    @Query("SELECT * FROM meals WHERE day = :day AND deleted = 0 ORDER BY eatenAt")
    fun mealsForDay(day: String): Flow<List<Meal>>

    @Query("SELECT day, SUM(totalKcal) AS totalKcal, COUNT(*) AS mealCount FROM meals WHERE day >= :fromDay AND deleted = 0 GROUP BY day")
    suspend fun dayTotalsSince(fromDay: String): List<DayTotal>

    /** Meals where the user confirmed the true value, for photo-estimate calibration. */
    @Query("SELECT * FROM meals WHERE verified = 1 AND deleted = 0 AND aiKcal > 0 ORDER BY eatenAt DESC LIMIT :limit")
    suspend fun verified(limit: Int = 60): List<Meal>

    @Query("SELECT * FROM meals ORDER BY eatenAt")
    suspend fun allIncludingDeleted(): List<Meal>

    @Query("SELECT * FROM meals WHERE day >= :fromDay AND deleted = 0")
    suspend fun mealsSince(fromDay: String): List<Meal>

    @Query("SELECT day, SUM(totalKcal) AS totalKcal, COUNT(*) AS mealCount FROM meals WHERE deleted = 0 GROUP BY day ORDER BY day DESC")
    fun dayTotals(): Flow<List<DayTotal>>
}
