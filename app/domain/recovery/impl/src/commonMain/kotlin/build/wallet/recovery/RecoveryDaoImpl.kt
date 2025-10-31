package build.wallet.recovery

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.ActiveServerRecoveryEntity
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.LocalRecoveryAttemptEntity
import build.wallet.database.sqldelight.RecoveryQueries
import build.wallet.db.DbError
import build.wallet.db.DbTransactionError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.logging.logError
import build.wallet.recovery.LocalRecoveryAttemptProgress.*
import build.wallet.recovery.Recovery.*
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.MaybeNoLongerRecovering
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@BitkeyInject(AppScope::class)
class RecoveryDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : RecoveryDao {
  override suspend fun setActiveServerRecovery(
    activeServerRecovery: ServerRecovery?,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        when (activeServerRecovery) {
          null -> {
            /*
            Whenever we get a null server recovery on sync, if we also have a local initiation
            attempt that had yet to see its server counterpart, we know the attempt did not
            succeed. So we'll delete the local recovery attempt. A very rare edge case exists where
            the call to initiate on FE is still in progress after a crash or app close, and if it succeeds,
            that recovery will show up at a later point after we've deleted our local recovery
            object. In this case, when the server recovery finally shows up, it will present as
            though some other person is trying to recovery. We will build a solution for that by
            making our initiation call transactional: stop all sync calls, and call initiate until
            we've successfully heard back from the server on its success.
             */
            recoveryQueries.clearActiveServerRecovery()
            recoveryQueries.purgeUnconfirmedLocalRecoveryAttempts()
          }
          else -> {
            recoveryQueries.setActiveServerRecovery(
              account = activeServerRecovery.fullAccountId,
              startTime = activeServerRecovery.delayStartTime,
              endTime = activeServerRecovery.delayEndTime,
              lostFactor = activeServerRecovery.lostFactor,
              destinationAppGlobalAuthKey = activeServerRecovery.destinationAppGlobalAuthPubKey,
              destinationAppRecoveryAuthKey = activeServerRecovery.destinationAppRecoveryAuthPubKey,
              destinationHardwareAuthKey = activeServerRecovery.destinationHardwareAuthPubKey
            )
          }
        }
      }
  }

  override fun activeRecovery() =
    flow {
      val queries = databaseProvider.database().recoveryQueries
      combine(
        queries.getLocalRecovery().asFlow(),
        queries.getServerRecovery().asFlow()
      ) { localRecoveryQuery, serverRecoveryQuery ->
        queries.awaitTransactionWithResult {
          val localRecoveryAttempt = localRecoveryQuery.executeAsOneOrNull()
          val activeServerRecovery = serverRecoveryQuery.executeAsOneOrNull()
          localRecoveryAttempt?.toRecovery(activeServerRecovery, queries)
            ?: activeServerRecovery?.let { SomeoneElseIsRecovering(it.lostFactor) }
            ?: NoActiveRecovery
        }
      }.distinctUntilChanged()
        .collect(::emit)
    }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      recoveryQueries.clear()
    }
  }

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, DbError> {
    return when (progress) {
      is CreatedPendingKeybundles -> markAsInitiated(progress)
      is AttemptingCompletion -> markAsAttemptingCompletion(progress)
      is CompletionAttemptFailedDueToServerCancellation -> markAsCompletionAttemptFailed()
      is RotatedAuthKeys -> markAuthKeysRotated()
      is CreatedSpendingKeys -> markSpendingKeysCreated(progress)
      is ActivatedSpendingKeys -> markSpendingKeysActivated()
      is UploadedDescriptorBackups -> markUploadedDescriptorBackups(progress)
      DdkBackedUp -> markDdkBackedUp()
      BackedUpToCloud -> markCloudBackedUp()
      SweepingFunds -> markSweepInProgress()
      is CompletedRecovery -> markFundsSwept(progress.keyboxToActivate)
    }
  }

  private suspend fun markAsInitiated(
    progress: CreatedPendingKeybundles,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        // We don't currently check to see if a local recovery object exists when starting fresh in
        // the app, thought it totally could if the app crashed during our initiation attempt.
        // In that scenario, the local initiation attempt presents itself as `NoRecovery` and
        // therefore starts us at the beginning, where we can begin a new attempt. While there
        // will likely be a more holistic check on app start in this scenario which polls for the
        // active server recovery and deletes the local one if null, blocking the app on a network
        // call is no easy feat. So this is a backstop that will guarantee we don't have a local
        // recovery when starting the local recovery flow.

        // There are still edge cases that would lead us to do this when our local recovery actually
        // did succeed, but they should be rare, and the customer will be able to restart their
        // recovery.
        recoveryQueries.clearLocalRecoveryAttempt()
        recoveryQueries.markAsInitiated(
          account = progress.fullAccountId,
          destinationAppGlobalAuthKey = progress.appKeyBundle.authKey,
          destinationAppRecoveryAuthKey = progress.appKeyBundle.recoveryAuthKey,
          destinationHardwareAuthKey = progress.hwKeyBundle.authKey,
          destinationAppSpendingKey = progress.appKeyBundle.spendingKey,
          destinationHardwareSpendingKey = progress.hwKeyBundle.spendingKey,
          appGlobalAuthKeyHwSignature = progress.appGlobalAuthKeyHwSignature,
          lostFactor = progress.lostFactor
        )
      }
  }

  private suspend fun markAuthKeysRotated(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markAuthKeysRotated()
      }
  }

  private suspend fun markAsAttemptingCompletion(
    progress: AttemptingCompletion,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markAsAttemptingCompletion(progress.sealedCsek, progress.sealedSsek)
      }
  }

  private suspend fun markAsCompletionAttemptFailed(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markAttemptedCompletionAsFailed()
      }
  }

  private suspend fun markSpendingKeysCreated(
    progress: CreatedSpendingKeys,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markSpendingKeysCreated(
          progress.f8eSpendingKeyset
        )
      }
  }

  private suspend fun markSpendingKeysActivated(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markSpendingKeysActivated()
      }
  }

  private suspend fun markUploadedDescriptorBackups(
    progress: UploadedDescriptorBackups,
  ): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      // First clear any dangling recovery spending keysets, just in case.
      recoveryQueries.clearRecoverySpendingKeysets()

      // Mark uploaded before inserting new keysets
      recoveryQueries.markUploadedDescriptorBackups()

      // Insert all descriptors
      progress.spendingKeysets.forEach {
        recoveryQueries.insertRecoverySpendingKeyset(
          recoveryAttemptRowId = 0, // Using stable rowId,
          keysetLocalId = it.localId,
          appKey = it.appKey,
          hardwareKey = it.hardwareKey,
          serverKey = it.f8eSpendingKeyset,
          networkType = it.networkType
        )
      }
    }
  }

  private suspend fun markDdkBackedUp(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markDdkBackedUp()
      }
  }

  private suspend fun markCloudBackedUp(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markCloudBackedUp()
      }
  }

  private suspend fun markSweepInProgress(): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.markSweepInProgress()
      }
  }

  private suspend fun markFundsSwept(keyboxToActivate: Keybox): Result<Unit, DbTransactionError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryQueries.clearLocalRecoveryAttempt()
        saveKeyboxAsActive(keyboxToActivate)
      }
  }
}

