package com.snapcal.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapcal.app.data.Pace
import com.snapcal.app.data.Plan
import com.snapcal.app.data.Profile
import com.snapcal.app.science.ActivityLevel
import com.snapcal.app.science.Sex
import java.util.Locale

/**
 * First-run plan: body data → activity → goal, ending with BMR / TDEE / daily intake target.
 * Also reachable from Settings ("重新制定计划").
 */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val p0 = remember { vm.settings.profile }
    val firstRun = remember { !vm.settings.onboarded }
    var step by rememberSaveable { mutableIntStateOf(0) }

    var sex by rememberSaveable { mutableStateOf(p0.sex) }
    var age by rememberSaveable { mutableStateOf(if (firstRun) "" else p0.age.toString()) }
    var height by rememberSaveable { mutableStateOf(if (firstRun) "" else p0.heightCm.toString()) }
    var weight by rememberSaveable { mutableStateOf(if (firstRun) "" else fmt(p0.weightKg)) }
    var bodyFat by rememberSaveable { mutableStateOf(if (p0.bodyFatPct > 0) fmt(p0.bodyFatPct) else "") }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.nearest(p0.pal)) }
    var goalWeight by rememberSaveable { mutableStateOf(if (p0.goalWeightKg > 0) fmt(p0.goalWeightKg) else "") }
    var pace by rememberSaveable { mutableStateOf(Pace.nearest(p0.targetDeficit)) }

    val ageN = age.toIntOrNull()?.takeIf { it in 12..100 }
    val heightN = height.toIntOrNull()?.takeIf { it in 100..250 }
    val weightN = weight.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
    val goalN = goalWeight.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
    val bodyFatN = bodyFat.toDoubleOrNull()?.takeIf { it in 3.0..65.0 }

    fun profile() = p0.copy(
        sex = sex,
        age = ageN ?: p0.age,
        heightCm = heightN ?: p0.heightCm,
        weightKg = weightN ?: p0.weightKg,
        bodyFatPct = bodyFatN ?: 0.0,
        pal = activity.pal,
        targetDeficit = pace.dailyDeficit,
        goalWeightKg = goalN ?: 0.0,
        // A fresh plan recomputes BMR from body data.
        bmrOverride = 0,
    )

    fun finish() {
        vm.settings.profile = profile()
        if (firstRun) vm.settings.trackingStartEpochDay = java.time.LocalDate.now().toEpochDay()
        // Onboarding weight doubles as today's weigh-in.
        weightN?.let { vm.logWeight(it) }
        vm.settings.onboarded = true
        vm.profileChanged()
        vm.finishOnboarding()
    }

    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            when (step) {
                0 -> {
                    Header("先认识一下你", "用来估算你每天大概消耗多少热量。数据只存在手机里。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(sex == Sex.MALE, { sex = Sex.MALE }, label = { Text("男") })
                        FilterChip(sex == Sex.FEMALE, { sex = Sex.FEMALE }, label = { Text("女") })
                    }
                    Field(age, { age = it }, "年龄", "岁", isError = age.isNotEmpty() && ageN == null)
                    Field(height, { height = it }, "身高", "cm", isError = height.isNotEmpty() && heightN == null)
                    Field(weight, { weight = it }, "现在体重", "kg", decimal = true, isError = weight.isNotEmpty() && weightN == null)
                    Field(bodyFat, { bodyFat = it }, "体脂率（可选，体脂秤/DEXA）", "%", decimal = true, isError = bodyFat.isNotEmpty() && bodyFatN == null)
                    Spacer(Modifier.weight(1f, fill = false))
                    Button(
                        onClick = { step = 1 },
                        enabled = ageN != null && heightN != null && weightN != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("下一步") }
                }
                1 -> {
                    Header(
                        "平时活动量",
                        "研究发现大多数人会高估自己的活动量，不确定就选“久坐”。手表记录的额外运动会按一半计入当天预算。",
                    )
                    ActivityLevel.entries.forEach { level ->
                        ChoiceCard(
                            selected = activity == level,
                            title = level.label,
                            subtitle = level.hint,
                            trailing = "${(profile().bmr * level.pal).toInt()} kcal/天",
                            onClick = { activity = level },
                        )
                    }
                    Nav(onBack = { step = 0 }, onNext = { step = 2 })
                }
                else -> {
                    Header("目标", "指南推荐每天 500–750 kcal 的缺口。速度越快越难坚持，也更容易掉肌肉。")
                    Field(goalWeight, { goalWeight = it }, "目标体重（可选）", "kg", decimal = true, isError = goalWeight.isNotEmpty() && goalN == null)
                    Pace.entries.forEach { p ->
                        ChoiceCard(
                            selected = pace == p,
                            title = "${p.label} · 每天少吃 ${p.dailyDeficit} kcal",
                            subtitle = "12 周后约 −${fmt1(Plan.of(profile().copy(targetDeficit = p.dailyDeficit)).kgIn12Weeks)} kg",
                            onClick = { pace = p },
                        )
                    }
                    PlanSummary(Plan.of(profile()))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f).height(52.dp)) { Text("上一步") }
                        Button(onClick = ::finish, modifier = Modifier.weight(2f).height(52.dp)) {
                            Text(if (firstRun) "开始记录" else "保存计划")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanSummary(plan: Plan) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("你的计划", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${plan.dailyIntakeTarget}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(" kcal / 天", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("每天建议摄入", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SummaryLine("基础代谢（Mifflin-St Jeor）", "${plan.bmr} kcal")
            SummaryLine(
                if (plan.tdeeIsAdaptive) "维持热量（已按体重趋势校准）" else "维持热量（估算 ±${plan.tdeeSd}）",
                "${plan.tdee} kcal",
            )
            SummaryLine("每日热量缺口", "${plan.deficit} kcal")
            SummaryLine("12 周预计", "−${fmt1(plan.kgIn12Weeks)} kg")
            when {
                plan.weeksToGoal != null -> SummaryLine("减 ${fmt1(plan.kgToLose)} kg 大约需要", "${plan.weeksToGoal} 周")
                plan.kgToLose > 0 -> Text(
                    "按这个缺口，体重会在离目标还有一段距离时趋于稳定，需要之后再调整计划。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SummaryLine("建议蛋白质", "${plan.proteinG.first}–${plan.proteinG.last} g/天")
            if (plan.cappedBySafety) {
                Text(
                    "为了安全，缺口不超过维持热量的 30%，摄入不低于 ${plan.intakeFloor} kcal，已自动调低。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "减重会越来越慢：体重下降后消耗也会下降（NIH 动态模型），所以这里不用「7700 kcal = 1 kg」。" +
                    "公式估算有个体误差，坚持记录和每天称重约 4 周后会自动校准。建议每周 2–3 次力量训练以保住肌肉。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    decimal: Boolean = false,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value,
        { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceCard(selected: Boolean, title: String, subtitle: String, trailing: String? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Nav(onBack: () -> Unit, onNext: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text("上一步") }
        Button(onClick = onNext, modifier = Modifier.weight(2f).height(52.dp)) { Text("下一步") }
    }
}

private fun fmt(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

private fun fmt1(d: Double): String = String.format(Locale.US, "%.1f", d)
