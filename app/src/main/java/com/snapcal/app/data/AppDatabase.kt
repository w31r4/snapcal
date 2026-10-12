package com.snapcal.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Meal::class, WeightEntry::class, AiCall::class, ScaleReading::class, DailySnapshot::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao
    abstract fun rawDao(): RawDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "snapcal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `aiKcal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `verified` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
                // Existing meals: best available baseline is the current total.
                db.execSQL("UPDATE `meals` SET `aiKcal` = `totalKcal`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_calls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mealId` INTEGER NOT NULL, " +
                        "`at` INTEGER NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `promptVersion` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `imageCount` INTEGER NOT NULL, `rawText` TEXT, `parsedKcal` INTEGER, `error` TEXT, " +
                        "`latencyMs` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_calls_mealId` ON `ai_calls` (`mealId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scale_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, `massKg` REAL NOT NULL, `impedance50` REAL, `impedance250` REAL, `heartRate` INTEGER, " +
                        "`profileId` INTEGER NOT NULL, `disposition` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scale_readings_day` ON `scale_readings` (`day`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_snapshots` (`day` TEXT NOT NULL, `intakeKcal` INTEGER NOT NULL, " +
                        "`intakeCalibratedKcal` INTEGER NOT NULL, `mealCount` INTEGER NOT NULL, `proteinG` REAL NOT NULL, `weightKg` REAL, " +
                        "`tdeePrior` INTEGER NOT NULL, `tdeeAdaptive` INTEGER, `activityAdjustment` INTEGER NOT NULL, " +
                        "`burnedKcal` INTEGER NOT NULL, `targetIntake` INTEGER NOT NULL, `photoCalibrationFactor` REAL NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`day`))",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `weights` ADD COLUMN `impedance50` REAL")
                db.execSQL("ALTER TABLE `weights` ADD COLUMN `impedance250` REAL")
                db.execSQL("ALTER TABLE `weights` ADD COLUMN `heartRate` INTEGER")
                db.execSQL("ALTER TABLE `weights` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `aiQuestion` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`day` TEXT NOT NULL, `kg` REAL NOT NULL, `measuredAt` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            }
        }
    }
}
