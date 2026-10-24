/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong
import net.newpipe.app.extractor.Formatting
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class QueueItem(
    val id: Long,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)

/**
 * File de lecture. L'élément courant est repéré par son identifiant et non par sa position :
 * retirer un élément placé avant lui ne relance donc pas la lecture.
 */
class PlayQueue {
    private val nextId = AtomicLong(1)

    val items = mutableStateListOf<QueueItem>()

    var currentId by mutableStateOf<Long?>(null)
        private set

    val current: QueueItem? get() = items.firstOrNull { it.id == currentId }

    private val currentIndex: Int get() = items.indexOfFirst { it.id == currentId }

    fun itemFrom(info: StreamInfoItem) = QueueItem(
        id = nextId.getAndIncrement(),
        url = info.url,
        title = info.name,
        uploader = info.uploaderName.orEmpty(),
        thumbnailUrl = Formatting.thumbnail(info.thumbnails),
        durationSeconds = info.duration
    )

    /** Joue tout de suite : insère juste après l'élément courant */
    fun playNow(item: QueueItem) {
        items.add(currentIndex + 1, item)
        currentId = item.id
    }

    fun enqueue(item: QueueItem) {
        items.add(item)
        if (currentId == null) currentId = item.id
    }

    fun select(id: Long) {
        if (items.any { it.id == id }) currentId = id
    }

    fun remove(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        if (id == currentId) {
            currentId = items.getOrNull(index + 1)?.id ?: items.getOrNull(index - 1)?.id
        }
        items.removeAt(index)
    }

    fun move(id: Long, delta: Int) {
        val index = items.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in items.indices) return
        items.add(target, items.removeAt(index))
    }

    fun hasNext() = currentIndex in 0 until items.lastIndex

    fun next(): Boolean {
        val next = items.getOrNull(currentIndex + 1) ?: return false
        currentId = next.id
        return true
    }

    fun previous(): Boolean {
        val previous = items.getOrNull(currentIndex - 1) ?: return false
        currentId = previous.id
        return true
    }

    fun clear() {
        items.clear()
        currentId = null
    }
}
