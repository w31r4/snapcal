package com.snapcal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snapcal.app.data.Profile
import com.snapcal.app.data.WeightEntry
import com.snapcal.app.science.AdaptiveTdee
import com.snapcal.app.science.BodyComposition
import com.snapcal.app.science.Trend
import com.snapcal.app.util.Days
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: MainViewModel) {
    val weights by vm.weights.collectAsState()
    val adaptive by vm.adaptive.collectAsState()
    val plan by vm.plan.collectAsState()
    var input by remember { mutableStateOf("") }
    val parsed = input.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("体重") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        input,
                        { v -> input = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("今天的体重") },
                        suffix = { Text("kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { parsed?.let { vm.logWeight(it); input = "" } }, enabled = parsed != null) { Text("记录") }
                }
                Text(
                    "每天早上空腹、上完厕所后称一次。单日体重会因水分、盐、糖原波动 1–2 kg，看趋势不看单天。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                val sc = vm.settings.scale
                OutlinedButton(onClick = { vm.navigate(Screen.Scale) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sc == null) "连接米家体脂秤（自动记录）" else "体脂秤已连接 · ${sc.mac.takeLast(5)}")
                }
            }
            if (weights.size >= 2) item { WeightChart(weights.takeLast(90).map { it.day to it.kg }) }
            item { BodyCompCard(weights, vm.settings.profile) }
            item { CalibrationCard(adaptive, plan.tdeePrior, plan.tdee) }
            items(weights.reversed(), key = { it.day }) { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Days.label(w.day))
                        val extra = listOfNotNull(
                            when (w.source) { "scale" -> "体脂秤"; "health_connect" -> "Health Connect"; else -> null },
                            w.impedance50?.let { "阻抗 ${it.toInt()} Ω" },
                            w.heartRate?.let { "心率 $it" },
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(String.format(Locale.US, "%.1f kg", w.kg), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.deleteWeight(w.day) }) { Icon(Icons.Default.Delete, "删除") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CalibrationCard(a: AdaptiveTdee.Result?, prior: Int, current: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("真实消耗校准", style = MaterialTheme.typography.titleMedium)
            if (a == null) {
                Text(
                    "需要至少 ${AdaptiveTdee.MIN_LOGGED_DAYS} 天饮食记录和 ${AdaptiveTdee.MIN_WEIGHINS} 次称重（开始记录的前两周是水分和糖原变化，会跳过）。" +
                        "之后会用「你记录的摄入 − 体重变化对应的能量」反推你的真实维持热量。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("目前使用公式估算：$prior kcal/天", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "体重趋势 ${String.format(Locale.US, "%+.2f", a.trendKgPerWeek)} kg/周，基于 ${a.loggedDays} 天记录、${a.weighIns} 次称重。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("校准后维持热量：$current kcal/天（公式估算 $prior）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "这个数是按「你自己的记录方式」算的：如果拍照估算整体偏低，校准值也会同样偏低，所以目标依然有效。它不是医学测量值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeightChart(points: List<Pair<String, Double>>) {
    val raw = points.map { it.second }
    val trend = Trend.ema(raw)
    val lo = (raw + trend).min() - 0.5
    val hi = (raw + trend).max() + 0.5
    val days = points.map { LocalDate.parse(it.first).toEpochDay() }
    val d0 = days.first()
    val span = (days.last() - d0).coerceAtLeast(1).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "趋势线（指数平滑）${String.format(Locale.US, "%.1f", trend.last())} kg",
                style = MaterialTheme.typography.labelLarge,
            )
            Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp)) {
                fun x(i: Int) = (days[i] - d0) / span * size.width
                fun y(v: Double) = ((hi - v) / (hi - lo) * size.height).toFloat()
                raw.forEachIndexed { i, v -> drawCircle(dot, radius = 4f, center = Offset(x(i), y(v))) }
                val path = Path().apply {
                    trend.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(path, primary, style = Stroke(width = 5f))
            }
        }
    }
}

/**
 * Body fat from foot-to-foot impedance (Wu 2015), shown only as a 7-day mean: single readings shift by
 * ~1–2 %-points with hydration, meals, time of day and foot placement (Oshima 2006; Dixon 2013).
 */
@Composable
private fun BodyCompCard(weights: List<WeightEntry>, profile: Profile) {
    val withZ = weights.filter { it.impedance50 != null }
    if (withZ.isEmpty()) return
    fun estimate(w: WeightEntry) = BodyComposition.wu2015(profile.sex, w.kg, profile.heightCm.toDouble(), profile.age, w.impedance50!!)
    val today = LocalDate.now()
    fun window(fromDaysAgo: Long, toDaysAgo: Long) = withZ.filter {
        val d = LocalDate.parse(it.day)
        !d.isBefore(today.minusDays(fromDaysAgo)) && !d.isAfter(today.minusDays(toDaysAgo))
    }.mapNotNull(::estimate)
    val thisWeek = window(6, 0)
    val prevWeek = window(13, 7)
    val latest = estimate(withZ.last()) ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("身体成分（体脂秤阻抗）", style = MaterialTheme.typography.titleMedium)
            if (thisWeek.isNotEmpty()) {
                val fat = thisWeek.map { it.fatPercent }.average()
                val ffm = thisWeek.map { it.fatFreeMassKg }.average()
                val fm = thisWeek.map { it.fatMassKg }.average()
                Text(
                    String.format(Locale.US, "近 7 天平均（%d 次）：体脂率 %.1f%%，脂肪 %.1f kg，去脂体重 %.1f kg", thisWeek.size, fat, fm, ffm),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (prevWeek.isNotEmpty()) {
                    val dFat = fm - prevWeek.map { it.fatMassKg }.average()
                    val dFfm = ffm - prevWeek.map { it.fatFreeMassKg }.average()
                    Text(
                        String.format(Locale.US, "比前一周：脂肪 %+.1f kg，去脂体重 %+.1f kg", dFat, dFfm) +
                            if (kotlin.math.abs(dFat) < 0.7) "（变化在测量噪声范围内）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                String.format(Locale.US, "体内水分约 %.1f kg（按去脂体重 × 0.73 推算）", latest.bodyWaterKg),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "绝对值误差约 ±${latest.fatPercentSe.toInt()} 个百分点（Wu 2015 公式，脚对脚阻抗），肥胖时体脂率通常偏低 2–3 点，只看趋势。" +
                    "不显示内脏脂肪、骨量、蛋白质率、身体年龄：只用脚测阻抗时电流不经过上半身，这些是厂商估算，不是测量值。" +
                    if (latest.outsideValidatedRange) " 你的数据超出公式验证范围，结果更不确定。" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
