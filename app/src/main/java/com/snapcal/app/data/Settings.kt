package com.snapcal.app.data

import android.content.Context
import com.snapcal.app.ai.Provider
import com.snapcal.app.science.ActivityLevel
import com.snapcal.app.science.Energy
import com.snapcal.app.science.Sex
import kotlin.math.roundToInt

data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Int = 170,
    /** Latest known weight; updated from weigh-ins. */
    val weightKg: Double = 70.0,
    /** 0 = unknown; when set, BMR uses Cunningham (FFM-based) averaged with Mifflin-St Jeor. */
    val bodyFatPct: Double = 0.0,
    /** 0 = compute; >0 = measured (e.g. indirect calorimetry). */
    val bmrOverride: Int = 0,
    val pal: Double = ActivityLevel.SEDENTARY.pal,
    val targetDeficit: Int = Pace.STANDARD.dailyDeficit,
    /** 0 = not set. */
    val goalWeightKg: Double = 0.0,
) {
    val bmr: Int
        get() {
            if (bmrOverride > 0) return bmrOverride
            val msj = Energy.mifflinStJeor(sex, weightKg, heightCm.toDouble(), age)
            if (bodyFatPct <= 0) return msj.roundToInt()
            // No FFM equation is clearly better in Chinese samples (Xue 2019), so average rather than replace.
            val ffm = weightKg * (1 - bodyFatPct / 100)
            return ((msj + Energy.cunningham(ffm)) / 2).roundToInt()
        }

    /** Measured body fat if known, else the Deurenberg 1991 BMI estimate. */
    val fatMassKg: Double
        get() {
            val pct = if (bodyFatPct > 0) bodyFatPct
            else Energy.bodyFatPercentFromBmi(sex, Energy.bmi(weightKg, heightCm.toDouble()), age)
            return weightKg * pct / 100
        }
}

/** A paired Xiaomi scale: its BLE address and the 16-byte bindkey from the owner's Xiaomi account. */
data class ScaleConfig(val mac: String, val bindkeyHex: String, val profileId: Int = 0)

/** Plain SharedPreferences; API keys stay on-device (backup is disabled in the manifest). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("snapcal", Context.MODE_PRIVATE)

    var provider: Provider
        get() = runCatching { Provider.valueOf(prefs.getString("provider", null)!!) }.getOrDefault(Provider.ANTHROPIC)
        set(v) = prefs.edit().putString("provider", v.name).apply()

    fun apiKey(p: Provider): String = prefs.getString("key_${p.name}", "")!!
    fun setApiKey(p: Provider, v: String) = prefs.edit().putString("key_${p.name}", v.trim()).apply()

    fun model(p: Provider): String = prefs.getString("model_${p.name}", null)?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun setModel(p: Provider, v: String) = prefs.edit().putString("model_${p.name}", v.trim()).apply()

    /** Optional proxy / compatible endpoint; blank means the provider's official URL. */
    fun baseUrl(p: Provider): String = prefs.getString("base_${p.name}", "")!!
    fun setBaseUrl(p: Provider, v: String) = prefs.edit().putString("base_${p.name}", v.trim().trimEnd('/')).apply()

    /** False until the first-run plan (body data + goal) has been completed. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Epoch day the user started tracking (set at onboarding); used to skip the initial water-loss phase. */
    var trackingStartEpochDay: Long
        get() = prefs.getLong("tracking_start", java.time.LocalDate.now().toEpochDay())
        set(v) = prefs.edit().putLong("tracking_start", v).apply()

    var scale: ScaleConfig?
        get() {
            val mac = prefs.getString("scale_mac", null) ?: return null
            val key = prefs.getString("scale_key", null) ?: return null
            return ScaleConfig(mac, key, prefs.getInt("scale_profile", 0))
        }
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove("scale_mac").remove("scale_key").remove("scale_profile")
            else e.putString("scale_mac", v.mac.uppercase()).putString("scale_key", v.bindkeyHex.lowercase()).putInt("scale_profile", v.profileId)
            e.apply()
        }

    var healthSync: Boolean
        get() = prefs.getBoolean("health_sync", true)
        set(v) = prefs.edit().putBoolean("health_sync", v).apply()

    var profile: Profile
        get() = Profile(
            sex = runCatching { Sex.valueOf(prefs.getString("sex", "MALE")!!) }.getOrDefault(Sex.MALE),
            age = prefs.getInt("age", 30),
            heightCm = prefs.getInt("height", 170),
            weightKg = prefs.getFloat("weight", 70f).toDouble(),
            bodyFatPct = prefs.getFloat("body_fat", 0f).toDouble(),
            bmrOverride = prefs.getInt("bmr_override", 0),
            pal = prefs.getFloat("pal", ActivityLevel.SEDENTARY.pal.toFloat()).toDouble(),
            targetDeficit = prefs.getInt("target_deficit", Pace.STANDARD.dailyDeficit),
            goalWeightKg = prefs.getFloat("goal_weight", 0f).toDouble(),
        )
        set(v) = prefs.edit()
            .putString("sex", v.sex.name)
            .putInt("age", v.age)
            .putInt("height", v.heightCm)
            .putFloat("weight", v.weightKg.toFloat())
            .putFloat("body_fat", v.bodyFatPct.toFloat())
            .putInt("bmr_override", v.bmrOverride)
            .putFloat("pal", v.pal.toFloat())
            .putInt("target_deficit", v.targetDeficit)
            .putFloat("goal_weight", v.goalWeightKg.toFloat())
            .apply()
}
