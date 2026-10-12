package com.snapcal.app.scale

import android.util.Log
import com.snapcal.app.data.MealRepository
import com.snapcal.app.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Connects decoded scale advertisements to storage. Shared by the foreground scanner and the background receiver. */
class ScaleBridge(private val settings: Settings, private val repo: MealRepository) {
    sealed interface Status {
        data object Idle : Status
        data class Measuring(val massKg: Double?) : Status
        data class Saved(val weighIn: WeighIn) : Status
        data object WrongKey : Status
        data class OtherProfile(val profileId: Int) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Latest decoded frame of any scale nearby (used by the pairing screen to find the MAC). */
    private val _seen = MutableStateFlow<Pair<String, MiBeacon.Model>?>(null)
    val seen: StateFlow<Pair<String, MiBeacon.Model>?> = _seen.asStateFlow()

    private val assembler = WeighInAssembler()
    private val lock = Mutex()

    suspend fun onAdvertisement(address: String, serviceData: ByteArray) = lock.withLock {
        val cfg = settings.scale
        val key = cfg?.let { MiBeacon.parseKey(it.bindkeyHex) }
        val isOurs = cfg != null && cfg.mac.equals(address, ignoreCase = true)
        MiBeacon.modelOf(serviceData)?.let { _seen.value = address to it }
        if (!isOurs) return@withLock
        when (val r = MiBeacon.parse(serviceData, address, key)) {
            is MiBeacon.Result.Ok -> {
                for (f in r.frames) {
                    if (f !is MiBeacon.BodyComposition) continue
                    if (cfg!!.profileId != 0 && f.profileId != cfg.profileId && !f.isReset) {
                        _status.value = Status.OtherProfile(f.profileId)
                        // Still assembled and kept as a raw reading, marked "other_profile".
                        assembler.accept(f)?.let { repo.logOtherProfileReading(it) }
                        continue
                    }
                    if (!f.isReset) _status.value = Status.Measuring(f.massKg ?: (status.value as? Status.Measuring)?.massKg)
                    assembler.accept(f)?.let { save(it) }
                }
            }
            MiBeacon.Result.BadKey -> _status.value = Status.WrongKey
            MiBeacon.Result.NoPayload, MiBeacon.Result.NotAScale -> Unit
        }
    }

    suspend fun flushPending() = lock.withLock { assembler.flush()?.let { save(it) } }

    private suspend fun save(w: WeighIn) {
        runCatching { repo.logWeighIn(w) }
            .onSuccess { _status.value = Status.Saved(w) }
            .onFailure { Log.w("ScaleBridge", "save failed", it) }
    }
}
