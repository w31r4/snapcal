package com.snapcal.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.snapcal.app.util.Images

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(vm: MainViewModel) {
    val state by vm.capture.collectAsState()
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) vm.addPhoto(uri)
        pendingUri = null
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        uris.forEach(vm::addPhoto)
    }
    fun launchCamera() {
        val uri = Images.newCaptureUri(context)
        pendingUri = uri
        takePicture.launch(uri)
    }

    // Lazy path: open the camera straight away when the screen is entered with no photos.
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoLaunched && state.photos.isEmpty()) {
            autoLaunched = true
            launchCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记一顿") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }, enabled = !state.analyzing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.photos) { i, bytes ->
                    PhotoPreview(bytes, onRemove = { vm.removePhoto(i) }, enabled = !state.analyzing)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = ::launchCamera, enabled = !state.analyzing, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.AddAPhoto, "再拍一张")
                        }
                        FilledTonalIconButton(
                            onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.analyzing,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, "从相册选")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::setNote,
                label = { Text("补充一句（可选）") },
                placeholder = { Text("例如：半碗饭、油比较多、只吃了一半") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.analyzing,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            val config = remember { vm.repo.currentConfig() }
            Button(
                onClick = vm::analyze,
                enabled = state.photos.isNotEmpty() && !state.analyzing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.analyzing) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("AI 正在看…")
                } else {
                    Text(if (state.photos.isEmpty()) "先拍一张" else "算热量（${state.photos.size} 张）")
                }
            }
            Text(
                "使用 ${config.provider.label} · ${config.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.error != null) {
                OutlinedButton(onClick = { vm.navigate(Screen.Settings) }) { Text("去设置换模型 / 填 Key") }
            }
        }
    }
}

@Composable
private fun PhotoPreview(bytes: ByteArray, onRemove: () -> Unit, enabled: Boolean) {
    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    Box(Modifier.size(136.dp)) {
        if (bmp != null) {
            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
        }
        if (enabled) {
            FilledTonalIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)) {
                Icon(Icons.Default.Close, "删除", Modifier.size(18.dp))
            }
        }
    }
}

