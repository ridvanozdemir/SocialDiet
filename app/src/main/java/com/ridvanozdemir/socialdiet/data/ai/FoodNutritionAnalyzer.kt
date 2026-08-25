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
import kotlin.math.roundToInt

data class NutritionPrediction(
    val caloriesPer100g: Float,
    val massGrams: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val proteinPer100g: Float
) {
    val totalCalories: Int
        get() = ((caloriesPer100g * massGrams) / 100f)
            .coerceAtLeast(0f)
            .roundToInt()

    fun fatGramsFor(mass: Float): Float =
        ((fatPer100g * mass) / 100f).coerceAtLeast(0f)

    fun carbsGramsFor(mass: Float): Float =
        ((carbsPer100g * mass) / 100f).coerceAtLeast(0f)

    fun proteinGramsFor(mass: Float): Float =
        ((proteinPer100g * mass) / 100f).coerceAtLeast(0f)
}

class FoodNutritionAnalyzer(
    context: Context
) : Closeable {
    private val appContext = context.applicationContext
    private var modelBuffer: ByteBuffer? = null
    private var interpreter: Interpreter? = null

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

    fun analyze(imageUri: Uri): NutritionPrediction {
        val bitmap = decodeBitmap(imageUri)
        val input = bitmapToInput(bitmap)
        if (!bitmap.isRecycled) bitmap.recycle()

        val output = Array(1) { FloatArray(5) }
        getInterpreter().run(input, output)
        val values = output[0]

        return NutritionPrediction(
            caloriesPer100g = safe(values[0]),
            massGrams = safe(values[1]).coerceAtLeast(1f),
            fatPer100g = safe(values[2]),
            carbsPer100g = safe(values[3]),
            proteinPer100g = safe(values[4])
        )
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

        val cropped = Bitmap.createBitmap(
            source,
            offsetX,
            offsetY,
            cropSize,
            cropSize
        )
        val resized = Bitmap.createScaledBitmap(
            cropped,
            INPUT_SIZE,
            INPUT_SIZE,
            true
        )

        if (cropped !== source && !cropped.isRecycled) cropped.recycle()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * FLOAT_BYTES)
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

    private fun safe(value: Float): Float =
        if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    override fun close() {
        interpreter?.close()
        interpreter = null
        modelBuffer = null
    }

    companion object {
        private const val MODEL_ASSET = "image2nutrition.tflite"
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        private const val FLOAT_BYTES = 4
    }
}
