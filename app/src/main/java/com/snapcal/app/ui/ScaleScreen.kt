package com.snapcal.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snapcal.app.SnapCalApp
import com.snapcal.app.data.ScaleConfig
import com.snapcal.app.scale.MiBeacon
import com.snapcal.app.scale.ScaleBridge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleScreen(vm: MainViewModel) {
    val app = vm.getApplication<SnapCalApp>()
    val scanner = vm.scaleScanner
    val seen by app.scale.seen.collectAsState()
    val status by app.scale.status.collectAsState()
    val saved = remember { vm.settings.scale }
    var mac by remember { mutableStateOf(saved?.mac ?: "") }
    var key by remember { mutableStateOf(saved?.bindkeyHex ?: "") }
    var profile by remember { mutableStateOf(saved?.profileId?.takeIf { it > 0 }?.toString() ?: "") }
    var granted by remember { mutableStateOf(scanner.hasPermissions()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = scanner.hasPermissions()
        if (granted) vm.startScaleScan()
    }
    DisposableEffect(Unit) {
        if (granted) vm.startScaleScan()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接体脂秤") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "支持米家体脂秤 S400 / S400 Pro（体重 + 阻抗）和体重秤 S200（仅体重）。" +
                    "app 只在旁边被动接收秤的蓝牙广播，不配对、不连接，米家 App 照常可用。",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!scanner.isSupported) {
                Text("这台设备不支持低功耗蓝牙。", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (!granted) {
                Button(onClick = { permLauncher.launch(scanner.requiredPermissions()) }) { Text("允许扫描附近蓝牙设备") }
            } else if (!scanner.isEnabled) {
                Text("请打开手机蓝牙。", color = MaterialTheme.colorScheme.error)
            }

            Text("第 1 步：找到秤", style = MaterialTheme.typography.titleMedium)
            Text("站上秤让它亮屏，下面会出现它的地址。", style = MaterialTheme.typography.bodySmall)
            seen?.let { (addr, model) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("发现 ${model.label}", style = MaterialTheme.typography.titleSmall)
                        Text(addr, fontFamily = FontFamily.Monospace)
                        if (mac != addr) OutlinedButton(onClick = { mac = addr }) { Text("使用这台") }
                    }
                }
            }
            OutlinedTextField(mac, { mac = it.uppercase() }, label = { Text("蓝牙地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("第 2 步：填入秤的密钥（bindkey）", style = MaterialTheme.typography.titleMedium)
            Text(
                "新款米家秤的广播是加密的，需要这台秤的 32 位密钥。只需取一次：在电脑上运行开源工具 " +
                    "Xiaomi-cloud-tokens-extractor，用米家 App 扫码登录你自己的账号，服务器选 cn，" +
                    "在设备列表里找到体脂秤，复制 BLE KEY。",
                style = MaterialTheme.typography.bodySmall,
            )
            SelectionContainer {
                Text(
                    "git clone https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor\n" +
                        "cd Xiaomi-cloud-tokens-extractor && pip3 install -r requirements.txt\n" +
                        "python3 token_extractor.py",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val keyValid = MiBeacon.parseKey(key) != null
            OutlinedTextField(
                key, { key = it.trim() },
                label = { Text("BLE KEY（32 位十六进制）") },
                singleLine = true,
                isError = key.isNotEmpty() && !keyValid,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                profile, { profile = it.filter(Char::isDigit) },
                label = { Text("米家里的成员编号（可选，多人共用一台秤时填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.settings.scale = ScaleConfig(mac.trim(), key.lowercase(), profile.toIntOrNull() ?: 0)
                    vm.startScaleScan(background = true)
                },
                enabled = mac.count { it == ':' } == 5 && keyValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }

            Text("第 3 步：站上秤测试", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        when (val s = status) {
                            ScaleBridge.Status.Idle -> "等待称重…（光脚站上去才能测阻抗）"
                            is ScaleBridge.Status.Measuring -> "测量中 ${s.massKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: ""}"
                            is ScaleBridge.Status.Saved -> buildString {
                                append("已记录 ").append(String.format(Locale.US, "%.1f kg", s.weighIn.massKg))
                                s.weighIn.impedanceLow?.let { append("，阻抗 ").append(it.toInt()).append(" Ω") }
                                if (s.weighIn.impedanceLow == null) append("（没测到阻抗：穿袜子或脚太干）")
                            }
                            ScaleBridge.Status.WrongKey -> "收到秤的数据，但密钥不对，解密失败。请重新复制 BLE KEY。"
                            is ScaleBridge.Status.OtherProfile -> "这次是成员 ${s.profileId} 在称，已忽略。"
                        },
                    )
                }
            }
            Text(
                "每天只记录当天第一次称重（早上空腹最准）；与上次相差超过 5 kg 的读数会被当作家人称重而忽略。" +
                    "app 关闭时也会尝试在后台接收，但部分手机系统会限制后台蓝牙，打开 app 后称最可靠。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
