package build.wallet.f8e

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for retrieving current active [F8eEnvironment].
 */
interface ActiveF8eEnvironmentRepository {
  /**
   * Emits currently active [F8eEnvironment], if any.
   */
  fun activeF8eEnvironment(): Flow<Result<F8eEnvironment?, Error>>
}
