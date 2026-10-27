package com.ppkitchen.ocr

import android.content.Context
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OcrViewModel : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _rows = MutableStateFlow<List<InvoiceRowDraft>>(emptyList())
    val rows: StateFlow<List<InvoiceRowDraft>> = _rows.asStateFlow()

    private val _state = MutableStateFlow(OcrState.Camera)
    val state: StateFlow<OcrState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Прямая аннотация для использования экспериментального API
    @ExperimentalGetImage
    fun recognize(imageProxy: ImageProxy, mode: OcrMode) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            _state.value = OcrState.Error
            _error.value = "Не удалось получить изображение"
            return
        }

        _state.value = OcrState.Processing
        _error.value = null

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        processImage(inputImage, mode) {
            imageProxy.close()
        }
    }

    fun recognizeFromUri(uri: Uri, context: Context, mode: OcrMode) {
        _state.value = OcrState.Processing
        _error.value = null
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            processImage(inputImage, mode) { }
        } catch (e: Exception) {
            _state.value = OcrState.Error
            _error.value = "Ошибка загрузки файла: ${e.message}"
        }
    }

    private fun processImage(inputImage: InputImage, mode: OcrMode, onComplete: () -> Unit) {
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val detected = visionText.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        DetectedLine(
                            text = line.text,
                            top = box.top,
                            left = box.left,
                            height = box.height(),
                            centerY = box.centerY()
                        )
                    }

                val parsed = if (mode == OcrMode.INVOICE) {
                    InvoiceParser.parseInvoice(detected)
                } else {
                    InvoiceParser.parseReference(detected)
                }

                _rows.value = parsed
                _state.value = if (parsed.isEmpty()) OcrState.Error else OcrState.Draft
                if (parsed.isEmpty()) {
                    _error.value = "Не удалось распознать строки. Попробуйте ещё раз."
                }
            }
            .addOnFailureListener { e ->
                _state.value = OcrState.Error
                _error.value = e.message ?: "Ошибка распознавания"
            }
            .addOnCompleteListener {
                onComplete()
            }
    }

    fun updateRow(updated: InvoiceRowDraft) {
        _rows.value = _rows.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeRow(id: Long) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    fun addEmptyRow() {
        _rows.value = _rows.value + InvoiceRowDraft()
    }

    fun retry() {
        _state.value = OcrState.Camera
        _error.value = null
    }

    fun reset() {
        _rows.value = emptyList()
        _state.value = OcrState.Camera
        _error.value = null
    }

    override fun onCleared() {
        recognizer.close()
    }
}