package build.wallet.cloud.backup.csek

import com.github.michaelbull.result.Result

interface SsekDao {
  /**
   * Access unsealed [Ssek] from local storage, if available.
   *
   * The [Ssek] is created using [SekGenerator] during hardware pairing.
   *
   * Read [SealedSsek] and [Sek] for more details.
   */
  suspend fun get(key: SealedSsek): Result<Ssek?, Throwable>

  /**
   * Set CSEK in local storage using sealed CSEK as a key, and raw CSEK as value.
   *
   * Read [SealedSsek] and [Ssek] for more details.
   */
  suspend fun set(
    key: SealedSsek,
    value: Ssek,
  ): Result<Unit, Throwable>

  /**
   * Clear any SSEKs in local storage.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
