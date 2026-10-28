package com.ppkitchen.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OcrViewModel : ViewModel() {

    private var tessBaseAPI: TessBaseAPI? = null

    private val _rows = MutableStateFlow<List<InvoiceRowDraft>>(emptyList())
    val rows: StateFlow<List<InvoiceRowDraft>> = _rows.asStateFlow()

    private val _state = MutableStateFlow(OcrState.Camera)
    val state: StateFlow<OcrState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var knownWords: List<String> = emptyList()

    fun updateKnownWords(words: List<String>) {
        knownWords = words
    }

    fun initializeTesseract(context: Context) {
        if (tessBaseAPI != null) return

        try {
            if (!OpenCVLoader.initDebug()) {
                _error.value = "Ошибка инициализации OpenCV"
                _state.value = OcrState.Error
                return
            }
        } catch (e: Throwable) {
            _error.value = "OpenCV не загружен: ${e.message}"
            _state.value = OcrState.Error
            return
        }

        val dataPath = File(context.filesDir, "tesseract/").apply { mkdirs() }
        val tessdataDir = File(dataPath, "tessdata").apply { mkdirs() }

        try {
            copyAssetFile(context, "tessdata/rus.traineddata", File(tessdataDir, "rus.traineddata"))
            copyAssetFile(context, "tessdata/eng.traineddata", File(tessdataDir, "eng.traineddata"))
        } catch (e: IOException) {
            _error.value = "Ошибка загрузки языковых данных: ${e.message}"
            _state.value = OcrState.Error
            return
        }

        try {
            val api = TessBaseAPI()
            val initSuccess = api.init(dataPath.absolutePath, "rus+eng")
            if (!initSuccess) {
                _error.value = "Не удалось инициализировать Tesseract"
                _state.value = OcrState.Error
                return
            }

            // Умный белый список: буквы, цифры, пробелы, точки, запятые и дефисы.
            // Запрещает Tesseract читать линии таблицы как "_" или "|"
            api.setVariable("tessedit_char_whitelist", "0123456789.,АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя- ()")

            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO

            tessBaseAPI = api
        } catch (e: Throwable) {
            _error.value = "Сбой Tesseract: ${e.message}"
            _state.value = OcrState.Error
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, destFile: File) {
        if (destFile.exists()) return
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

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
        _statusMessage.value = "Захват кадра..."

        try {
            val yuvImage = YuvImage(
                mediaImage.planes[0].buffer.array(),
                ImageFormat.NV21,
                mediaImage.width,
                mediaImage.height,
                null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 90, out)
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (bitmap != null) {
                processBitmapAsync(bitmap, mode) {
                    imageProxy.close()
                }
            } else {
                _state.value = OcrState.Error
                _error.value = "Ошибка конвертации"
                imageProxy.close()
            }
        } catch (e: Throwable) {
            _state.value = OcrState.Error
            _error.value = "Ошибка: ${e.message}"
            imageProxy.close()
        }
    }

    fun recognizeFromUri(uri: Uri, context: Context, mode: OcrMode) {
        _state.value = OcrState.Processing
        _error.value = null
        _statusMessage.value = "Загрузка файла..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                var inSampleSize = 1
                val maxDimension = 2000
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options()
                decodeOptions.inSampleSize = inSampleSize
                decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888

                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        _state.value = OcrState.Error
                        _error.value = "Не удалось загрузить изображение"
                    }
                    return@launch
                }

                processBitmapAsync(bitmap, mode) { }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _state.value = OcrState.Error
                    _error.value = "Ошибка файла: ${e.message}"
                }
            }
        }
    }

    private fun processBitmapAsync(bitmap: Bitmap, mode: OcrMode, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            val api = tessBaseAPI
            if (api == null) {
                withContext(Dispatchers.Main) {
                    _state.value = OcrState.Error
                    _error.value = "Tesseract не инициализирован"
                    onComplete()
                }
                return@launch
            }

            try {
                withContext(Dispatchers.Main) { _statusMessage.value = "Улучшение изображения..." }
                val processedBitmap = preprocessImage(bitmap)

                withContext(Dispatchers.Main) { _statusMessage.value = "Распознавание текста..." }
                api.setImage(processedBitmap)

                api.utF8Text

                val detectedLines = mutableListOf<DetectedLine>()
                val iterator = api.resultIterator

                if (iterator != null) {
                    iterator.begin()
                    do {
                        if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_WORD)) {
                            val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                            val wordBox = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)

                            if (wordText != null && wordBox != null && wordText.isNotBlank() && wordBox.size == 4) {
                                val left = wordBox[0]
                                val top = wordBox[1]
                                val right = wordBox[2]
                                val bottom = wordBox[3]
                                val height = bottom - top
                                val centerY = top + (height / 2)
                                val centerX = left + ((right - left) / 2)

                                detectedLines.add(
                                    DetectedLine(
                                        text = wordText.trim(),
                                        top = top,
                                        left = left,
                                        height = height,
                                        centerY = centerY,
                                        centerX = centerX
                                    )
                                )
                            }
                        }
                    } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                }

                if (detectedLines.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _state.value = OcrState.Error
                        _error.value = "Tesseract не увидел текст."
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) { _statusMessage.value = "Разбор строк..." }

                val parsed = if (mode == OcrMode.INVOICE) {
                    InvoiceParser.parseInvoice(detectedLines, knownWords)
                } else {
                    InvoiceParser.parseReference(detectedLines, knownWords)
                }

                withContext(Dispatchers.Main) {
                    _rows.value = parsed
                    _state.value = if (parsed.isEmpty()) OcrState.Error else OcrState.Draft
                    if (parsed.isEmpty()) {
                        val debugStr = detectedLines.take(15).joinToString(" | ") { "'${it.text}' (X:${it.centerX}, Y:${it.centerY})" }
                        _error.value = "Не удалось разобрать строки.\nСлова:\n$debugStr"
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _state.value = OcrState.Error
                    _error.value = "Ошибка распознавания: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = null
                    onComplete()
                }
            }
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            // Адаптивный порог хорошо убирает синий фон таблицы
            val thresh = Mat()
            Imgproc.adaptiveThreshold(gray, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 10.0)

            val resultBitmap = Bitmap.createBitmap(thresh.cols(), thresh.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(thresh, resultBitmap)

            src.release()
            gray.release()
            thresh.release()

            resultBitmap
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    fun updateRow(updated: InvoiceRowDraft) {
        _rows.value = _rows.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeRow(id: Long) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    fun addEmptyRow() {
        _rows.value += InvoiceRowDraft()
    }

    fun retry() {
        _state.value = OcrState.Camera
        _error.value = null
        _statusMessage.value = null
    }

    fun reset() {
        _rows.value = emptyList()
        _state.value = OcrState.Camera
        _error.value = null
        _statusMessage.value = null
    }

    override fun onCleared() {
        tessBaseAPI?.end()
        tessBaseAPI = null
    }
}