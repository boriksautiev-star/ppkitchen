package com.ppkitchen.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
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

    private val _bitmapToCrop = MutableStateFlow<Bitmap?>(null)
    val bitmapToCrop: StateFlow<Bitmap?> = _bitmapToCrop.asStateFlow()

    private var currentMode: OcrMode = OcrMode.INVOICE
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
            _error.value = "Ошибка загрузки данных: ${e.message}"
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
            api.setVariable("tessedit_char_whitelist", "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюяABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,/- ()")
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
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
        currentMode = mode
        try {
            val bitmap = imageProxy.toBitmap()
            val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            _bitmapToCrop.value = rotatedBitmap
            _state.value = OcrState.Crop
        } catch (e: Throwable) {
            _error.value = "Ошибка камеры: ${e.message}"
            _state.value = OcrState.Error
        } finally {
            imageProxy.close()
        }
    }

    fun recognizeFromUri(uri: Uri, context: Context, mode: OcrMode) {
        currentMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

                var inSampleSize = 1
                val maxDimension = 2500
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

                var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (bitmap != null && !matrix.isIdentity) {
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }
                }

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        _bitmapToCrop.value = bitmap
                        _state.value = OcrState.Crop
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _error.value = "Не удалось загрузить фото"
                        _state.value = OcrState.Error
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _error.value = "Ошибка файла: ${e.message}"
                    _state.value = OcrState.Error
                }
            }
        }
    }

    fun cancelCrop() {
        _bitmapToCrop.value = null
        _state.value = OcrState.Camera
    }

    fun processCroppedImage(bitmap: Bitmap) {
        _bitmapToCrop.value = null
        processBitmapAsync(bitmap, currentMode)
    }

    private fun processBitmapAsync(originalBitmap: Bitmap, mode: OcrMode) {
        _state.value = OcrState.Processing
        _error.value = null
        _statusMessage.value = "Улучшение изображения..."

        viewModelScope.launch(Dispatchers.Default) {
            val api = tessBaseAPI
            if (api == null) {
                withContext(Dispatchers.Main) {
                    _error.value = "Tesseract не инициализирован"
                    _state.value = OcrState.Error
                }
                return@launch
            }

            try {
                var processedBitmap = preprocessImage(originalBitmap)
                var detectedLines = extractWords(api, processedBitmap)

                if (detectedLines.isEmpty()) {
                    withContext(Dispatchers.Main) { _statusMessage.value = "Повторная попытка (без очистки)..." }
                    processedBitmap = originalBitmap
                    detectedLines = extractWords(api, processedBitmap)
                }

                if (detectedLines.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _error.value = "Текст не найден."
                        _state.value = OcrState.Error
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) { _statusMessage.value = "Разбор строк..." }

                val parsed = when (mode) {
                    OcrMode.INVOICE -> InvoiceParser.parseInvoice(detectedLines)
                    OcrMode.REFERENCE -> InvoiceParser.parseReference(detectedLines)
                }

                withContext(Dispatchers.Main) {
                    _rows.value = parsed
                    _state.value = if (parsed.isEmpty()) OcrState.Error else OcrState.Draft
                    if (parsed.isEmpty()) _error.value = "Не удалось разобрать строки."
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _error.value = "Ошибка: ${e.message}"
                    _state.value = OcrState.Error
                }
            } finally {
                withContext(Dispatchers.Main) { _statusMessage.value = null }
            }
        }
    }

    private fun extractWords(api: TessBaseAPI, bitmap: Bitmap): List<DetectedLine> {
        val detectedLines = mutableListOf<DetectedLine>()
        api.setImage(bitmap)
        api.utF8Text

        val iterator = api.resultIterator
        if (iterator != null) {
            iterator.begin()
            do {
                if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_WORD)) {
                    val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    val wordBox = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)

                    if (wordText != null && wordBox != null && wordText.isNotBlank() && wordBox.size == 4) {
                        detectedLines.add(
                            DetectedLine(
                                text = wordText.trim(),
                                top = wordBox[1],
                                left = wordBox[0],
                                height = wordBox[3] - wordBox[1],
                                centerY = wordBox[1] + (wordBox[3] - wordBox[1]) / 2,
                                centerX = wordBox[0] + (wordBox[2] - wordBox[0]) / 2
                            )
                        )
                    }
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        }
        return detectedLines
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            val resized = Mat()
            if (src.cols() < 1500) {
                Imgproc.resize(src, resized, Size(src.cols() * 2.0, src.rows() * 2.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
            } else {
                src.copyTo(resized)
            }

            val gray = Mat()
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)

            val bw = Mat()
            Imgproc.threshold(gray, bw, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

            val horizontal = bw.clone()
            val horizontalSize = resized.cols() / 15
            val horizontalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(horizontalSize.toDouble(), 1.0))
            Imgproc.erode(horizontal, horizontal, horizontalKernel)
            Imgproc.dilate(horizontal, horizontal, horizontalKernel)

            val vertical = bw.clone()
            val verticalSize = resized.rows() / 15
            val verticalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, verticalSize.toDouble()))
            Imgproc.erode(vertical, vertical, verticalKernel)
            Imgproc.dilate(vertical, vertical, verticalKernel)

            val lines = Mat()
            Core.add(horizontal, vertical, lines)
            val resultMat = Mat()
            Core.subtract(bw, lines, resultMat)
            val finalMat = Mat()
            Core.bitwise_not(resultMat, finalMat)

            val resultBitmap = Bitmap.createBitmap(finalMat.cols(), finalMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(finalMat, resultBitmap)

            src.release(); resized.release(); gray.release(); bw.release()
            horizontal.release(); vertical.release(); lines.release()
            resultMat.release(); finalMat.release()

            resultBitmap
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    fun updateRow(updated: InvoiceRowDraft) {
        _rows.value = _rows.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeRow(id: Long) { _rows.value = _rows.value.filterNot { it.id == id } }
    fun addEmptyRow() { _rows.value += InvoiceRowDraft() }
    fun retry() { _state.value = OcrState.Camera; _error.value = null; _statusMessage.value = null }
    fun reset() { _rows.value = emptyList(); _state.value = OcrState.Camera; _error.value = null; _statusMessage.value = null }

    override fun onCleared() {
        tessBaseAPI?.end()
        tessBaseAPI = null
    }
}