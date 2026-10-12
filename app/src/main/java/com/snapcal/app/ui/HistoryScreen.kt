package com.snapcal.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapcal.app.data.Plan
import com.snapcal.app.util.Days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val totals by vm.dayTotals.collectAsState()
    // Without per-day burn history, compare against the plan's daily intake target.
    val target = Plan.of(vm.settings.profile).dailyIntakeTarget

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("每日记录") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            if (totals.isNotEmpty()) {
                item {
                    val last7 = totals.take(7)
                    Text(
                        "最近 ${last7.size} 天平均摄入 ${last7.sumOf { it.totalKcal } / last7.size} kcal（参考目标 ≤ $target）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            } else {
                item { Text("还没有记录") }
            }
            items(totals, key = { it.day }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.selectDay(d.day); vm.back() }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Days.label(d.day), style = MaterialTheme.typography.titleMedium)
                        Text("${d.mealCount} 顿", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${d.totalKcal} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (d.totalKcal > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
