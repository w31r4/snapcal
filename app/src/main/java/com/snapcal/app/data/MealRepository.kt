package com.snapcal.app.data

import android.content.Context
import android.util.Log
import com.snapcal.app.ai.Analyzers
import com.snapcal.app.ai.FoodAnalysis
import com.snapcal.app.ai.ProviderConfig
import com.snapcal.app.health.HealthBridge
import com.snapcal.app.util.Days
import com.snapcal.app.util.Images
import com.snapcal.app.science.AdaptiveTdee
import com.snapcal.app.science.BurnSource
import com.snapcal.app.science.DailyBurn
import com.snapcal.app.science.DailyBurnModel
import com.snapcal.app.science.PhotoCalibration
import com.snapcal.app.science.Trend
import com.snapcal.app.science.WeightModel
import com.snapcal.app.scale.WeighIn
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class MealRepository(
    private val context: Context,
    private val dao: MealDao,
    private val weightDao: WeightDao,
    private val rawDao: RawDao,
    private val settings: Settings,
    val health: HealthBridge,
) {
    // ---- photo calibration ----

    suspend fun photoCalibration(): PhotoCalibration.Result =
        PhotoCalibration.estimate(dao.verified().map { it.aiKcal to it.totalKcal })

    /**
     * Factor applied to *unverified* meals' totals for energy accounting. Verified meals are already exact.
     * The same factor is used for daily intake and the adaptive-TDEE window so both stay in the same units.
     */
    suspend fun activeCalibrationFactor(): Double = photoCalibration().let { if (it.active) it.factor else 1.0 }

    fun calibratedKcal(meal: Meal, factor: Double): Int =
        if (meal.verified || factor == 1.0) meal.totalKcal else (meal.totalKcal * factor).toInt()

    suspend fun setVerified(meal: Meal, verified: Boolean) {
        dao.update(meal.copy(verified = verified, version = meal.version + 1))
    }

    fun weights() = weightDao.all()

    /** Record a weigh-in, keep the profile's current weight in sync, and mirror it to Health Connect. */
    suspend fun logWeight(kg: Double, at: Long = System.currentTimeMillis()) {
        weightDao.upsert(WeightEntry(Days.dayOf(at), kg, at))
        updateProfileWeight()
        if (settings.healthSync) runCatching { health.writeWeight(kg, Instant.ofEpochMilli(at)) }
            .onFailure { Log.w(TAG, "Health Connect weight write failed", it) }
    }

    /**
     * Automatic weigh-in from the scale. Keeps the first reading of the day (closest to a fasted morning
     * weight; foot-to-foot impedance also drifts ~30 Ω over the day, Oshima 2006). Plausibility-checked
     * against the latest weight so a family member on the same profile doesn't pollute the trend.
     */
    suspend fun logWeighIn(w: WeighIn) {
        val day = Days.dayOf(w.atMillis)
        val disposition = when {
            weightDao.get(day) != null -> "later_same_day"
            !isPlausible(w.massKg, LocalDate.parse(day)) -> "implausible"
            else -> "used"
        }
        rawDao.insertReading(
            ScaleReading(
                day = day, at = w.atMillis, massKg = w.massKg, impedance50 = w.impedanceLow,
                impedance250 = w.impedanceHigh, heartRate = w.heartRate, profileId = w.profileId, disposition = disposition,
            ),
        )
        if (disposition != "used") return
        weightDao.upsert(
            WeightEntry(day, w.massKg, w.atMillis, w.impedanceLow, w.impedanceHigh, w.heartRate, source = "scale"),
        )
        updateProfileWeight()
        if (settings.healthSync) runCatching { health.writeWeight(w.massKg, Instant.ofEpochMilli(w.atMillis)) }
    }

    /**
     * Rejects readings far from the user's *recent* weight (likely a family member on the same profile).
     * Only weights from the last 14 days count as reference, so after a break the user is never locked out.
     */
    private suspend fun isPlausible(kg: Double, day: LocalDate): Boolean {
        val recent = weightDao.since(day.minusDays(PLAUSIBILITY_WINDOW_DAYS).toString()).filter { it.day < day.toString() }
        if (recent.isEmpty()) return true
        val ref = recent.map { it.kg }.sorted()[recent.size / 2]
        return kotlin.math.abs(kg - ref) <= PLAUSIBILITY_MAX_DIFF_KG
    }

    suspend fun logOtherProfileReading(w: WeighIn) {
        rawDao.insertReading(
            ScaleReading(
                day = Days.dayOf(w.atMillis), at = w.atMillis, massKg = w.massKg, impedance50 = w.impedanceLow,
                impedance250 = w.impedanceHigh, heartRate = w.heartRate, profileId = w.profileId, disposition = "other_profile",
            ),
        )
    }

    suspend fun deleteWeight(day: String) {
        weightDao.delete(day)
        updateProfileWeight()
    }

    private suspend fun updateProfileWeight() {
        weightDao.latest()?.let { settings.profile = settings.profile.copy(weightKg = it.kg) }
    }

    /** Pull weigh-ins from smart-scale apps via Health Connect for days we don't have yet. */
    suspend fun importWeightsFromHealth(days: Long = 60) {
        val to = Instant.now()
        val from = to.minus(Duration.ofDays(days))
        val existing = weightDao.since(LocalDate.now().minusDays(days).toString()).map { it.day }.toSet()
        val imported = runCatching { health.readWeights(from, to) }.getOrDefault(emptyMap())
        imported.forEach { (day, v) ->
            if (day.toString() !in existing) {
                weightDao.upsert(WeightEntry(day.toString(), v.first, v.second.toEpochMilli(), source = "health_connect"))
            }
        }
        if (imported.isNotEmpty()) updateProfileWeight()
    }

    /** Adaptive TDEE from the last 28 days of logs and weigh-ins, or null if there isn't enough data yet. */
    suspend fun adaptiveTdee(prior: Int): AdaptiveTdee.Result? {
        val today = LocalDate.now()
        val from = today.minusDays(AdaptiveTdee.WINDOW_DAYS.toLong()).toString()
        val factor = activeCalibrationFactor()
        val intake = dao.mealsSince(from).groupBy { LocalDate.parse(it.day) }
            .mapValues { (_, ms) -> ms.sumOf { calibratedKcal(it, factor) } }
        val weights = weightDao.since(from).associate { LocalDate.parse(it.day) to it.kg }
        return AdaptiveTdee.estimate(
            today = today,
            trackingStart = LocalDate.ofEpochDay(settings.trackingStartEpochDay),
            intakeByDay = intake,
            weights = weights,
            priorTdee = prior,
            fatMassKg = settings.profile.fatMassKg,
        )
    }

    /** Actual vs expected weight change over the last 28 days, plus a 12-week projection on the current plan. */
    suspend fun outlook(plan: Plan): Outlook {
        val today = LocalDate.now()
        val from = today.minusDays(27)
        val weights = weightDao.since(from.toString())
        val factor = activeCalibrationFactor()
        val days = dao.mealsSince(from.toString()).filter { LocalDate.parse(it.day).isBefore(today) }
            .groupBy { it.day }.mapValues { (_, ms) -> ms.sumOf { calibratedKcal(it, factor) } }
        val sex = settings.profile.sex
        val slope = if (weights.size >= 5 && weights.last().day != weights.first().day) {
            Trend.theilSenSlope(weights.map { LocalDate.parse(it.day).toEpochDay().toDouble() to it.kg })
        } else null
        val trend = weights.takeIf { it.isNotEmpty() }?.let { Trend.ema(it.map { w -> w.kg }).last() }
        // Expected uses the prior/adaptive maintenance currently in the plan.
        val avgDeficit = if (days.size >= 7) (plan.tdee - days.values.average()).toInt() else null
        val expected = avgDeficit?.let { -it * 7 / WeightModel.rho(sex) }
        val latest = weights.lastOrNull()?.kg
        return Outlook(
            latestKg = latest,
            trendKg = trend,
            actualKgPerWeek = slope?.times(7),
            expectedKgPerWeek = expected,
            avgLoggedDeficit = avgDeficit,
            loggedDays = days.size,
            weighIns = weights.size,
            projected12wKg = (trend ?: latest)?.let { it + WeightModel.deltaWeight(sex, -plan.deficit.toDouble(), 84.0) },
        )
    }

    /** Persist today's computed state; recomputed on every refresh, final value is the end-of-day one. */
    suspend fun snapshotToday(plan: Plan, burn: DailyBurn?) {
        val today = LocalDate.now().toString()
        val meals = dao.mealsSince(today).filter { it.day == today }
        val factor = activeCalibrationFactor()
        rawDao.upsertSnapshot(
            DailySnapshot(
                day = today,
                intakeKcal = meals.sumOf { it.totalKcal },
                intakeCalibratedKcal = meals.sumOf { calibratedKcal(it, factor) },
                mealCount = meals.size,
                proteinG = meals.sumOf { it.proteinG },
                weightKg = weightDao.get(today)?.kg,
                tdeePrior = plan.tdeePrior,
                tdeeAdaptive = if (plan.tdeeIsAdaptive) plan.tdee else null,
                activityAdjustment = burn?.activityAdjustmentKcal ?: 0,
                burnedKcal = burn?.totalKcal ?: plan.tdee,
                targetIntake = plan.dailyIntakeTarget,
                photoCalibrationFactor = factor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Burn for [day]: calibrated baseline + half of above-usual wearable activity. */
    suspend fun dailyBurn(day: LocalDate, plan: Plan): DailyBurn {
        val source = if (plan.tdeeIsAdaptive) BurnSource.ADAPTIVE else BurnSource.PRIOR
        val activity = runCatching { health.readActivity(day.minusDays(28), day) }.getOrDefault(emptyMap())
        val todayData = activity[day]
        // Compare like with like: only history days from the same kind of source.
        val history = activity.filterKeys { it.isBefore(day) }.values
            .filter { it.hasRealData && it.workoutsOnly == (todayData?.workoutsOnly ?: false) }
            .map { it.activeKcal }
        return DailyBurnModel.compute(
            baselineTdee = plan.tdee,
            baselineSource = source,
            today = todayData,
            todayComplete = day.isBefore(LocalDate.now()),
            history = history,
        )
    }

    fun mealsForDay(day: String) = dao.mealsForDay(day)
    fun dayTotals() = dao.dayTotals()
    fun observe(id: Long) = dao.observe(id)

    fun currentConfig(): ProviderConfig {
        val p = settings.provider
        return ProviderConfig(p, settings.apiKey(p), settings.model(p), settings.baseUrl(p))
    }

    /** Send photos to the configured model and store the result as a new meal. Returns the meal id. */
    suspend fun analyzeAndSave(photos: List<ByteArray>, note: String, eatenAt: Long = System.currentTimeMillis()): Long {
        val config = currentConfig()
        val started = System.currentTimeMillis()
        val result = runCatching { Analyzers.create(config).analyze(photos.map(Images::toPayload), note) }
        val analysis = result.getOrElse { e ->
            // Keep failures too (mealId 0 = no meal created).
            logAiCall(0, config, note, photos.size, started, null, e)
            throw e
        }.analysis
        // Persist photos only once there is a meal to attach them to.
        val paths = photos.map { Images.saveMealPhoto(context, it) }
        val meal = Meal(
            eatenAt = eatenAt,
            day = Days.dayOf(eatenAt),
            title = titleFor(analysis, eatenAt),
            itemsJson = encodeItems(analysis.items),
            totalKcal = analysis.totalKcal,
            note = note,
            photoPaths = paths.joinToString("\n"),
            provider = config.provider.name,
            model = config.model,
            confidence = analysis.confidence,
            aiNotes = analysis.notes,
            aiQuestion = analysis.question,
            aiKcal = analysis.totalKcal,
        )
        val id = dao.insert(meal)
        logAiCall(id, config, note, photos.size, started, result.getOrNull(), null)
        syncToHealth(meal.copy(id = id))
        return id
    }

    /**
     * Re-estimate an existing meal using its saved photos plus an extra user hint
     * (e.g. the answer to the model's follow-up question).
     */
    suspend fun reanalyze(meal: Meal, extraHint: String) {
        val config = currentConfig()
        val photos = meal.photos.mapNotNull { runCatching { File(it).readBytes() }.getOrNull() }
        require(photos.isNotEmpty()) { "原照片已不存在" }
        val note = listOf(meal.note, extraHint).filter { it.isNotBlank() }.joinToString("；")
        val started = System.currentTimeMillis()
        val r = runCatching { Analyzers.create(config).analyze(photos.map(Images::toPayload), note) }
        logAiCall(meal.id, config, note, photos.size, started, r.getOrNull(), r.exceptionOrNull())
        val a = r.getOrThrow().analysis
        val updated = meal.copy(
            title = titleFor(a, meal.eatenAt),
            itemsJson = encodeItems(a.items),
            totalKcal = a.totalKcal,
            note = note,
            provider = config.provider.name,
            model = config.model,
            confidence = a.confidence,
            aiNotes = a.notes,
            aiQuestion = "",
            aiKcal = a.totalKcal,
            verified = false,
            version = meal.version + 1,
        )
        dao.update(updated)
        syncToHealth(updated)
    }

    private suspend fun logAiCall(
        mealId: Long, config: ProviderConfig, note: String, images: Int, started: Long,
        result: com.snapcal.app.ai.AnalyzerResult?, error: Throwable?,
    ) {
        runCatching {
            rawDao.insertAiCall(
                AiCall(
                    mealId = mealId,
                    at = started,
                    provider = config.provider.name,
                    model = config.model,
                    promptVersion = com.snapcal.app.ai.Prompt.VERSION,
                    note = note,
                    imageCount = images,
                    rawText = result?.rawText,
                    parsedKcal = result?.analysis?.totalKcal,
                    error = error?.let { it.message ?: it.toString() },
                    latencyMs = System.currentTimeMillis() - started,
                ),
            )
        }.onFailure { Log.w(TAG, "ai_calls insert failed", it) }
    }

    suspend fun update(meal: Meal) {
        val updated = meal.copy(
            totalKcal = meal.items.sumOf { it.kcal },
            day = Days.dayOf(meal.eatenAt),
            version = meal.version + 1,
        )
        dao.update(updated)
        syncToHealth(updated)
    }

    /** Soft delete: hidden from totals but kept (with photos) for later analysis. */
    suspend fun delete(meal: Meal) {
        dao.update(meal.copy(deleted = true, version = meal.version + 1))
        if (settings.healthSync) runCatching { health.deleteMeal(meal.id) }
            .onFailure { Log.w(TAG, "Health Connect delete failed", it) }
    }

    private suspend fun syncToHealth(meal: Meal) {
        if (!settings.healthSync) return
        runCatching { health.writeMeal(meal) }.onFailure { Log.w(TAG, "Health Connect write failed", it) }
    }

    private fun titleFor(a: FoodAnalysis, eatenAt: Long): String =
        a.title.ifBlank { Days.mealSlot(eatenAt) + "：" + a.items.joinToString("、") { it.name }.ifBlank { "未识别" } }

    companion object {
        private const val TAG = "MealRepository"
        private const val PLAUSIBILITY_WINDOW_DAYS = 14L
        private const val PLAUSIBILITY_MAX_DIFF_KG = 5.0
    }
}
