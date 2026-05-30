package com.goldsmith.billing.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomAppIconConverter {
    suspend fun saveSquareIcon(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val source = decodeBitmap(context, sourceUri)
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = iconColorFilter()
        }
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        val src = Rect(left, top, left + size, top + size)
        val dst = RectF(0f, 0f, 512f, 512f)
        val shape = Path().apply { addRoundRect(dst, 112f, 112f, Path.Direction.CW) }
        val baseColor = averageColor(source)
        framePaint.shader = LinearGradient(
            0f,
            0f,
            512f,
            512f,
            brighten(baseColor, 1.25f),
            darken(baseColor, 0.72f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(shape, framePaint)
        canvas.save()
        canvas.clipPath(shape)
        canvas.drawBitmap(source, src, dst, imagePaint)
        framePaint.shader = null
        framePaint.color = Color.argb(28, 255, 255, 255)
        canvas.drawRoundRect(RectF(0f, 0f, 512f, 220f), 112f, 112f, framePaint)
        canvas.restore()
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 10f
        framePaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawPath(shape, framePaint)

        val file = File(context.filesDir, "custom_app_icon.png")
        file.outputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        if (source !== output) source.recycle()
        output.recycle()
        Uri.fromFile(file).toString()
    }

    private fun decodeBitmap(context: Context, sourceUri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("Unable to read selected image.")
        } ?: error("Selected file is not a readable image.")
    }

    private fun iconColorFilter(): ColorMatrixColorFilter {
        val saturation = ColorMatrix().apply { setSaturation(1.18f) }
        val contrast = ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0f, 0f, -8f,
                0f, 1.08f, 0f, 0f, -8f,
                0f, 0f, 1.08f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturation.postConcat(contrast)
        return ColorMatrixColorFilter(saturation)
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 12).coerceAtLeast(1)
        val stepY = (bitmap.height / 12).coerceAtLeast(1)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 32) {
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) return Color.rgb(42, 35, 28)
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun brighten(color: Int, amount: Float): Int =
        Color.rgb(
            (Color.red(color) * amount).toInt().coerceIn(0, 255),
            (Color.green(color) * amount).toInt().coerceIn(0, 255),
            (Color.blue(color) * amount).toInt().coerceIn(0, 255)
        )

    private fun darken(color: Int, amount: Float): Int =
        Color.rgb(
            (Color.red(color) * amount).toInt().coerceIn(0, 255),
            (Color.green(color) * amount).toInt().coerceIn(0, 255),
            (Color.blue(color) * amount).toInt().coerceIn(0, 255)
        )
}
