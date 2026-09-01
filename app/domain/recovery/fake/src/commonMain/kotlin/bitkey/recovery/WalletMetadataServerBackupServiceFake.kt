package bitkey.recovery

import bitkey.f8e.account.WalletMetadataBackup
import build.wallet.bitcoin.metadata.WalletMetadataAccountId
import build.wallet.bitcoin.metadata.WalletMetadataSnapshot
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WalletMetadataServerBackupServiceFake : WalletMetadataServerBackupService {
  /** Settable so tests can drive restore-in-progress UI states (e.g. the note "Loading…" row). */
  val isRestoringFlow = MutableStateFlow(false)
  override val isRestoring: StateFlow<Boolean> = isRestoringFlow
  val remediationFlow = MutableStateFlow<WalletMetadataRemediation>(WalletMetadataRemediation.None)
  override val remediation: StateFlow<WalletMetadataRemediation> = remediationFlow
  val recordSealedSsekCalls = mutableListOf<Pair<WalletMetadataAccountId, build.wallet.cloud.backup.csek.SealedSsek>>()
  val provisionCalls = mutableListOf<Pair<FullAccountId, build.wallet.cloud.backup.csek.SealedSsek>>()
  val provisionEnvironments = mutableListOf<F8eEnvironment>()
  val requestBackupCalls = mutableListOf<WalletMetadataAccountId>()
  val syncPendingBackupsCalls = mutableListOf<Unit>()
  val fetchBackupCalls = mutableListOf<Pair<F8eEnvironment, FullAccountId>>()
  val resolveSealedSsekCalls = mutableListOf<Triple<F8eEnvironment, FullAccountId, WalletMetadataBackup?>>()
  val hasSsekCalls = mutableListOf<build.wallet.cloud.backup.csek.SealedSsek>()
  val recordUnsealedSsekCalls = mutableListOf<Triple<FullAccountId, build.wallet.cloud.backup.csek.SealedSsek, Ssek>>()
  val restoreCalls = mutableListOf<FullAccountId>()
  val prefetchedRestoreCalls = mutableListOf<FullAccountId>()
  var recordSealedSsekResult: Result<Unit, Error> = Ok(Unit)
  var provisionResult: Result<Unit, Error> = Ok(Unit)
  var requestBackupResult: Result<Unit, Error> = Ok(Unit)
  var syncPendingBackupsResult: Result<Unit, Error> = Ok(Unit)
  var fetchBackupResult: Result<WalletMetadataBackup?, Error> = Ok(null)
  var resolveSealedSsekResult: Result<build.wallet.cloud.backup.csek.SealedSsek?, Error> = Ok(null)
  var hasSsekResult: Result<Boolean, Error> = Ok(true)
  var recordUnsealedSsekResult: Result<Unit, Error> = Ok(Unit)
  var remediateWithUnsealedSsekResult: Result<WalletMetadataSnapshot?, Error> = Ok(null)
  var restoreFromServerBackupResult: Result<WalletMetadataSnapshot?, Error> = Ok(null)
  var restoreFromPrefetchedBackupResult: Result<WalletMetadataSnapshot?, Error> = Ok(null)

  override suspend fun recordSealedSsek(
    accountId: WalletMetadataAccountId,
    sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
  ): Result<Unit, Error> {
    recordSealedSsekCalls += accountId to sealedSsek
    return recordSealedSsekResult
  }

  override suspend fun provision(
    accountId: FullAccountId,
    sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    provisionCalls += accountId to sealedSsek
    provisionEnvironments += f8eEnvironment
    return provisionResult
  }

  override suspend fun requestBackup(accountId: WalletMetadataAccountId): Result<Unit, Error> {
    requestBackupCalls += accountId
    return requestBackupResult
  }

  override suspend fun syncPendingBackups(): Result<Unit, Error> {
    syncPendingBackupsCalls += Unit
    return syncPendingBackupsResult
  }

  override suspend fun fetchBackup(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, Error> {
    fetchBackupCalls += f8eEnvironment to accountId
    return fetchBackupResult
  }

  override suspend fun resolveSealedSsek(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    metadataBackup: WalletMetadataBackup?,
  ): Result<build.wallet.cloud.backup.csek.SealedSsek?, Error> {
    resolveSealedSsekCalls += Triple(f8eEnvironment, accountId, metadataBackup)
    return resolveSealedSsekResult
  }

  override suspend fun hasSsek(
    sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
  ): Result<Boolean, Error> {
    hasSsekCalls += sealedSsek
    return hasSsekResult
  }

  override suspend fun recordUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
    ssek: Ssek,
  ): Result<Unit, Error> {
    recordUnsealedSsekCalls += Triple(accountId, sealedSsek, ssek)
    return recordUnsealedSsekResult
  }

  override suspend fun remediateWithUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
    ssek: Ssek,
  ): Result<WalletMetadataSnapshot?, Error> = remediateWithUnsealedSsekResult

  override suspend fun restoreFromServerBackup(
    accountId: FullAccountId,
  ): Result<WalletMetadataSnapshot?, Error> {
    restoreCalls += accountId
    return restoreFromServerBackupResult
  }

  override suspend fun restoreFromPrefetchedBackup(
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<WalletMetadataSnapshot?, Error> {
    prefetchedRestoreCalls += accountId
    return restoreFromPrefetchedBackupResult
  }

  fun reset() {
    isRestoringFlow.value = false
    remediationFlow.value = WalletMetadataRemediation.None
    recordSealedSsekCalls.clear()
    provisionCalls.clear()
    provisionEnvironments.clear()
    requestBackupCalls.clear()
    syncPendingBackupsCalls.clear()
    fetchBackupCalls.clear()
    resolveSealedSsekCalls.clear()
    hasSsekCalls.clear()
    recordUnsealedSsekCalls.clear()
    restoreCalls.clear()
    prefetchedRestoreCalls.clear()
    recordSealedSsekResult = Ok(Unit)
    provisionResult = Ok(Unit)
    requestBackupResult = Ok(Unit)
    syncPendingBackupsResult = Ok(Unit)
    fetchBackupResult = Ok(null)
    resolveSealedSsekResult = Ok(null)
    hasSsekResult = Ok(true)
    recordUnsealedSsekResult = Ok(Unit)
    remediateWithUnsealedSsekResult = Ok(null)
    restoreFromServerBackupResult = Ok(null)
    restoreFromPrefetchedBackupResult = Ok(null)
  }
}
