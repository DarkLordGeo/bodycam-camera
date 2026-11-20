package com.darklordgeo.bodycamfx.media

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateTakenMs: Long,
    val durationMs: Long,
    val displayName: String,
    val relativePath: String,
)

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object AlreadyGone : DeleteResult
    /** Android needs the user's consent (media not owned by this install). Launch, then refresh. */
    class NeedsConsent(val intentSender: IntentSender) : DeleteResult
    class Failed(val message: String) : DeleteResult
}

/** Reads this app's captures from MediaStore and keeps the gallery in sync with external changes. */
class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val thumbs = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    private val imagesUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val videosUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Emits the current list and re-queries whenever MediaStore reports a change. */
    fun observe(): Flow<List<MediaItem>> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(imagesUri, true, observer)
        resolver.registerContentObserver(videosUri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { query() }.flowOn(Dispatchers.IO)

    fun query(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        queryCollection(imagesUri, "Pictures/BODYCAM/%", isVideo = false, out)
        queryCollection(videosUri, "Movies/BODYCAM/%", isVideo = true, out)
        out.sortByDescending { it.dateTakenMs }
        return out
    }

    private fun queryCollection(collection: Uri, pathLike: String, isVideo: Boolean, out: MutableList<MediaItem>) {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        if (isVideo) projection += MediaStore.Video.Media.DURATION
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        try {
            resolver.query(collection, projection.toTypedArray(), selection, arrayOf(pathLike), null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iDur = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                    out += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        isVideo = isVideo,
                        dateTakenMs = taken,
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        displayName = c.getString(iName) ?: "",
                        relativePath = c.getString(iPath) ?: "",
                    )
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("MediaRepository", "Query failed", t)
        }
    }

    suspend fun thumbnail(item: MediaItem, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = item.id * 31 + if (item.isVideo) 1 else 0
        thumbs.get(key)?.let { return@withContext it }
        try {
            resolver.loadThumbnail(item.uri, Size(sizePx, sizePx), null).also { thumbs.put(key, it) }
        } catch (t: Throwable) {
            null
        }
    }

    /** Ownership-aware delete of exactly one item. */
    suspend fun delete(item: MediaItem): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val n = resolver.delete(item.uri, null, null)
            if (n > 0) DeleteResult.Deleted else DeleteResult.AlreadyGone
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    DeleteResult.NeedsConsent(MediaStore.createDeleteRequest(resolver, listOf(item.uri)).intentSender)
                e is RecoverableSecurityException ->
                    DeleteResult.NeedsConsent(e.userAction.actionIntent.intentSender)
                else -> DeleteResult.Failed(e.message ?: "Permission denied")
            }
        } catch (t: Throwable) {
            DeleteResult.Failed(t.message ?: "Delete failed")
        }
    }
}
