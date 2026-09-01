package bitkey.recovery

import bitkey.account.AccountConfigService
import bitkey.f8e.account.WalletMetadataBackup
import bitkey.f8e.account.WalletMetadataBackupF8eClient
import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.bitcoin.metadata.WalletMetadataAccountId
import build.wallet.bitcoin.metadata.WalletMetadataDao
import build.wallet.bitcoin.metadata.WalletMetadataSnapshot
import build.wallet.bitcoin.metadata.WalletMetadataTombstone
import build.wallet.bitcoin.metadata.mergeWalletMetadataSnapshots
import build.wallet.bitcoin.metadata.walletMetadataUpdatedAt
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.catchingResult
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.encrypt.XCiphertext
import build.wallet.feature.flags.TransactionNotesFeatureFlag
import build.wallet.feature.flags.TransactionNotesSyncFrequencySecondsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.feature.isEnabled
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import build.wallet.store.removeWithResult
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
@Suppress("LargeClass")
class WalletMetadataServerBackupServiceImpl(
  private val accountService: AccountService,
  private val accountConfigService: AccountConfigService,
  private val walletMetadataBackupF8eClient: WalletMetadataBackupF8eClient,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val walletMetadataDao: WalletMetadataDao,
  private val ssekDao: SsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  private val transactionNotesFeatureFlag: TransactionNotesFeatureFlag,
  private val transactionNotesSyncFrequencySecondsFeatureFlag:
    TransactionNotesSyncFrequencySecondsFeatureFlag,
) : WalletMetadataServerBackupService, WalletMetadataServerBackupSyncWorker {
  private companion object {
    const val STORE_NAME = "WalletMetadataServerBackupSync"
    const val PENDING_ACCOUNT_IDS_KEY = "pending-account-ids"
    const val SEALED_SSEK_PREFIX = "sealed-ssek-"
    const val PENDING_TARGET_SSEK_PREFIX = "pending-target-ssek-"
    const val GENERATION_PREFIX = "backup-generation-"
  }

  /** Serializes fast local pending/generation bookkeeping writes. */
  private val stateMutex = Mutex()

  /** Serializes pull-merge-push passes from startup, periodic, and immediate triggers. */
  private val syncMutex = Mutex()

  /** Emits when a metadata edit requests an immediate sync pass instead of the next periodic tick. */
  private val immediateSyncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  override val retryStrategy: RetryStrategy = RetryStrategy.Always(delay = 5.minutes, retries = 12)
  override val runStrategy: Set<RunStrategy> = setOf(
    // App initialization can occur before AppSessionManager reports FOREGROUND. Waiting avoids
    // silently dropping the one startup pull that should make a restarted device converge.
    RunStrategy.Startup(backgroundStrategy = BackgroundStrategy.Wait),
    RunStrategy.OnEvent(
      observer = transactionNotesSyncFrequencySecondsFeatureFlag.flagValue()
        .flatMapLatest { flag ->
          flow {
            val interval = flag.value.toInt().coerceAtLeast(1).seconds
            while (currentCoroutineContext().isActive) {
              delay(interval)
              emit(Unit)
            }
          }
        },
      backgroundStrategy = BackgroundStrategy.Skip
    ),
    // The startup pull no-ops when the feature flag has not synced yet or no account is active
    // (e.g. a fresh install where recovery completes after launch). Re-run the sync when the flag
    // becomes enabled or a full account becomes active so those installs converge without waiting
    // for the next periodic tick.
    RunStrategy.OnEvent(
      // The initial emission is intentionally not dropped: the startup pass and this collector
      // start in separate coroutines, so the flag can become enabled after the startup pass
      // observed it disabled but before this collector subscribes. An extra pass when the flag
      // is already enabled is harmless — syncPendingBackups is serialized and idempotent.
      observer = transactionNotesFeatureFlag.flagValue()
        .map { it.value }
        .distinctUntilChanged()
        .filter { it },
      backgroundStrategy = BackgroundStrategy.Wait
    ),
    RunStrategy.OnEvent(
      // The initial emission is intentionally not dropped for the same reason as the flag
      // trigger above: an account can become active before this collector subscribes.
      observer = accountService.activeAccount()
        .map { it is FullAccount }
        .distinctUntilChanged()
        .filter { it },
      backgroundStrategy = BackgroundStrategy.Wait
    ),
    // Metadata edits request an immediate pass so local changes are backed up promptly rather
    // than waiting for the next periodic tick.
    RunStrategy.OnEvent(
      observer = immediateSyncRequests,
      backgroundStrategy = BackgroundStrategy.Wait
    )
  )

  private val aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // Server backups are a versioned interchange format. Keep version and empty collections
    // explicit so an empty-but-provisioned snapshot is unambiguous on every client version.
    encodeDefaults = true
  }
  private val restoring = MutableStateFlow(false)
  private val remediationState = MutableStateFlow<WalletMetadataRemediation>(
    WalletMetadataRemediation.None
  )

  override val isRestoring: StateFlow<Boolean> = restoring
  override val remediation: StateFlow<WalletMetadataRemediation> = remediationState

  override suspend fun recordSealedSsek(
    accountId: WalletMetadataAccountId,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error> =
    store()
      .putStringWithResult("$SEALED_SSEK_PREFIX${accountId.value}", sealedSsek.hex())
      .mapError { Error("Failed to persist sealed SSEK for wallet metadata server backup", it) }

  override suspend fun provision(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> = syncMutex.withLock {
    provisionWithinSync(
      accountId = accountId,
      sealedSsek = sealedSsek,
      f8eEnvironment = f8eEnvironment
    )
  }

  private suspend fun provisionWithinSync(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> = coroutineBinding {
    val metadataAccountId = WalletMetadataAccountId.fromAccountId(accountId)
    // Persist the desired/current key before network work. If an older remote backup cannot yet be
    // decrypted, remediation unseals that old key without replacing this desired rotation target.
    recordSealedSsek(metadataAccountId, sealedSsek).bind()
    recordPendingTargetSealedSsek(metadataAccountId, sealedSsek).bind()
    markPending(metadataAccountId).bind()
    mergeRemoteBeforeProvision(accountId, metadataAccountId, f8eEnvironment).bind()
    // Attempt provisioning immediately. The pending marker is deliberately written first, so a
    // failed PUT remains durable retry intent for the startup/periodic worker.
    uploadPendingSnapshot(accountId, metadataAccountId, f8eEnvironment).bind()
  }

  private suspend fun mergeRemoteBeforeProvision(
    accountId: FullAccountId,
    metadataAccountId: WalletMetadataAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> = coroutineBinding {
    val remote = walletMetadataBackupF8eClient
      .get(f8eEnvironment, accountId)
      .mapError { Error("Failed to fetch existing wallet metadata before provisioning", it) }
      .bind() ?: return@coroutineBinding
    val hasRemoteKey = ssekDao.get(remote.sealedSsek)
      .mapError { Error("Failed to read existing wallet metadata encryption key", it) }
      .bind() != null
    if (!hasRemoteKey) {
      remediationState.value = WalletMetadataRemediation.HardwareUnsealRequired(
        accountId = accountId,
        sealedSsek = remote.sealedSsek,
        targetSealedSsek = getRecordedSealedSsek(metadataAccountId).bind() ?: remote.sealedSsek
      )
      logWarn { "Wallet metadata rotation requires the previous SSEK to preserve remote data" }
      Err(Error("Previous wallet metadata SSEK must be unsealed before rotation can continue."))
        .bind<Unit>()
    }
    when (val decoded = unsealSnapshot(remote.sealedSsek, remote.sealedWalletMetadataSnapshot).bind()) {
      is DecodedSnapshot.UnsupportedVersion ->
        Err(Error("Cannot rotate an unsupported wallet metadata snapshot version."))
          .bind<Unit>()
      is DecodedSnapshot.Supported -> {
        if (decoded.hadInvalidRecords) {
          Err(Error("Existing wallet metadata contains malformed records; refusing destructive SSEK rotation."))
            .bind<Unit>()
        }
        if (decoded.snapshot.accountId != metadataAccountId) {
          Err(Error("Wallet metadata server backup belongs to a different account."))
            .bind<Unit>()
        }
        walletMetadataDao.mergeSnapshot(decoded.snapshot)
          .mapError { Error("Failed to preserve wallet metadata before SSEK rotation", it) }
          .bind()
      }
    }
  }

  override suspend fun fetchBackup(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, Error> =
    if (!transactionNotesFeatureFlag.isEnabled()) {
      Ok(null)
    } else {
      walletMetadataBackupF8eClient
        .get(f8eEnvironment, accountId)
        .mapError { Error("Failed to fetch wallet metadata server backup", it) }
    }

  override suspend fun resolveSealedSsek(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    metadataBackup: WalletMetadataBackup?,
  ): Result<SealedSsek?, Error> = coroutineBinding {
    metadataBackup?.sealedSsek ?: listKeysetsF8eClient.listKeysets(f8eEnvironment, accountId)
      .mapError { Error("Failed to fetch descriptor SSEK for wallet metadata recovery", it) }
      .bind()
      .let { response ->
        if (response.descriptorBackups.isNotEmpty() && response.wrappedSsek == null) {
          Err(Error("Private descriptor backups are missing their required wrapped SSEK."))
            .bind<SealedSsek?>()
        }
        // Legacy non-private wallets may legitimately have neither descriptor material nor an
        // SSEK. They remain unsupported for standalone metadata until a later migration mints one.
        response.wrappedSsek
      }
  }

  override suspend fun hasSsek(sealedSsek: SealedSsek): Result<Boolean, Error> =
    ssekDao.get(sealedSsek)
      .map { it != null }
      .mapError { Error("Failed to read server storage encryption key", it) }

  override suspend fun recordUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    ssek: Ssek,
  ): Result<Unit, Error> = coroutineBinding {
    ssekDao.set(sealedSsek, ssek)
      .mapError { Error("Failed to store server storage encryption key", it) }
      .bind()
    recordSealedSsek(WalletMetadataAccountId.fromAccountId(accountId), sealedSsek).bind()
  }

  override suspend fun executeWork() {
    // Transient failures throw for worker retry. Missing plaintext keys instead set [remediation]
    // and return successfully because only a foreground hardware interaction can resolve them.
    syncPendingBackups()
      .logFailure { "Failed to sync wallet metadata with server backup" }
      .getOrThrow()
  }

  override suspend fun remediateWithUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    ssek: Ssek,
  ): Result<WalletMetadataSnapshot?, Error> = coroutineBinding {
    val required = remediationState.value as? WalletMetadataRemediation.HardwareUnsealRequired
      ?: Err(Error("Wallet metadata no longer requires hardware remediation.")).bind()
    if (required.accountId != accountId || required.sealedSsek != sealedSsek) {
      Err(Error("Wallet metadata remediation key does not match the active account.")).bind<Unit>()
    }
    // Store the old remote key without changing the durable desired rotation target.
    ssekDao.set(sealedSsek, ssek)
      .mapError { Error("Failed to store server storage encryption key", it) }
      .bind()
    val restored = restoreFromServerBackup(accountId).bind()
    val durableTarget = getRecordedSealedSsek(WalletMetadataAccountId.fromAccountId(accountId))
      .bind() ?: required.targetSealedSsek
    remediationState.value = WalletMetadataRemediation.None
    if (durableTarget != sealedSsek) {
      // Foreground remediation only runs on an active account, so ambient config is correct here.
      provision(
        accountId = accountId,
        sealedSsek = durableTarget,
        f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      ).bind()
    }
    restored
  }

  override suspend fun requestBackup(accountId: WalletMetadataAccountId): Result<Unit, Error> =
    markPending(accountId)
      .also {
        // Ask the sync worker for an immediate pass. The durable pending marker above is the
        // source of truth; this emission is best-effort scheduling.
        immediateSyncRequests.tryEmit(Unit)
      }

  /**
   * Performs one pull-merge-push pass for the active full account. Pulling even when no local work
   * is pending lets active devices converge on the same note set. Fresh installations are
   * bootstrapped explicitly by the recovery flows (cloud restoration and lost-app recovery apply
   * a prefetched backup via [restoreFromPrefetchedBackup]/[restoreFromServerBackup]); this
   * periodic pull is a convergence pass and safety net, not the bootstrap mechanism. The merged
   * union is uploaded only when it differs from the remote snapshot.
   */
  @Suppress("CyclomaticComplexMethod")
  override suspend fun syncPendingBackups(): Result<Unit, Error> =
    syncMutex.withLock {
      coroutineBinding {
        if (!transactionNotesFeatureFlag.isEnabled()) return@coroutineBinding

        val activeFullAccount = accountService.getAccountOrNull<FullAccount>()
          .mapError { Error("Failed to read active account for wallet metadata server sync", it) }
          .bind()
          ?: return@coroutineBinding
        val metadataAccountId = WalletMetadataAccountId.fromAccountId(activeFullAccount.accountId)
        val generationBeforePull = stateMutex.withLock { generation(metadataAccountId.value) }.bind()
        val f8eEnvironment = activeFullAccount.config.f8eEnvironment
        val remoteBackup = walletMetadataBackupF8eClient
          .get(f8eEnvironment, activeFullAccount.accountId)
          .mapError { Error("Failed to fetch wallet metadata server backup", it) }
          .bind()

        if (remoteBackup == null) {
          // Existing-account backfill is complete inside this helper when the descriptor SSEK is
          // available. If it instead records remediation/unsupported state, stop here so that a
          // local pending marker does not turn missing key material into a retryable worker error.
          // Legacy accounts without descriptor SSEK may still continue with an already-recorded
          // metadata SSEK from this installation.
          val shouldStopAfterBackfill = ensureProvisionedForExistingAccount(
            accountId = activeFullAccount.accountId,
            metadataAccountId = metadataAccountId,
            f8eEnvironment = f8eEnvironment
          ).bind()
          if (shouldStopAfterBackfill) return@coroutineBinding
        } else {
          val pendingTargetSealedSsek = getPendingTargetSealedSsek(metadataAccountId).bind()
          val descriptorRotationTarget = descriptorRotationTarget(
            accountId = activeFullAccount.accountId,
            metadataAccountId = metadataAccountId,
            f8eEnvironment = f8eEnvironment,
            remoteSealedSsek = remoteBackup.sealedSsek,
            pendingTargetSealedSsek = pendingTargetSealedSsek
          ).bind()
          val hasRemoteKey = ssekDao.get(remoteBackup.sealedSsek)
            .mapError { Error("Failed to read server storage encryption key", it) }
            .bind() != null
          if (!hasRemoteKey) {
            remediationState.value = WalletMetadataRemediation.HardwareUnsealRequired(
              accountId = activeFullAccount.accountId,
              sealedSsek = remoteBackup.sealedSsek,
              // When the descriptor SSEK has moved past this backup's key, remediation should
              // finish the interrupted rotation instead of re-encrypting under the retired key.
              targetSealedSsek = descriptorRotationTarget ?: remoteBackup.sealedSsek
            )
            logWarn { "Wallet metadata sync requires hardware SSEK remediation" }
            return@coroutineBinding
          }
          remediationState.value = WalletMetadataRemediation.None
          when (val decoded = unsealSnapshot(remoteBackup.sealedSsek, remoteBackup.sealedWalletMetadataSnapshot).bind()) {
            is DecodedSnapshot.UnsupportedVersion -> {
              logWarn {
                "Skipping wallet metadata server backup with unsupported snapshot version"
              }
              return@coroutineBinding
            }
            is DecodedSnapshot.Supported -> {
              val remoteSnapshot = decoded.snapshot
              if (remoteSnapshot.accountId != metadataAccountId) {
                Err(Error("Wallet metadata server backup belongs to a different account."))
                  .bind<Unit>()
              }
              if (descriptorRotationTarget != null) {
                // The descriptor SSEK (sealed by the currently paired hardware) has moved past
                // this backup's key — an earlier rotation's metadata PUT never completed and the
                // local rotation marker did not survive (e.g. reinstall/recovery). Re-adopt the
                // descriptor key as the durable rotation target so this pass re-encrypts under it.
                recordSealedSsek(metadataAccountId, descriptorRotationTarget).bind()
                recordPendingTargetSealedSsek(metadataAccountId, descriptorRotationTarget).bind()
              } else if (pendingTargetSealedSsek == null || pendingTargetSealedSsek == remoteBackup.sealedSsek) {
                // Adopt the remote key only after successful decryption and account ownership
                // validation. Preserve an in-flight SSEK rotation target across transient PUT
                // failures so the retry does not silently fall back to the still-old remote key.
                recordSealedSsek(metadataAccountId, remoteBackup.sealedSsek).bind()
              }

              val merged = walletMetadataDao.mergeSnapshot(remoteSnapshot)
                .mapError { Error("Failed to merge wallet metadata server backup locally", it) }
                .bind()
              val normalizedRemote = mergeWalletMetadataSnapshots(null, remoteSnapshot)

              if (decoded.hadInvalidRecords) {
                // Preserve malformed remote records byte-for-byte by refusing to replace the
                // object. Valid records are merged locally, but no sanitized destructive PUT runs.
                logWarn { "Wallet metadata backup has malformed records; skipping server rewrite" }
                clearPendingIfGenerationUnchanged(metadataAccountId, generationBeforePull).bind()
                return@coroutineBinding
              }
              val isRotatingSsek = descriptorRotationTarget != null ||
                (pendingTargetSealedSsek != null && pendingTargetSealedSsek != remoteBackup.sealedSsek)
              if (merged != normalizedRemote || isRotatingSsek) {
                // Even when the records already match the remote object, a pending SSEK rotation
                // still needs a PUT so the server backup is re-encrypted under the target key.
                markPending(metadataAccountId).bind()
              } else {
                // The server already contains the merged result. Clear an older pending marker only
                // if no edit advanced the generation while the pull and merge were in progress.
                clearPendingIfGenerationUnchanged(metadataAccountId, generationBeforePull).bind()
              }
            }
          }
        }

        uploadPendingSnapshot(activeFullAccount.accountId, metadataAccountId, f8eEnvironment).bind()
      }
    }

  override suspend fun restoreFromServerBackup(
    accountId: FullAccountId,
  ): Result<WalletMetadataSnapshot?, Error> = coroutineBinding {
    restoring.value = true
    try {
      val f8eEnvironment = accountService.getAccountOrNull<FullAccount>()
        .mapError { Error("Failed to read active account for wallet metadata restore", it) }
        .bind()
        ?.takeIf { it.accountId == accountId }
        ?.config
        ?.f8eEnvironment
        ?: accountConfigService.defaultConfig().value.f8eEnvironment
      val backup = fetchBackup(f8eEnvironment, accountId).bind()
        ?: return@coroutineBinding null

      applyBackupLocally(accountId, backup).bind()
    } finally {
      restoring.value = false
    }
  }

  override suspend fun restoreFromPrefetchedBackup(
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<WalletMetadataSnapshot?, Error> = coroutineBinding {
    restoring.value = true
    try {
      applyBackupLocally(accountId, backup).bind()
    } finally {
      restoring.value = false
    }
  }

  /** Unseals, validates ownership, and merges a recovery-prefetched snapshot locally. */
  private suspend fun applyBackupLocally(
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<WalletMetadataSnapshot?, Error> = coroutineBinding {
    when (val decoded = unsealSnapshot(backup.sealedSsek, backup.sealedWalletMetadataSnapshot).bind()) {
      is DecodedSnapshot.UnsupportedVersion -> {
        logWarn { "Skipping wallet metadata restore with unsupported snapshot version" }
        null
      }
      is DecodedSnapshot.Supported -> {
        val snapshot = decoded.snapshot
        val expectedAccountId = WalletMetadataAccountId.fromAccountId(accountId)
        if (snapshot.accountId != expectedAccountId) {
          Err(Error("Wallet metadata server backup belongs to a different account."))
            .bind<WalletMetadataSnapshot?>()
        }

        val merged = walletMetadataDao.mergeSnapshot(snapshot)
          .mapError { Error("Failed to merge wallet metadata server backup locally", it) }
          .bind()
        logInfo { "Merged wallet metadata from standalone server backup" }
        merged
      }
    }
  }

  /**
   * Detects an interrupted SSEK rotation whose local rotation marker did not survive (e.g. the
   * app was reinstalled or recovered mid-rotation). The descriptor wrappedSsek is always sealed
   * by the currently paired hardware, so when it differs from the remote metadata backup's key,
   * the metadata backup is still encrypted under a retired key and must be re-rotated.
   *
   * Scoped to first adoption of the remote key: no descriptor fetch happens when a local
   * rotation is already in flight or the recorded key already matches the remote backup's key.
   * Returns the descriptor SSEK to rotate to only when its plaintext is available locally, so
   * steady-state syncs stay network-free and an unavailable target never blocks sync.
   */
  private suspend fun descriptorRotationTarget(
    accountId: FullAccountId,
    metadataAccountId: WalletMetadataAccountId,
    f8eEnvironment: F8eEnvironment,
    remoteSealedSsek: SealedSsek,
    pendingTargetSealedSsek: SealedSsek?,
  ): Result<SealedSsek?, Error> = coroutineBinding {
    // An in-flight local rotation already tracks its own durable target.
    if (pendingTargetSealedSsek != null) return@coroutineBinding null
    // Steady state: this installation has already reconciled against this remote key.
    val recorded = getRecordedSealedSsek(metadataAccountId).bind()
    if (recorded == remoteSealedSsek) return@coroutineBinding null

    val descriptorSsek = listKeysetsF8eClient.listKeysets(f8eEnvironment, accountId)
      .mapError { Error("Failed to fetch descriptor SSEK for wallet metadata sync", it) }
      .bind()
      .wrappedSsek
    if (descriptorSsek == null || descriptorSsek == remoteSealedSsek) {
      return@coroutineBinding null
    }
    val hasTargetKey = ssekDao.get(descriptorSsek)
      .mapError { Error("Failed to read server storage encryption key", it) }
      .bind() != null
    if (!hasTargetKey) {
      // Without the plaintext target key this installation cannot complete the rotation; fall
      // back to today's behavior rather than blocking sync on an impossible re-encryption.
      logWarn { "Descriptor SSEK moved past the metadata backup key, but is not available locally" }
      return@coroutineBinding null
    }
    descriptorSsek
  }

  private suspend fun ensureProvisionedForExistingAccount(
    accountId: FullAccountId,
    metadataAccountId: WalletMetadataAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Boolean, Error> = coroutineBinding {
    // A missing metadata backup always consults descriptor state, even if a local metadata
    // reference exists: descriptor wrappedSsek is the account's current source of truth when
    // present. Returns true when sync should stop because this method either uploaded or recorded
    // remediation/unsupported state; false means caller can proceed with an existing metadata SSEK.
    val descriptorState = listKeysetsF8eClient.listKeysets(f8eEnvironment, accountId)
      .mapError { Error("Failed to fetch descriptor SSEK for wallet metadata provisioning", it) }
      .bind()
    val descriptorSsek = descriptorState.wrappedSsek
    if (descriptorSsek == null) {
      if (descriptorState.descriptorBackups.isNotEmpty()) {
        Err(Error("Private descriptor backups are missing their required wrapped SSEK."))
          .bind<Boolean>()
      }
      if (getRecordedSealedSsek(metadataAccountId).bind() != null) {
        // The server backup is missing but this installation still has a usable metadata SSEK.
        // Mark pending so the caller's upload pass recreates the backup instead of no-oping on
        // an absent pending marker.
        markPending(metadataAccountId).bind()
        return@coroutineBinding false
      }
      remediationState.value = WalletMetadataRemediation.Unsupported(
        accountId = accountId,
        reason = "The account does not have a recoverable server storage encryption key."
      )
      logWarn { "Wallet metadata backfill is unsupported because descriptor SSEK is absent" }
      return@coroutineBinding true
    }
    val hasKey = ssekDao.get(descriptorSsek)
      .mapError { Error("Failed to read server storage encryption key", it) }
      .bind() != null
    if (!hasKey) {
      remediationState.value = WalletMetadataRemediation.HardwareUnsealRequired(
        accountId = accountId,
        sealedSsek = descriptorSsek
      )
      logWarn { "Wallet metadata backfill requires hardware SSEK remediation" }
      return@coroutineBinding true
    }
    remediationState.value = WalletMetadataRemediation.None
    provisionWithinSync(
      accountId = accountId,
      sealedSsek = descriptorSsek,
      f8eEnvironment = f8eEnvironment
    ).bind()
    true
  }

  private suspend fun uploadPendingSnapshot(
    accountId: FullAccountId,
    metadataAccountId: WalletMetadataAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> = coroutineBinding {
    val isPending = stateMutex.withLock { pendingAccountIds() }.bind()
      .contains(metadataAccountId.value)
    if (!isPending) return@coroutineBinding

    val generationAtUploadStart =
      stateMutex.withLock { generation(metadataAccountId.value) }.bind()
    uploadCurrentSnapshot(accountId, metadataAccountId, f8eEnvironment).bind()
    clearPendingTargetSealedSsek(metadataAccountId).bind()
    clearPendingIfGenerationUnchanged(metadataAccountId, generationAtUploadStart).bind()
  }

  private suspend fun uploadCurrentSnapshot(
    accountId: FullAccountId,
    metadataAccountId: WalletMetadataAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> = coroutineBinding {
    val snapshot = walletMetadataDao.snapshot(metadataAccountId)
      .mapError { Error("Failed to read wallet metadata snapshot for server backup", it) }
      .bind()
      ?: WalletMetadataSnapshot(
        accountId = metadataAccountId,
        updatedAt = Clock.System.now()
      )

    val sealedSsek = getRecordedSealedSsek(metadataAccountId).bind()
      ?: Err(Error("No sealed SSEK available for wallet metadata server backup.")).bind()
    val sealedSnapshot = sealSnapshot(sealedSsek, snapshot).bind()
    walletMetadataBackupF8eClient
      .put(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        backup = WalletMetadataBackup(
          sealedSsek = sealedSsek,
          sealedWalletMetadataSnapshot = sealedSnapshot
        )
      )
      .mapError { Error("Failed to upload wallet metadata server backup", it) }
      .bind()
  }

  private suspend fun getRecordedSealedSsek(
    accountId: WalletMetadataAccountId,
  ): Result<SealedSsek?, Error> =
    store()
      .getStringOrNullWithResult("$SEALED_SSEK_PREFIX${accountId.value}")
      .mapError { Error("Failed to read sealed SSEK for wallet metadata server backup", it) }
      .map { sealedSsekHex -> sealedSsekHex?.decodeHex() }

  private suspend fun recordPendingTargetSealedSsek(
    accountId: WalletMetadataAccountId,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error> =
    store()
      .putStringWithResult("$PENDING_TARGET_SSEK_PREFIX${accountId.value}", sealedSsek.hex())
      .mapError { Error("Failed to persist target SSEK for wallet metadata server backup", it) }

  private suspend fun getPendingTargetSealedSsek(
    accountId: WalletMetadataAccountId,
  ): Result<SealedSsek?, Error> =
    store()
      .getStringOrNullWithResult("$PENDING_TARGET_SSEK_PREFIX${accountId.value}")
      .mapError { Error("Failed to read target SSEK for wallet metadata server backup", it) }
      .map { sealedSsekHex -> sealedSsekHex?.decodeHex() }

  private suspend fun clearPendingTargetSealedSsek(
    accountId: WalletMetadataAccountId,
  ): Result<Unit, Error> =
    store()
      .removeWithResult("$PENDING_TARGET_SSEK_PREFIX${accountId.value}")
      .mapError { Error("Failed to clear target SSEK for wallet metadata server backup", it) }

  private suspend fun sealSnapshot(
    sealedSsek: SealedSsek,
    snapshot: WalletMetadataSnapshot,
  ): Result<XCiphertext, Error> = coroutineBinding {
    val ssek = ssekDao.get(sealedSsek).getOrElse {
      Err(Error("Failed to read server storage encryption key", it)).bind()
    } ?: Err(Error("Server storage encryption key not found.")).bind()

    catchingResult {
      symmetricKeyEncryptor.seal(
        unsealedData = json.encodeToString(snapshot.toRaw()).encodeUtf8(),
        key = ssek.key,
        aad = aad
      )
    }.mapError { Error("Failed to seal wallet metadata snapshot", it) }.bind()
  }

  private suspend fun unsealSnapshot(
    sealedSsek: SealedSsek,
    sealedSnapshot: XCiphertext,
  ): Result<DecodedSnapshot, Error> = coroutineBinding {
    val ssek = ssekDao.get(sealedSsek).getOrElse {
      Err(Error("Failed to read server storage encryption key", it)).bind()
    } ?: Err(Error("Server storage encryption key not found.")).bind()

    val snapshotJson = catchingResult {
      symmetricKeyEncryptor.unseal(
        ciphertext = sealedSnapshot,
        key = ssek.key,
        aad = aad
      ).utf8()
    }.mapError { Error("Failed to unseal wallet metadata snapshot", it) }.bind()

    // Inspect only the top-level version before decoding the known schema. A future version may
    // change record shapes, but must still be skipped without us dropping unknown fields on upload.
    val version = catchingResult {
      json.parseToJsonElement(snapshotJson).jsonObject["version"]?.jsonPrimitive?.content?.toUInt()
        ?: WalletMetadataSnapshot.CURRENT_VERSION
    }.mapError { Error("Failed to inspect wallet metadata snapshot version", it) }.bind()
    if (version > WalletMetadataSnapshot.CURRENT_VERSION) {
      return@coroutineBinding DecodedSnapshot.UnsupportedVersion(version)
    }

    val raw = catchingResult { json.decodeFromString<RawWalletMetadataSnapshot>(snapshotJson) }
      .mapError { Error("Failed to decode wallet metadata snapshot", it) }
      .bind()

    val accountId = catchingResult { WalletMetadataAccountId(raw.accountId) }
      .mapError { Error("Wallet metadata snapshot has an invalid account id", it) }
      .bind()
    var invalidRecordCount = 0
    val notes = raw.transactionNotes.mapNotNull { rawNote ->
      catchingResult {
        TransactionNote(
          transactionId = BitcoinTransactionId(rawNote.transactionId),
          note = rawNote.note,
          createdAt = Instant.parse(rawNote.createdAt),
          updatedAt = Instant.parse(rawNote.updatedAt)
        )
      }.getOrElse {
        invalidRecordCount += 1
        null
      }
    }.toSet()
    val tombstones = raw.tombstones.mapNotNull { rawTombstone ->
      catchingResult {
        WalletMetadataTombstone.DeletedTransactionNote(
          transactionId = BitcoinTransactionId(rawTombstone.transactionId),
          deletedAt = Instant.parse(rawTombstone.deletedAt)
        )
      }.getOrElse {
        invalidRecordCount += 1
        null
      }
    }.toSet()
    if (invalidRecordCount > 0) {
      logWarn { "Skipped $invalidRecordCount invalid wallet metadata record(s) during sync" }
    }

    val updatedAt = walletMetadataUpdatedAt(notes, tombstones)
      ?: raw.updatedAt?.let { encoded ->
        catchingResult { Instant.parse(encoded) }.get()
      }
      ?: Instant.fromEpochMilliseconds(0)
    DecodedSnapshot.Supported(
      snapshot = WalletMetadataSnapshot(
        version = raw.version,
        accountId = accountId,
        transactionNotes = notes,
        tombstones = tombstones,
        updatedAt = updatedAt
      ),
      hadInvalidRecords = invalidRecordCount > 0
    )
  }

  private suspend fun markPending(
    accountId: WalletMetadataAccountId,
  ): Result<Unit, Error> = stateMutex.withLock {
    coroutineBinding {
      val pending = pendingAccountIds().bind().toMutableSet()
      pending += accountId.value
      setPendingAccountIds(pending).bind()
      bumpGeneration(accountId.value).bind()
    }
  }

  private suspend fun clearPendingIfGenerationUnchanged(
    accountId: WalletMetadataAccountId,
    expectedGeneration: Long,
  ): Result<Unit, Error> = stateMutex.withLock {
    coroutineBinding {
      val generationNow = generation(accountId.value).bind()
      if (generationNow == expectedGeneration) {
        val pending = pendingAccountIds().bind().toMutableSet()
        pending -= accountId.value
        setPendingAccountIds(pending).bind()
      } else {
        logInfo { "Wallet metadata changed during server sync; leaving backup pending for retry" }
      }
    }
  }

  private suspend fun store() = encryptedKeyValueStoreFactory.getOrCreate(STORE_NAME)

  private suspend fun pendingAccountIds(): Result<Set<String>, Error> =
    store()
      .getStringOrNullWithResult(PENDING_ACCOUNT_IDS_KEY)
      .mapError { Error("Failed to read pending wallet metadata backup state", it) }
      .map { pending ->
        pending?.split("\n")?.filter { it.isNotBlank() }?.toSet().orEmpty()
      }

  private suspend fun setPendingAccountIds(accountIds: Set<String>): Result<Unit, Error> =
    store()
      .putStringWithResult(PENDING_ACCOUNT_IDS_KEY, accountIds.sorted().joinToString("\n"))
      .mapError { Error("Failed to persist pending wallet metadata backup state", it) }

  private suspend fun generation(accountId: String): Result<Long, Error> =
    store()
      .getStringOrNullWithResult("$GENERATION_PREFIX$accountId")
      .mapError { Error("Failed to read wallet metadata backup generation", it) }
      .map { it?.toLongOrNull() ?: 0L }

  private suspend fun bumpGeneration(accountId: String): Result<Unit, Error> = coroutineBinding {
    val current = generation(accountId).bind()
    store()
      .putStringWithResult("$GENERATION_PREFIX$accountId", (current + 1).toString())
      .mapError { Error("Failed to persist wallet metadata backup generation", it) }
      .bind()
  }


  private fun WalletMetadataSnapshot.toRaw(): RawWalletMetadataSnapshot =
    RawWalletMetadataSnapshot(
      version = version,
      accountId = accountId.value,
      transactionNotes = transactionNotes.map { note ->
        RawTransactionNote(
          transactionId = note.transactionId.value,
          note = note.note,
          createdAt = note.createdAt.toString(),
          updatedAt = note.updatedAt.toString()
        )
      },
      tombstones = tombstones
        .filterIsInstance<WalletMetadataTombstone.DeletedTransactionNote>()
        .map { tombstone ->
          RawDeletedTransactionNote(
            transactionId = tombstone.transactionId.value,
            deletedAt = tombstone.deletedAt.toString()
          )
        },
      updatedAt = updatedAt.toString()
    )

  private sealed interface DecodedSnapshot {
    data class Supported(
      val snapshot: WalletMetadataSnapshot,
      val hadInvalidRecords: Boolean,
    ) : DecodedSnapshot

    data class UnsupportedVersion(val version: UInt) : DecodedSnapshot
  }

  @Serializable
  private data class RawWalletMetadataSnapshot(
    val version: UInt = WalletMetadataSnapshot.CURRENT_VERSION,
    val accountId: String,
    val transactionNotes: List<RawTransactionNote> = emptyList(),
    val tombstones: List<RawDeletedTransactionNote> = emptyList(),
    val updatedAt: String? = null,
  )

  @Serializable
  private data class RawTransactionNote(
    val transactionId: String,
    val note: String,
    val createdAt: String,
    val updatedAt: String,
  )

  @Serializable
  private data class RawDeletedTransactionNote(
    val transactionId: String,
    val deletedAt: String,
  )
}
