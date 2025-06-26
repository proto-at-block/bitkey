package build.wallet.cloud.backup.csek

import com.github.michaelbull.result.Result

interface CsekDao {
  /**
   * Access unsealed [Csek] from local storage, if available.
   *
   * The [Csek] is created using [SekGenerator] during hardware pairing.
   *
   * Read [SealedCsek] and [Csek] for more details.
   */
  suspend fun get(key: SealedCsek): Result<Csek?, Throwable>

  /**
   * Set CSEK in local storage using sealed CSEK as a key, and raw CSEK as value.
   *
   * Read [SealedCsek] and [Csek] for more details.
   */
  suspend fun set(
    key: SealedCsek,
    value: Csek,
  ): Result<Unit, Throwable>

  /**
   * Clear any CSEKs in local storage.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
