package com.snapcal.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.snapcal.app.ai.Provider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    var provider by remember { mutableStateOf(s.provider) }
    var apiKey by remember(provider) { mutableStateOf(s.apiKey(provider)) }
    var model by remember(provider) { mutableStateOf(s.model(provider)) }
    var baseUrl by remember(provider) { mutableStateOf(s.baseUrl(provider)) }
    var showKey by remember { mutableStateOf(false) }

    val p0 = remember { s.profile }
    var bmrOverride by remember { mutableStateOf(if (p0.bmrOverride > 0) p0.bmrOverride.toString() else "") }

    fun saveAll() {
        s.provider = provider
        s.setApiKey(provider, apiKey)
        s.setModel(provider, model)
        s.setBaseUrl(provider, baseUrl)
        s.profile = s.profile.copy(bmrOverride = bmrOverride.toIntOrNull()?.takeIf { it in 800..5000 } ?: 0)
        vm.profileChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = { saveAll(); vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI 模型", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Provider.entries.forEach { p ->
                    FilterChip(
                        selected = provider == p,
                        onClick = {
                            // Persist the current provider's fields before switching tabs.
                            s.setApiKey(provider, apiKey); s.setModel(provider, model); s.setBaseUrl(provider, baseUrl)
                            provider = p
                        },
                        label = { Text(p.label) },
                    )
                }
            }
            OutlinedTextField(
                apiKey, { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text(provider.keyHint) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.suggestedModels.forEach { m -> SuggestionChip(onClick = { model = m }, label = { Text(m) }) }
            }
            OutlinedTextField(
                baseUrl, { baseUrl = it },
                label = { Text("自定义 Base URL（可选，代理用）") },
                placeholder = { Text(provider.defaultBaseUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Key 只保存在这台手机里，不会上传到任何地方（除了发给你选的 AI 服务商）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            HealthSection(vm)

            HorizontalDivider()
            Text("我的计划", style = MaterialTheme.typography.titleMedium)
            val currentPlan by vm.plan.collectAsState()
            PlanSummary(currentPlan)
            OutlinedButton(onClick = { saveAll(); vm.navigate(Screen.Onboarding) }, modifier = Modifier.fillMaxWidth()) {
                Text("重新制定计划（体重变了就来更新）")
            }
            NumField(bmrOverride, { bmrOverride = it }, "高级：实测 BMR（间接测热法，留空自动算）", Modifier.fillMaxWidth())

            HorizontalDivider()
            Text("数据", style = MaterialTheme.typography.titleMedium)
            Text(
                "所有原始数据都会保留：每次 AI 的原始返回、你改过之前的估计、每次上秤的读数（含阻抗）、删除的记录、每天的计算快照。",
                style = MaterialTheme.typography.bodySmall,
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val app = vm.getApplication<com.snapcal.app.SnapCalApp>()
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.exporter.export() }
                        ctx.startActivity(android.content.Intent.createChooser(app.exporter.shareIntent(f), "导出数据"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出全部数据（CSV）") }

            Button(onClick = { saveAll(); vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}

@Composable
private fun HealthSection(vm: MainViewModel) {
    val health = vm.repo.health
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var sync by remember { mutableStateOf(vm.settings.healthSync) }
    val status = remember { health.sdkStatus() }

    val launcher = rememberLauncherForActivityResult(health.permissionContract()) { result ->
        granted = result.containsAll(health.permissions)
        vm.refreshEnergy()
    }
    LaunchedEffect(Unit) { granted = runCatching { health.hasAllPermissions() }.getOrDefault(false) }

    Text("Health Connect 联动", style = MaterialTheme.typography.titleMedium)
    when (status) {
        HealthConnectClient.SDK_AVAILABLE -> {
            Text(
                "读取手表 / 运动 App 的活动消耗和体脂秤的体重，并把饮食和体重写回 Health Connect。" +
                    "手表的热量数值误差常在 20% 以上，且运动消耗会被身体部分代偿，所以只把「比你平时多出的活动」按一半计入当天预算。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (granted == true) {
                Text("已授权 ✓", color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = { launcher.launch(health.permissions) }) { Text("授权 Health Connect") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("把饮食记录写入 Health Connect", Modifier.weight(1f))
                Switch(sync, { sync = it; vm.settings.healthSync = it })
            }
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            Text("需要在 Play 商店安装或更新 Health Connect。", style = MaterialTheme.typography.bodySmall)
        else ->
            Text("这台设备不支持 Health Connect，消耗将按基础代谢估算。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value,
        { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
