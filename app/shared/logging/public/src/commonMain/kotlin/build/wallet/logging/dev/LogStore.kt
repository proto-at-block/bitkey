package build.wallet.logging.dev

import build.wallet.logging.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Records app logs and stores them for showing in developer options for debugging purposes.
 * TODO(W-1785): use noop implementation in release builds.
 */
interface LogStore {
  /**
   * Represents an entity of a single log.
   */
  data class Entity(
    val time: Instant,
    val level: LogLevel,
    val tag: String,
    val throwable: Throwable?,
    val message: String,
  )

  /**
   * Record a log, will be emitted by [logs].
   */
  fun record(entity: Entity)

  /**
   * Emits latest list of log entities, above the [minimumLevel].
   */
  fun logs(
    minimumLevel: LogLevel,
    tag: String?,
  ): Flow<List<Entity>>

  /**
   * Returns current list of log entities above the [minimumLevel].
   * If [tag] is specified, only log entities with the same log are returned.
   */
  fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity>

  /**
   * Clears all logs that were recorded this far.
   */
  fun clear()
}
