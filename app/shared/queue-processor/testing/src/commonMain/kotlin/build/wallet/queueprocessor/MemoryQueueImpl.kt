package build.wallet.queueprocessor

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Generic implementation of a [Queue<T>] that is backed by an in-memory list. Useful for
 * prototyping and testing.
 */
class MemoryQueueImpl<T> : Queue<T> {
  private val queue = mutableListOf<T>()

  override suspend fun append(item: T): Result<Unit, Error> {
    queue.add(item)

    return Ok(Unit)
  }

  override suspend fun take(num: Int): Result<List<T>, Error> {
    return Ok(queue.take(num))
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }
    repeat(num) {
      if (queue.isNotEmpty()) {
        queue.removeAt(0)
      }
    }
    return Ok(Unit)
  }
}
