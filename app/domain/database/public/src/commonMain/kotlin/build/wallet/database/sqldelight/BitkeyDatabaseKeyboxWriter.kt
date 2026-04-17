package build.wallet.database.sqldelight

import build.wallet.bitkey.keybox.Keybox

/**
 * Saves [keybox] and sets its account as the active full account.
 *
 * Delegates to [saveKeybox] for the insert, then marks the account active.
 * Designed to be called inside an existing transaction when other writes must
 * be atomic with keybox activation (e.g. [W3UpgradeCheckpointWriter]).
 */
fun BitkeyDatabase.saveKeyboxAsActive(keybox: Keybox) {
  saveKeybox(keybox)
  fullAccountQueries.setActiveFullAccountId(keybox.fullAccountId)
}

/**
 * Inserts a [Keybox] and all of its key bundles and spending keysets into the database.
 *
 * This is a pure insert with no active-account side effects. Extracted from [KeyboxDaoImpl]
 * so it can be reused inside composite transactions (see [saveKeyboxAsActive]).
 */
fun BitkeyDatabase.saveKeybox(keybox: Keybox) {
  fullAccountQueries.insertFullAccount(
    accountId = keybox.fullAccountId
  )

  keyboxQueries.insertKeybox(
    id = keybox.localId,
    accountId = keybox.fullAccountId,
    appGlobalAuthKeyHwSignature = keybox.appGlobalAuthKeyHwSignature,
    networkType = keybox.config.bitcoinNetworkType,
    fakeHardware = keybox.config.isHardwareFake,
    hardwareType = keybox.config.hardwareType,
    f8eEnvironment = keybox.config.f8eEnvironment,
    isTestAccount = keybox.config.isTestAccount,
    isUsingSocRecFakes = keybox.config.isUsingSocRecFakes,
    delayNotifyDuration = keybox.config.delayNotifyDuration,
    canUseKeyboxKeysets = keybox.canUseKeyboxKeysets
  )

  appKeyBundleQueries.insertKeyBundle(
    id = keybox.activeAppKeyBundle.localId,
    keyboxId = keybox.localId,
    globalAuthKey = keybox.activeAppKeyBundle.authKey,
    spendingKey = keybox.activeAppKeyBundle.spendingKey,
    recoveryAuthKey = keybox.activeAppKeyBundle.recoveryAuthKey,
    isActive = true
  )

  hwKeyBundleQueries.insertKeyBundle(
    id = keybox.activeHwKeyBundle.localId,
    keyboxId = keybox.localId,
    authKey = keybox.activeHwKeyBundle.authKey,
    spendingKey = keybox.activeHwKeyBundle.spendingKey,
    isActive = true
  )

  if (keybox.keysets.isNotEmpty()) {
    keybox.keysets.forEach { keyset ->
      spendingKeysetQueries.insertKeyset(
        id = keyset.localId,
        keyboxId = keybox.localId,
        appKey = keyset.appKey,
        hardwareKey = keyset.hardwareKey,
        serverKey = keyset.f8eSpendingKeyset,
        isActive = keyset.localId == keybox.activeSpendingKeyset.localId
      )
    }
  } else {
    spendingKeysetQueries.insertKeyset(
      id = keybox.activeSpendingKeyset.localId,
      keyboxId = keybox.localId,
      appKey = keybox.activeSpendingKeyset.appKey,
      hardwareKey = keybox.activeSpendingKeyset.hardwareKey,
      serverKey = keybox.activeSpendingKeyset.f8eSpendingKeyset,
      isActive = true
    )
  }
}
