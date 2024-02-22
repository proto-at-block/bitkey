package build.wallet.keybox

import com.github.michaelbull.result.Result

interface AppDataDeleter {
  /**
   * Deletes app's local data: database and keychain (including app spending and auth private keys).
   * Used for development purposes only.
   */
  suspend fun deleteAll(): Result<Unit, Error>
}
