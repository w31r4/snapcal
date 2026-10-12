package com.snapcal.app

import android.app.Application
import com.snapcal.app.data.AppDatabase
import com.snapcal.app.data.Exporter
import com.snapcal.app.data.MealRepository
import com.snapcal.app.data.Settings
import com.snapcal.app.health.HealthBridge
import com.snapcal.app.scale.ScaleBridge

class SnapCalApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var repository: MealRepository
        private set
    lateinit var scale: ScaleBridge
        private set
    lateinit var exporter: Exporter
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        val db = AppDatabase.create(this)
        repository = MealRepository(this, db.mealDao(), db.weightDao(), db.rawDao(), settings, HealthBridge(this))
        scale = ScaleBridge(settings, repository)
        exporter = Exporter(this, db, settings)
    }
}