private fun ActiveServerRecoveryEntity.toServerRecovery(): ServerRecovery {
  return ServerRecovery(
    fullAccountId = account,
    delayStartTime = startTime,
    delayEndTime = endTime,
    lostFactor = lostFactor,
    destinationAppGlobalAuthPubKey = destinationAppGlobalAuthKey,
    destinationAppRecoveryAuthPubKey = destinationAppRecoveryAuthKey,
    destinationHardwareAuthPubKey = destinationHardwareAuthKey
  )
}

/**
 * Local recovery attempts can be either:
 *
 * • server-dependent (prior to their completion on the server, their success is dependent on the
 *   server recognizing it as the active one).
 * • server-independent (they've already been completed on the server so they're free to move
 *   forward regardless of what happens on the server).
 *
 * This method converts a local recovery attempt to one of these two representations.
 */
private fun LocalRecoveryAttemptEntity.toRecovery(
  serverRecovery: ActiveServerRecoveryEntity?,
  queries: RecoveryQueries,
): Recovery {
  // If our recovery has moved past server completion, then it's considered server-independent.
  // This state trumps all and doesn't matter if the server recovery exists, since if one
  // does pop up, it shouldn't stop us from trying to recover. In the rare scenario where someone
  // does complete a recovery after we complete it on the server but before we finish recovering
  // funds on the client, our uncompleted recovery is doomed anyway, because the customer no longer
  // has an active device, so the server won't cooperate with a sweep.

  // TODO(W-4229)
  // In the scenario above, let the customer exit funds recovery process in Lost Hardware
  // so they at least go back to money home and if they happen to find their old hardware, they could
  // do app/HW spends.

  // TODO(W-4229)
  // In the scenario above, let the customer exit back to the onboarding home screen recovery
  // so they could try a new recovery.
  when (val serverIndependentRecovery = toServerIndependentRecovery(queries)) {
    null -> Unit
    else -> {
      return serverIndependentRecovery
    }
  }

  return if (serverRecovery == null) {
    serverRecoveryMissing()
  } else {
    serverRecoveryPresent(serverRecovery, appGlobalAuthKeyHwSignature)
  }
}

