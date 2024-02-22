package build.wallet.queueprocessor

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

/**
 * Minimal Queue interface
 */
interface Queue<T> {
  /**
   * Append [T] to the end of the queue
   */
  suspend fun append(item: T): Result<Unit, Error>

  /**
   * Retrieve the first [num] elements from the queue without removing them. If [num] is greater than
   * queue, returns only the data in the queue.
   */
  suspend fun take(num: Int): Result<List<T>, Error>

  /**
   * Remove the first [num] elements from the queue. If [num] is greater than the size of the queue,
   * all elements are removed.
   */
  suspend fun removeFirst(num: Int): Result<Unit, Error>

  /**
   * Move the first [num] elements from the front of the queue to the back.
   */
  suspend fun moveToEnd(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Rotation count $num is less than zero." }

    return binding {
      val items = take(num).bind()
      items.map { append(it) }
      removeFirst(items.size).bind()
    }
  }
}
