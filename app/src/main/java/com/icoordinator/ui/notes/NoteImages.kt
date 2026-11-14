package com.icoordinator.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.icoordinator.personal.NoteImageFiles
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Import (decode, shrink, rotate, store as JPEG) and load of note images in filesDir/note_images. */
object NoteImages {
    private const val MAX_IMPORT_PX = 1600
    private const val JPEG_QUALITY = 85

    /** Copies the picked image into the notes image folder; returns the new file name, or null on any failure. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var target: File? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMPORT_PX)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null

            val orientation = resolver.openInputStream(uri)?.use {
                try {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (e: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            var bitmap = decoded
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (bitmap !== decoded) decoded.recycle()
            }
            if (bitmap.hasAlpha()) {
                // JPEG has no transparency: draw on white instead of black
                val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                bitmap.recycle()
                bitmap = flat
            }

            val name = UUID.randomUUID().toString() + ".jpg"
            val file = File(NoteImageFiles.dir(context), name)
            target = file
            val ok = FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            bitmap.recycle()
            if (!ok) {
                file.delete()
                return@withContext null
            }
            name
        } catch (e: CancellationException) {
            target?.delete()
            throw e
        } catch (e: Exception) {
            target?.delete()
            null
        } catch (e: OutOfMemoryError) {
            target?.delete()
            null
        }
    }

    /** Decodes a stored image so that its longest side is about [maxPx] or less; null when missing or unreadable. */
    suspend fun load(context: Context, name: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")) return@withContext null
        try {
            val file = File(NoteImageFiles.dir(context), name)
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Power-of-two sample size that brings the longest side to [maxPx] or less. */
    private fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > maxPx) sample *= 2
        return sample
    }
}
