package build.wallet.home

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface GettingStartedTaskDao {
  /**
   * A flow of the current list of tasks
   * If a DB error occurs, this returns an empty list
   */
  fun tasks(): Flow<List<GettingStartedTask>>

  /**
   * The current list of tasks in the dao
   * If a DB error occurs, this returns an empty list
   */
  suspend fun getTasks(): List<GettingStartedTask>

  /**
   * Adds the given tasks to the dao
   */
  suspend fun addTasks(tasks: List<GettingStartedTask>): Result<Unit, DbError>

  /**
   * Updates the state of the given task.
   */
  suspend fun updateTask(
    id: GettingStartedTask.TaskId,
    state: GettingStartedTask.TaskState,
  ): Result<Unit, DbError>

  /**
   * Clears all tasks
   */
  suspend fun clearTasks(): Result<Unit, DbError>
}