/**
 * Converts a local recovery attempt into the proper representation if the server recovery is present.
 */
private fun LocalRecoveryAttemptEntity.serverRecoveryPresent(
  serverRecovery: ActiveServerRecoveryEntity,
  appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
): Recovery {
  val serverDependentRecovery =
    ServerDependentRecovery.InitiatedRecovery(
      fullAccountId = account,
      appSpendingKey = destinationAppSpendingKey,
      appGlobalAuthKey = destinationAppGlobalAuthKey,
      appRecoveryAuthKey = destinationAppRecoveryAuthKey,
      hardwareSpendingKey = destinationHardwareSpendingKey,
      hardwareAuthKey = destinationHardwareAuthKey,
      factorToRecover = lostFactor,
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      serverRecovery = serverRecovery.toServerRecovery()
    )

  val hwKeysMatch = serverRecovery.destinationHardwareAuthKey == destinationHardwareAuthKey
  val appKeysMatch =
    serverRecovery.destinationAppGlobalAuthKey == destinationAppGlobalAuthKey &&
      serverRecovery.destinationAppRecoveryAuthKey == destinationAppRecoveryAuthKey

  return when (hwKeysMatch && appKeysMatch) {
    true -> serverDependentRecovery
    false -> NoLongerRecovering(serverRecovery.lostFactor)
  }
}

/**
 * Converts a local recovery attempt into the proper representation if the server recovery is null.
 */
private fun LocalRecoveryAttemptEntity.serverRecoveryMissing(): Recovery {
  // We set the sealed Csek just before we attempt completion. So we know if this is present then
  // the absence of a server recovery means that we either successfully completed or we didn't and
  // now someone else has canceled ours.
  sealedCsek?.let {
    return MaybeNoLongerRecovering(
      fullAccountId = account,
      appSpendingKey = destinationAppSpendingKey,
      appGlobalAuthKey = destinationAppGlobalAuthKey,
      appRecoveryAuthKey = destinationAppRecoveryAuthKey,
      hardwareSpendingKey = destinationHardwareSpendingKey,
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      hardwareAuthKey = destinationHardwareAuthKey,
      factorToRecover = lostFactor,
      sealedCsek = it,
      sealedSsek = sealedSsek
    )
  }

  // Here, we have the following state: a recovery attempt was in progress -- either
  // before we know the results of our server call or after. And there is currently no server
  // recovery present. If `hadServerRecovery` is true, a server recovery was present but has
  // not gone away. Otherwise, we haven't yet seen a server recovery. So we still don't know
  // if the recovery worked, so we return `NoActiveRecovery`.
  return if (hadServerRecovery) {
    NoLongerRecovering(lostFactor)
  } else {
    NoActiveRecovery
  }
}

