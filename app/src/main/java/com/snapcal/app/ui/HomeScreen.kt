package com.snapcal.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapcal.app.science.AdaptiveTdee
import com.snapcal.app.science.BurnSource
import com.snapcal.app.data.EnergyBalance
import com.snapcal.app.data.Meal
import com.snapcal.app.data.Outlook
import com.snapcal.app.data.items
import com.snapcal.app.data.photos
import com.snapcal.app.util.Days
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val day by vm.selectedDay.collectAsState()
    val meals by vm.meals.collectAsState()
    val balance by vm.balance.collectAsState()
    val adaptive by vm.adaptive.collectAsState()
    val weights by vm.weights.collectAsState()
    val outlook by vm.outlook.collectAsState()
    val calibration by vm.calibration.collectAsState()

    LaunchedEffect(Unit) { vm.refreshEnergy() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍热量") },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.History) }) { Icon(Icons.Default.DateRange, "历史") }
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
        bottomBar = {
            // The two primary user paths, equally prominent.
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { vm.startCapture() }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("拍一顿", fontSize = 18.sp)
                }
                FilledTonalButton(onClick = { vm.navigate(Screen.Weight) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Icon(Icons.Default.MonitorWeight, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("秤一下", fontSize = 18.sp)
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DaySwitcher(day, onSelect = vm::selectDay)
            }
            balance?.let { b -> item { BalanceCard(b, adaptive) } }
            if (Days.parse(day) == LocalDate.now()) {
                item {
                    BodyCard(
                        outlook = outlook,
                        weighedToday = weights.any { it.day == day },
                        scaleConnected = vm.settings.scale != null,
                        onClick = { vm.navigate(Screen.Weight) },
                    )
                }
            }
            if (vm.settings.apiKey(vm.settings.provider).isBlank()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Settings) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("还差一步：选一个 AI 并填 API Key", style = MaterialTheme.typography.titleSmall)
                            Text("支持 Claude / GPT / Gemini / Mistral / DeepSeek，点这里去设置", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (meals.isEmpty()) {
                item {
                    Text(
                        "还没有饮食记录。点下面「拍一顿」拍张照片就行。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(meals, key = { it.id }) { meal ->
                MealRow(meal) { vm.navigate(Screen.MealDetail(meal.id)) }
            }
        }
    }
}

@Composable
private fun DaySwitcher(day: String, onSelect: (String) -> Unit) {
    val d = Days.parse(day)
    val isToday = d == LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onSelect(d.minusDays(1).toString()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前一天")
        }
        Text(
            Days.label(day),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).clickable { onSelect(Days.today()) },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onSelect(d.plusDays(1).toString()) }, enabled = !isToday) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "后一天")
        }
    }
}

@Composable
fun BalanceCard(b: EnergyBalance, adaptive: AdaptiveTdee.Result?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(if (b.remaining >= 0) "今天还能吃" else "今天已超出", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${kotlin.math.abs(b.remaining)}", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            }
            LinearProgressIndicator(
                progress = { (b.intakeKcal.toFloat() / b.budgetKcal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("已摄入", "${b.intakeKcal}", Modifier.weight(1f))
                Stat("今日预算", "${b.budgetKcal}", Modifier.weight(1f))
                // Before anything is logged the running deficit is just "everything", which is meaningless;
                // show the planned deficit instead.
                if (b.intakeKcal == 0) {
                    Stat("计划缺口", "${b.plan.deficit}", Modifier.weight(1f), highlight = true)
                } else {
                    Stat(
                        if (b.deficit >= 0) "当前缺口" else "热量盈余",
                        "${kotlin.math.abs(b.deficit)}",
                        Modifier.weight(1f),
                        highlight = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val burnLine = buildString {
                append("消耗 ≈ ${b.burnedKcal}：维持热量 ${b.burn.baselineKcal}")
                if (b.burn.activityAdjustmentKcal != 0) {
                    append(if (b.burn.activityAdjustmentKcal > 0) " + 额外活动 " else " − 活动偏少 ")
                    append(kotlin.math.abs(b.burn.activityAdjustmentKcal))
                }
            }
            Text(burnLine, style = MaterialTheme.typography.bodySmall)
            if (b.photoFactor != 1.0 && b.rawIntakeKcal != b.intakeKcal) {
                Text(
                    "摄入已按拍照校准系数 ×${String.format(java.util.Locale.US, "%.2f", b.photoFactor)} 修正（记录值 ${b.rawIntakeKcal}）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val sourceLine = when (b.burn.baselineSource) {
                BurnSource.ADAPTIVE -> "维持热量已根据你 ${adaptive?.loggedDays ?: 0} 天记录和 ${adaptive?.weighIns ?: 0} 次称重校准" +
                    (adaptive?.let { "（可信度 ${(it.confidence * 100).toInt()}%）" } ?: "")
                BurnSource.PRIOR -> "维持热量是公式估算（个体误差约 ±${b.plan.tdeeSd}），坚持记录 + 每天称重约 4 周后会自动校准"
            }
            Text(
                sourceLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun MealRow(meal: Meal, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MealThumb(meal.photos.firstOrNull(), Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    Days.timeOf(meal.eatenAt) + " · " + meal.items.joinToString("、") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("${meal.totalKcal}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(" kcal", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 秤一下: body change and diet effect — what the scale says vs. what the logged diet predicts. */
@Composable
private fun BodyCard(outlook: Outlook?, weighedToday: Boolean, scaleConnected: Boolean, onClick: () -> Unit) {
    fun f1(d: Double) = String.format(java.util.Locale.US, "%.1f", d)
    fun f2s(d: Double) = String.format(java.util.Locale.US, "%+.2f", d)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("身体变化", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (!weighedToday) {
                    Text(
                        if (scaleConnected) "今天还没称 · 光脚上秤即可" else "今天还没称",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val o = outlook
            if (o?.latestKg == null) {
                Text("还没有体重记录。每天早上空腹称一次，用来计算饮食效果并校准真实消耗。", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(f1(o.trendKg ?: o.latestKg), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(" kg 趋势", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text("最近一次 ${f1(o.latestKg)}", style = MaterialTheme.typography.bodySmall)
            }
            if (o.actualKgPerWeek != null) {
                Text("实际变化：${f2s(o.actualKgPerWeek)} kg/周（近 ${o.weighIns} 次称重）", style = MaterialTheme.typography.bodyMedium)
            }
            if (o.expectedKgPerWeek != null && o.avgLoggedDeficit != null) {
                Text(
                    "按记录的饮食应为：${f2s(o.expectedKgPerWeek)} kg/周（日均缺口 ${o.avgLoggedDeficit} kcal，${o.loggedDays} 天）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (o.actualKgPerWeek != null) {
                    val gap = o.actualKgPerWeek - o.expectedKgPerWeek
                    Text(
                        when {
                            o.weighIns < 14 -> "数据还少，前两周体重主要是水分变化，先看趋势。"
                            gap > 0.25 -> "比预期减得慢：可能有漏记或份量估低了，维持热量会据此自动下调。"
                            gap < -0.25 -> "比预期减得快：维持热量可能被低估，会据此自动上调。"
                            else -> "实际与记录基本一致。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            o.projected12wKg?.let {
                Text("照现在的计划，12 周后约 ${f1(it)} kg（动态模型）", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
