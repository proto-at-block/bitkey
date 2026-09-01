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
   * Access all unsealed [Ssek]s from local storage, keyed by their [SealedSsek] form.
   */
  suspend fun getAll(): Result<Map<SealedSsek, Ssek>, Throwable>

  /**
   * Enumerates the [SealedSsek] identifiers of all stored SSEKs without reading the
   * unsealed key material. Prefer this over [getAll] when only the set of stored keys
   * is needed (e.g. staleness checks) to avoid materializing plaintext keys.
   */
  suspend fun getAllSealedIds(): Result<Set<SealedSsek>, Throwable>

  /**
   * Clear any SSEKs in local storage.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
