package build.wallet.recovery.keyset

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.ListKeysetsResponse
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake implementation of [SpendingKeysetRepairService] for testing.
 */
class SpendingKeysetRepairServiceFake : SpendingKeysetRepairService {
  private val _syncStatus =
    MutableStateFlow<SpendingKeysetSyncStatus>(SpendingKeysetSyncStatus.Synced)
  override val syncStatus: StateFlow<SpendingKeysetSyncStatus> = _syncStatus

  /**
   * The status to return from [checkSyncStatus].
   */
  var checkSyncStatusResult: SpendingKeysetSyncStatus = SpendingKeysetSyncStatus.Synced

  /**
   * Result to return from [checkPrivateKeysets].
   */
  var checkPrivateKeysetsResult: Result<PrivateKeysetInfo, KeysetRepairError> =
    Ok(PrivateKeysetInfo.None(FakeCachedData))

  /**
   * Result to return from [attemptRepair].
   */
  var attemptRepairResult: Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    Err(KeysetRepairError.FetchKeysetsFailed(cause = NotImplementedError("Fake not configured")))

  /**
   * Result to return from [regenerateActiveKeyset].
   */
  var regenerateActiveKeysetResult: Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    Err(KeysetRepairError.FetchKeysetsFailed(cause = NotImplementedError("Fake not configured")))

  override suspend fun checkPrivateKeysets(
    account: FullAccount,
  ): Result<PrivateKeysetInfo, KeysetRepairError> {
    return checkPrivateKeysetsResult
  }

  override suspend fun attemptRepair(
    account: FullAccount,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> {
    return attemptRepairResult
  }

  override suspend fun regenerateActiveKeyset(
    account: FullAccount,
    updatedKeybox: Keybox,
    hwSpendingKey: HwSpendingPublicKey,
    hwProofOfPossession: HwFactorProofOfPossession,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> {
    return regenerateActiveKeysetResult
  }

  /**
   * Sets the sync status directly for testing.
   */
  fun setStatus(status: SpendingKeysetSyncStatus) {
    _syncStatus.value = status
    checkSyncStatusResult = status
  }

  /**
   * Resets the fake to its initial state.
   */
  fun reset() {
    _syncStatus.value = SpendingKeysetSyncStatus.Synced
    checkSyncStatusResult = SpendingKeysetSyncStatus.Synced
    checkPrivateKeysetsResult = Ok(PrivateKeysetInfo.None(FakeCachedData))
    attemptRepairResult =
      Err(KeysetRepairError.FetchKeysetsFailed(cause = NotImplementedError("Fake not configured")))
    regenerateActiveKeysetResult =
      Err(KeysetRepairError.FetchKeysetsFailed(cause = NotImplementedError("Fake not configured")))
  }

  companion object {
    /**
     * Fake cached data for testing.
     */
    val FakeCachedData: KeysetRepairCachedData = KeysetRepairCachedData(
      response = ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = null,
        descriptorBackups = emptyList(),
        activeKeysetId = "fake-active-keyset-id"
      ),
      serverActiveKeysetId = "fake-active-keyset-id"
    )
  }
}