/**
 * Local recovery attempts can be either:
 *
 * • server-dependent (prior to their completion on the server, their success is dependent on the
 *   server recognizing it as the active one).
 * • server-independent (they've already been completed on the server so they're free to move
 *   forward regardless of what happens on the server).
 *
 * This method converts a local recovery attempt to a server-independent recovery attempt if that's
 * what the local one represents. If the local recovery attempt is not server-dependent,
 * then it returns null.
 */
private fun LocalRecoveryAttemptEntity.toServerIndependentRecovery(
  queries: RecoveryQueries,
): ServerIndependentRecovery? {
  if (authKeysRotated) {
    if (spendingKeysCreated()) {
      // Get the local set of keysets from the recovery table; this is all active + inactive keysets
      // for the account, including the one created by this recovery.
      val storedKeysets = queries.getRecoverySpendingKeysets()
        .executeAsList()
        .map {
          SpendingKeyset(
            localId = it.keysetLocalId,
            f8eSpendingKeyset = it.serverKey,
            appKey = it.appKey,
            hardwareKey = it.hardwareKey,
            networkType = it.networkType
          )
        }

      val serverF8eSpendingKeyset = serverSpendingKey
      checkNotNull(serverF8eSpendingKeyset) {
        "serverSpendingKey should not be null when spendingKeysCreated() returns true"
      }

      if (sweepInProgress) {
        return ServerIndependentRecovery.SweepAttempted(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          factorToRecover = lostFactor,
          keysets = storedKeysets
        )
      }

      if (backedUpToCloud) {
        return ServerIndependentRecovery.BackedUpToCloud(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          factorToRecover = lostFactor,
          keysets = storedKeysets
        )
      }

      if (ddkBackedUp) {
        return ServerIndependentRecovery.DdkBackedUp(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          factorToRecover = lostFactor,
          sealedCsek = sealedCsek!!,
          sealedSsek = sealedSsek,
          keysets = storedKeysets
        )
      }

      if (spendingKeysActivated) {
        return ServerIndependentRecovery.ActivatedSpendingKeys(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          factorToRecover = lostFactor,
          sealedCsek = sealedCsek!!,
          sealedSsek = sealedSsek,
          keysets = storedKeysets
        )
      }

      if (uploadedDescriptorBackups) {
        return ServerIndependentRecovery.UploadedDescriptorBackups(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          factorToRecover = lostFactor,
          sealedCsek = sealedCsek!!,
          sealedSsek = sealedSsek,
          keysets = storedKeysets
        )
      } else {
        // Spending keys have been created but not yet activated
        return ServerIndependentRecovery.CreatedSpendingKeys(
          f8eSpendingKeyset = serverF8eSpendingKeyset,
          fullAccountId = account,
          appSpendingKey = destinationAppSpendingKey,
          appGlobalAuthKey = destinationAppGlobalAuthKey,
          appRecoveryAuthKey = destinationAppRecoveryAuthKey,
          hardwareSpendingKey = destinationHardwareSpendingKey,
          hardwareAuthKey = destinationHardwareAuthKey,
          factorToRecover = lostFactor,
          sealedCsek = sealedCsek!!,
          sealedSsek = sealedSsek,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
        )
      }
    } else {
      return ServerIndependentRecovery.RotatedAuthKeys(
        fullAccountId = account,
        appSpendingKey = destinationAppSpendingKey,
        appGlobalAuthKey = destinationAppGlobalAuthKey,
        appRecoveryAuthKey = destinationAppRecoveryAuthKey,
        hardwareSpendingKey = destinationHardwareSpendingKey,
        hardwareAuthKey = destinationHardwareAuthKey,
        appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
        factorToRecover = lostFactor,
        sealedCsek = sealedCsek!!,
        sealedSsek = sealedSsek
      )
    }
  } else {
    return null
  }
}

