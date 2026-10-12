package com.snapcal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapcal.app.ai.Provider
import com.snapcal.app.data.FoodItem
import com.snapcal.app.data.Meal
import com.snapcal.app.data.encodeItems
import com.snapcal.app.data.items
import com.snapcal.app.data.photos
import com.snapcal.app.science.PhotoCalibration
import com.snapcal.app.util.Days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(vm: MainViewModel, id: Long) {
    val meal by remember(id) { vm.repo.observe(id) }.collectAsState(initial = null)
    val m = meal ?: return
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Days.label(m.day) + " " + Days.timeOf(m.eatenAt)) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除") } },
            )
        },
    ) { pad ->
        MealEditor(m, Modifier.padding(pad), vm, onSave = { vm.saveMeal(it); vm.back() })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这一顿？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteMeal(m) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

private class EditableItem(item: FoodItem) {
    var name by mutableStateOf(item.name)
    var portion by mutableStateOf(item.portion)
    var kcal by mutableStateOf(item.kcal.toString())
    val original = item

    fun toItem(): FoodItem? {
        val k = kcal.trim().toIntOrNull() ?: return null
        if (name.isBlank()) return null
        // Scale macros along with kcal edits so they stay roughly consistent.
        val ratio = if (original.kcal > 0) k.toDouble() / original.kcal else 1.0
        return original.copy(
            name = name.trim(),
            portion = portion.trim(),
            kcal = k,
            proteinG = original.proteinG?.times(ratio),
            carbsG = original.carbsG?.times(ratio),
            fatG = original.fatG?.times(ratio),
        )
    }
}

@Composable
private fun MealEditor(meal: Meal, modifier: Modifier, vm: MainViewModel, onSave: (Meal) -> Unit) {
    var title by remember(meal.id) { mutableStateOf(meal.title) }
    val items = remember(meal.id) { mutableStateListOf(*meal.items.map(::EditableItem).toTypedArray()) }
    val total = items.sumOf { it.kcal.trim().toIntOrNull() ?: 0 }
    var verified by remember(meal.id) { mutableStateOf(meal.verified) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (meal.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(meal.photos) { MealThumb(it, Modifier.size(160.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            if (meal.confidence.isNotBlank()) {
                Text(
                    "可信度：" + when (meal.confidence) { "high" -> "高"; "medium" -> "中"; "low" -> "低"; else -> meal.confidence },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Text("明细（数字不对直接改）", style = MaterialTheme.typography.titleSmall)
        items.forEachIndexed { i, it ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(it.name, { v -> it.name = v }, label = { Text("食物") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(it.portion, { v -> it.portion = v }, label = { Text("分量") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        it.kcal,
                        { v -> it.kcal = v.filter(Char::isDigit) },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                    IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.RemoveCircleOutline, "删除此项") }
                }
            }
        }
        TextButton(onClick = { items.add(EditableItem(FoodItem(name = "", portion = "", kcal = 0))) }) {
            Icon(Icons.Default.Add, null)
            Text("加一项")
        }

        Text(
            "照片估算单顿误差通常在 ±25–35%（研究中最好的模型也是这个水平），主要来自分量判断。" +
                "大致范围 ${(total * 0.7).toInt()}–${(total * 1.3).toInt()} kcal。长期趋势比单顿精确更重要。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (meal.aiQuestion.isNotBlank()) {
            FollowUp(meal.aiQuestion, busy = vm.reanalyzing.collectAsState().value, error = vm.reanalyzeError.collectAsState().value) { answer ->
                vm.reanalyze(meal, "${meal.aiQuestion} 回答：$answer")
            }
        }
        if (meal.aiNotes.isNotBlank()) {
            Text("AI 说明：" + meal.aiNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (meal.note.isNotBlank()) {
            Text("你的补充：" + meal.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val providerLabel = runCatching { Provider.valueOf(meal.provider).label }.getOrDefault(meal.provider)
        if (meal.model.isNotBlank()) {
            Text("由 $providerLabel · ${meal.model} 估算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("这是准确值（看了营养成分表 / 称过重）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "勾选后用来校准拍照估算：AI 原始估计 ${meal.aiKcal} kcal → 准确值 $total kcal。" +
                            "攒够 ${PhotoCalibration.MIN_SAMPLES} 顿后自动生效。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(verified, { verified = it })
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val newItems = items.mapNotNull { it.toItem() }
                onSave(meal.copy(title = title.trim().ifBlank { meal.title }, itemsJson = encodeItems(newItems), verified = verified))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("保存") }
    }
}

@Composable
private fun FollowUp(question: String, busy: Boolean, error: String?, onAnswer: (String) -> Unit) {
    var answer by remember(question) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI 想确认一下（可跳过）", style = MaterialTheme.typography.labelLarge)
            Text(question, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(answer, { answer = it }, singleLine = true, modifier = Modifier.weight(1f), enabled = !busy)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAnswer(answer.trim()) }, enabled = answer.isNotBlank() && !busy) {
                    Text(if (busy) "重算中…" else "重算")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
