package com.snapcal.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapcal.app.SnapCalApp
import com.snapcal.app.data.DayTotal
import com.snapcal.app.data.EnergyBalance
import com.snapcal.app.data.Plan
import com.snapcal.app.data.WeightEntry
import com.snapcal.app.science.AdaptiveTdee
import com.snapcal.app.scale.ScaleScanner
import com.snapcal.app.science.DailyBurn
import com.snapcal.app.science.PhotoCalibration
import com.snapcal.app.data.Outlook
import com.snapcal.app.data.Meal
import com.snapcal.app.util.Days
import com.snapcal.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Capture : Screen
    data class MealDetail(val id: Long) : Screen
    data object History : Screen
    data object Settings : Screen
    data object Onboarding : Screen
    data object Weight : Screen
    data object Scale : Screen
}

data class CaptureState(
    val photos: List<ByteArray> = emptyList(),
    val note: String = "",
    val analyzing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val snapApp = app as SnapCalApp
    val settings = snapApp.settings
    val repo = snapApp.repository

    private val backStack = MutableStateFlow<List<Screen>>(
        listOf(if (settings.onboarded) Screen.Home else Screen.Onboarding),
    )
    val screen: StateFlow<Screen> = backStack.map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, backStack.value.last())

    /** After onboarding: first run lands on Home; a re-plan from Settings returns there. */
    fun finishOnboarding() = backStack.update { stack ->
        val rest = stack.dropLast(1)
        rest.ifEmpty { listOf(Screen.Home) }
    }

    fun navigate(s: Screen) = backStack.update { it + s }
    fun canGoBack(): Boolean = backStack.value.size > 1

    fun back(): Boolean {
        if (backStack.value.size <= 1) return false
        backStack.update { it.dropLast(1) }
        return true
    }
    private fun replaceTop(s: Screen) = backStack.update { it.dropLast(1) + s }

    /** Which day the home screen is showing. */
    val selectedDay = MutableStateFlow(Days.today())

    val meals: StateFlow<List<Meal>> = selectedDay
        .flatMapLatest { repo.mealsForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayTotals: StateFlow<List<DayTotal>> = repo.dayTotals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val profileVersion = MutableStateFlow(0)

    val weights: StateFlow<List<WeightEntry>> = repo.weights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _adaptive = MutableStateFlow<AdaptiveTdee.Result?>(null)
    val adaptive: StateFlow<AdaptiveTdee.Result?> = _adaptive.asStateFlow()

    /** Current plan, using the adaptive TDEE once there is enough data. */
    val plan: StateFlow<Plan> = combine(_adaptive, profileVersion) { a, _ -> Plan.of(settings.profile, a?.tdee) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Plan.of(settings.profile))

    private val burn = MutableStateFlow<DailyBurn?>(null)

    private val _calibration = MutableStateFlow(PhotoCalibration.estimate(emptyList()))
    val calibration: StateFlow<PhotoCalibration.Result> = _calibration.asStateFlow()

    val balance: StateFlow<EnergyBalance?> = combine(meals, burn, plan, _calibration) { m, b, p, c ->
        val f = if (c.active) c.factor else 1.0
        b?.let { EnergyBalance(m.sumOf { meal -> repo.calibratedKcal(meal, f) }, it, p, m.sumOf { it.totalKcal }, f) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _outlook = MutableStateFlow<Outlook?>(null)
    val outlook: StateFlow<Outlook?> = _outlook.asStateFlow()

    /** Recompute adaptive TDEE and the selected day's burn (cheap; called on resume, day change, new data). */
    fun refreshEnergy() {
        viewModelScope.launch {
            runCatching { repo.importWeightsFromHealth() }
            val prior = Plan.of(settings.profile).tdeePrior
            _adaptive.value = runCatching { repo.adaptiveTdee(prior) }.getOrNull()
            _calibration.value = repo.photoCalibration()
            val p = Plan.of(settings.profile, _adaptive.value?.tdee)
            burn.value = repo.dailyBurn(Days.parse(selectedDay.value), p)
            _outlook.value = runCatching { repo.outlook(p) }.getOrNull()
            runCatching { repo.snapshotToday(p, burn.value) }
        }
    }

    // ---- scale ----

    val scaleScanner = ScaleScanner(app)

    /** Foreground scan while the app is visible; optionally (re)arm the background PendingIntent scan. */
    fun startScaleScan(background: Boolean = false) {
        if (!scaleScanner.hasPermissions()) return
        val bridge = snapApp.scale
        scaleScanner.startForeground { addr, data -> viewModelScope.launch { bridge.onAdvertisement(addr, data) } }
        if (background && settings.scale != null) scaleScanner.startBackground()
        viewModelScope.launch {
            // Socks / weight-only weigh-ins complete after a quiet period.
            while (true) {
                kotlinx.coroutines.delay(2000)
                bridge.flushPending()
            }
        }.also { flushJob?.cancel(); flushJob = it }
    }

    private var flushJob: kotlinx.coroutines.Job? = null

    fun stopScaleScan() {
        scaleScanner.stopForeground()
        flushJob?.cancel()
    }

    init {
        viewModelScope.launch {
            snapApp.scale.status.collect { if (it is com.snapcal.app.scale.ScaleBridge.Status.Saved) profileChanged() }
        }
    }

    override fun onCleared() {
        stopScaleScan()
    }

    fun logWeight(kg: Double) = viewModelScope.launch {
        repo.logWeight(kg)
        profileChanged()
    }

    fun deleteWeight(day: String) = viewModelScope.launch {
        repo.deleteWeight(day)
        profileChanged()
    }

    fun selectDay(day: String) {
        selectedDay.value = day
        refreshEnergy()
    }

    fun profileChanged() {
        profileVersion.update { it + 1 }
        refreshEnergy()
    }

    // ---- capture flow ----

    private val _capture = MutableStateFlow(CaptureState())
    val capture: StateFlow<CaptureState> = _capture.asStateFlow()

    fun startCapture() {
        _capture.value = CaptureState()
        navigate(Screen.Capture)
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { Images.loadForUpload(getApplication(), uri) }
                    .onFailure { Log.w(TAG, "Failed to load $uri", it) }
                    .getOrNull()
            }
            if (bytes == null) {
                _capture.update { it.copy(error = "这张照片读取失败，换一张试试") }
                return@launch
            }
            _capture.update { it.copy(photos = it.photos + bytes, error = null) }
        }
    }

    fun removePhoto(index: Int) = _capture.update { s -> s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }) }
    fun setNote(note: String) = _capture.update { it.copy(note = note) }

    fun analyze() {
        val s = _capture.value
        if (s.photos.isEmpty() || s.analyzing) return
        _capture.update { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.analyzeAndSave(s.photos, s.note) }
                .onSuccess { id ->
                    _capture.value = CaptureState()
                    Images.clearCaptureCache(getApplication())
                    selectDay(Days.today())
                    replaceTop(Screen.MealDetail(id))
                }
                .onFailure { e -> _capture.update { it.copy(analyzing = false, error = e.message ?: e.toString()) } }
        }
    }

    // ---- meal edit ----

    private val _reanalyzing = MutableStateFlow(false)
    val reanalyzing: StateFlow<Boolean> = _reanalyzing.asStateFlow()
    private val _reanalyzeError = MutableStateFlow<String?>(null)
    val reanalyzeError: StateFlow<String?> = _reanalyzeError.asStateFlow()

    fun reanalyze(meal: Meal, hint: String) {
        if (_reanalyzing.value) return
        _reanalyzing.value = true
        _reanalyzeError.value = null
        viewModelScope.launch {
            runCatching { repo.reanalyze(meal, hint) }
                .onFailure { _reanalyzeError.value = it.message ?: it.toString() }
            _reanalyzing.value = false
        }
    }

    fun saveMeal(meal: Meal) = viewModelScope.launch { repo.update(meal); refreshEnergy() }
    fun setVerified(meal: Meal, verified: Boolean) = viewModelScope.launch { repo.setVerified(meal, verified); refreshEnergy() }
    fun deleteMeal(meal: Meal) = viewModelScope.launch {
        repo.delete(meal)
        back()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
