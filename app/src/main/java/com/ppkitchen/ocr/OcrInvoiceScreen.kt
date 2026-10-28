package com.ppkitchen.ocr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height // <-- ДОБАВЛЕН ИМПОРТ
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ppkitchen.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrInvoiceScreen(
    mode: OcrMode,
    onBack: () -> Unit,
    onSave: (List<InvoiceRowDraft>, Boolean) -> Unit,
    viewModel: OcrViewModel = viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }

    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val error by viewModel.error.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.recognizeFromUri(uri, context, mode)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.initializeTesseract(context)
        val words = withContext(Dispatchers.IO) {
            db.ingredientDao().getAllOnce().map { it.name }
        }
        viewModel.updateKnownWords(words)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == OcrMode.INVOICE) "Скан накладной" else "Скан справочника БЖУ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Из галереи")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !hasPermission -> PermissionStub(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
                state == OcrState.Draft -> DraftList(
                    rows = rows,
                    mode = mode,
                    viewModel = viewModel,
                    onSave = onSave,
                    onBack = onBack
                )
                else -> CameraBlock(
                    processing = state == OcrState.Processing,
                    status = statusMessage,
                    error = error,
                    onImageCaptured = { proxy -> viewModel.recognize(proxy, mode) },
                    onRetry = viewModel::retry
                )
            }
        }
    }
}

@Composable
private fun PermissionStub(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Для сканирования нужен доступ к камере", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        Button(onClick = onRequest) { Text("Разрешить") }
    }
}

@Composable
private fun CameraBlock(
    processing: Boolean,
    status: String?,
    error: String?,
    onImageCaptured: (ImageProxy) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (status != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(text = status, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (error != null) {
                SelectionContainer {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (error != null) {
                    OutlinedButton(onClick = onRetry) { Text("Ещё раз") }
                }
                Button(
                    enabled = !processing && imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    onImageCaptured(image)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                }
                            }
                        )
                    }
                ) {
                    Text(if (processing) "Ожидайте..." else "Снять")
                }
            }
        }
    }
}

@Composable
private fun DraftList(
    rows: List<InvoiceRowDraft>,
    mode: OcrMode,
    viewModel: OcrViewModel,
    onSave: (List<InvoiceRowDraft>, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var updateDb by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.id }) { row ->
                DraftRowCard(
                    row = row,
                    mode = mode,
                    onChange = { updated -> viewModel.updateRow(updated) },
                    onDelete = { viewModel.removeRow(row.id) }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = updateDb,
                        onCheckedChange = { updateDb = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Обновить цены в базе", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Если снять галочку, цены не изменятся", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = viewModel::addEmptyRow) { Text("Добавить") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = viewModel::reset) { Text("Сбросить") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(rows.filter { row -> row.name.isNotBlank() }, updateDb) },
                        enabled = rows.any { row -> row.name.isNotBlank() }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftRowCard(
    row: InvoiceRowDraft,
    mode: OcrMode,
    onChange: (InvoiceRowDraft) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { onChange(row.copy(name = it)) },
                    label = { Text("Наименование") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }

            if (mode == OcrMode.INVOICE) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.quantity,
                        onValueChange = { onChange(row.copy(quantity = it)) },
                        label = { Text("Кол-во") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.unit,
                        onValueChange = { onChange(row.copy(unit = it)) },
                        label = { Text("Ед.") },
                        singleLine = true,
                        modifier = Modifier.width(70.dp)
                    )
                    OutlinedTextField(
                        value = row.price,
                        onValueChange = { onChange(row.copy(price = it)) },
                        label = { Text("Цена") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.calories,
                        onValueChange = { onChange(row.copy(calories = it)) },
                        label = { Text("Ккал") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.protein,
                        onValueChange = { onChange(row.copy(protein = it)) },
                        label = { Text("Б") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.fat,
                        onValueChange = { onChange(row.copy(fat = it)) },
                        label = { Text("Ж") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.carbs,
                        onValueChange = { onChange(row.copy(carbs = it)) },
                        label = { Text("У") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}