package com.ridvanozdemir.socialdiet.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class FoodClassPrediction(
    val key: String,
    val displayName: String,
    val confidence: Float
)

class TurkishFoodClassifier(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var modelBuffer: ByteBuffer? = null
    private var labels: List<String>? = null

    fun isAvailable(): Boolean = runCatching {
        appContext.assets.open(MODEL_ASSET).close()
        appContext.assets.open(LABELS_ASSET).close()
        true
    }.getOrDefault(false)

    @Synchronized
    private fun getLabels(): List<String> {
        labels?.let { return it }
        return appContext.assets.open(LABELS_ASSET).bufferedReader().useLines { lines ->
            lines.map(String::trim).filter(String::isNotEmpty).toList()
        }.also { labels = it }
    }

    @Synchronized
    private fun getInterpreter(): Interpreter {
        interpreter?.let { return it }
        val bytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
        modelBuffer = buffer
        return Interpreter(
            buffer,
            Interpreter.Options().apply { setNumThreads(4) }
        ).also { interpreter = it }
    }

    fun classify(imageUri: Uri, topK: Int = 3): List<FoodClassPrediction> {
        if (!isAvailable()) return emptyList()
        val labelList = getLabels()
        if (labelList.isEmpty()) return emptyList()

        val bitmap = decodeBitmap(imageUri)
        val input = bitmapToInput(bitmap)
        if (!bitmap.isRecycled) bitmap.recycle()

        val output = Array(1) { FloatArray(labelList.size) }
        getInterpreter().run(input, output)

        return output[0]
            .mapIndexed { index, confidence ->
                val key = labelList[index]
                FoodClassPrediction(
                    key = key,
                    displayName = DISPLAY_NAMES[key] ?: key.replace('_', ' '),
                    confidence = confidence.coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.confidence }
            .take(topK.coerceAtLeast(1))
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            appContext.contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(stream) { "Fotoğraf açılamadı." }
                requireNotNull(BitmapFactory.decodeStream(stream)) {
                    "Fotoğraf çözümlenemedi."
                }
            }
        }
    }

    private fun bitmapToInput(source: Bitmap): ByteBuffer {
        val cropSize = min(source.width, source.height)
        val offsetX = (source.width - cropSize) / 2
        val offsetY = (source.height - cropSize) / 2
        val cropped = Bitmap.createBitmap(source, offsetX, offsetY, cropSize, cropSize)
        val resized = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)

        if (cropped !== source && !cropped.isRecycled) cropped.recycle()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
            buffer.putFloat((pixel and 0xFF).toFloat())
        }
        if (resized !== source && !resized.isRecycled) resized.recycle()
        buffer.rewind()
        return buffer
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        modelBuffer = null
        labels = null
    }

    companion object {
        private const val MODEL_ASSET = "turkish_food_classifier.tflite"
        private const val LABELS_ASSET = "turkish_food_labels.txt"
        private const val INPUT_SIZE = 160

        private val DISPLAY_NAMES = mapOf(
            "asure" to "Aşure",
            "baklava" to "Baklava",
            "biber_dolmasi" to "Biber Dolması",
            "borek" to "Börek",
            "cig_kofte" to "Çiğ Köfte",
            "enginar" to "Enginar",
            "et_sote" to "Et Sote",
            "gozleme" to "Gözleme",
            "hamsi" to "Hamsi",
            "hunkar_begendi" to "Hünkâr Beğendi",
            "icli_kofte" to "İçli Köfte",
            "ispanak" to "Ispanak",
            "izmir_kofte" to "İzmir Köfte",
            "karniyarik" to "Karnıyarık",
            "kebap" to "Kebap",
            "kisir" to "Kısır",
            "kuru_fasulye" to "Kuru Fasulye",
            "lahmacun" to "Lahmacun",
            "lokum" to "Lokum",
            "manti" to "Mantı",
            "mucver" to "Mücver",
            "pirinc_pilavi" to "Pirinç Pilavı",
            "simit" to "Simit",
            "taze_fasulye" to "Taze Fasulye",
            "yaprak_sarma" to "Yaprak Sarma"
        )
    }
}
