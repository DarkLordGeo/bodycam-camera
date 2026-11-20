package com.darklordgeo.bodycamfx.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes captures into the shared MediaStore collections so they show up in Google Photos and other
 * gallery apps immediately:
 *   Pictures/BODYCAM/Photos, Pictures/BODYCAM/Selfies, Movies/BODYCAM/Recordings
 */
class MediaStorage(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    private fun name(prefix: String, ext: String): String =
        "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.$ext"

    /** Saves a processed photo as a high-quality JPEG. Returns the new item's Uri. */
    suspend fun savePhoto(bitmap: Bitmap, selfie: Boolean): Uri = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name(if (selfie) "SELFIE" else "BODYCAM", "jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, if (selfie) SELFIE_DIR else PHOTO_DIR)
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the photo")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("JPEG encode failed")
            } ?: throw IOException("Could not open output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        } finally {
            bitmap.recycle()
        }
    }

    /** Output target for CameraX Recorder; CameraX handles IS_PENDING for videos itself. */
    fun videoOutputOptions(): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name("BODYCAM", "mp4"))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_DIR)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        return MediaStoreOutputOptions.Builder(
            resolver, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ).setContentValues(values).build()
    }

    companion object {
        const val PHOTO_DIR = "Pictures/BODYCAM/Photos/"
        const val SELFIE_DIR = "Pictures/BODYCAM/Selfies/"
        const val VIDEO_DIR = "Movies/BODYCAM/Recordings/"
        const val JPEG_QUALITY = 95
    }
}