private fun LocalRecoveryAttemptEntity.spendingKeysCreated(): Boolean {
  return serverSpendingKey != null && sealedCsek != null
}

private fun BitkeyDatabase.saveKeyboxAsActive(keybox: Keybox) {
  // Insert the full account
  fullAccountQueries.insertFullAccount(
    accountId = keybox.fullAccountId
  )

  // Then, insert the keybox which points to the account.
  keyboxQueries.insertKeybox(
    id = keybox.localId,
    accountId = keybox.fullAccountId,
    appGlobalAuthKeyHwSignature = keybox.appGlobalAuthKeyHwSignature,
    networkType = keybox.config.bitcoinNetworkType,
    fakeHardware = keybox.config.isHardwareFake,
    f8eEnvironment = keybox.config.f8eEnvironment,
    isTestAccount = keybox.config.isTestAccount,
    isUsingSocRecFakes = keybox.config.isUsingSocRecFakes,
    delayNotifyDuration = keybox.config.delayNotifyDuration,
    canUseKeyboxKeysets = keybox.canUseKeyboxKeysets
  )

  // Insert the app key bundle
  appKeyBundleQueries.insertKeyBundle(
    id = keybox.activeAppKeyBundle.localId,
    keyboxId = keybox.localId,
    globalAuthKey = keybox.activeAppKeyBundle.authKey,
    spendingKey = keybox.activeAppKeyBundle.spendingKey,
    recoveryAuthKey = keybox.activeAppKeyBundle.recoveryAuthKey,
    isActive = true
  )

  // Insert the hw key bundle
  hwKeyBundleQueries.insertKeyBundle(
    id = keybox.activeHwKeyBundle.localId,
    keyboxId = keybox.localId,
    authKey = keybox.activeHwKeyBundle.authKey,
    spendingKey = keybox.activeHwKeyBundle.spendingKey,
    isActive = true
  )

  // Insert all keysets, if they're available
  if (keybox.keysets.isNotEmpty()) {
    val hardwareKeys = keybox.keysets.groupBy { it.hardwareKey }
    val appKeys = keybox.keysets.groupBy { it.appKey }
    val serverKeys = keybox.keysets.groupBy { it.f8eSpendingKeyset.spendingPublicKey }
    (hardwareKeys + appKeys + serverKeys)
      .filter { it.value.size > 1 }
      .map { it.value.map { it.localId } }
      .forEach {
        logError { "Duplicate keys found in keysets: [${it.joinToString()}]" }
      }
    for (keyset in keybox.keysets) {
      val isActive = keyset.localId == keybox.activeSpendingKeyset.localId
      spendingKeysetQueries.insertKeyset(
        id = keyset.localId,
        keyboxId = keybox.localId,
        appKey = keyset.appKey,
        hardwareKey = keyset.hardwareKey,
        serverKey = keyset.f8eSpendingKeyset,
        isActive = isActive
      )
    }
  } else {
    // Otherwise, just insert the active keyset.
    spendingKeysetQueries.insertKeyset(
      id = keybox.activeSpendingKeyset.localId,
      keyboxId = keybox.localId,
      appKey = keybox.activeSpendingKeyset.appKey,
      hardwareKey = keybox.activeSpendingKeyset.hardwareKey,
      serverKey = keybox.activeSpendingKeyset.f8eSpendingKeyset,
      isActive = true
    )
  }

  // Activate full account
  fullAccountQueries.setActiveFullAccountId(keybox.fullAccountId)
}
