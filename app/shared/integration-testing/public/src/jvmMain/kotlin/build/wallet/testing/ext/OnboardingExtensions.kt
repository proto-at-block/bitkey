package build.wallet.testing.ext

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.unwrap
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Convenience method to get the application into a fully onboarded state with a Full Account.
 * There is an active spending keyset and an account created on the server.
 *
 * @param shouldSetUpNotifications Whether the account should be set up with
 * notifications as part of onboarding. If true, the F8eEnvironment will be [Staging].
 * @param cloudStoreAccountForBackup If provided, the fake cloud store account instance to use
 * for backing up the keybox. If none is provided, the keybox will not be backed up.
 */
suspend fun AppTester.onboardFullAccountWithFakeHardware(
  shouldSetUpNotifications: Boolean = false,
  cloudStoreAccountForBackup: CloudStoreAccountFake? = null,
  delayNotifyDuration: Duration = 1.seconds,
): FullAccount {
  fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
  app.apply {
    // Create Keybox config
    val fullAccountConfig =
      FullAccountConfig(
        bitcoinNetworkType = initialBitcoinNetworkType,
        isHardwareFake = true,
        f8eEnvironment = initialF8eEnvironment,
        isTestAccount = true,
        isUsingSocRecFakes = isUsingSocRecFakes,
        delayNotifyDuration = delayNotifyDuration
      )
    appComponent.templateFullAccountConfigDao.set(fullAccountConfig)

    // Generate app keys
    val appKeys =
      appComponent.appKeysGenerator.generateKeyBundle(fullAccountConfig.bitcoinNetworkType)
        .getOrThrow()

    // Start fingerprint enrollment, which is just a pairing attempt before fingerprint enrollment
    pairingTransactionProvider(
      networkType = fullAccountConfig.bitcoinNetworkType,
      appGlobalAuthPublicKey = appKeys.authKey,
      onSuccess = {},
      onCancel = {},
      isHardwareFake = fullAccountConfig.isHardwareFake
    ).let { transaction ->
      nfcTransactor.fakeTransact(
        transaction = transaction::session
      ).getOrThrow().also { transaction.onSuccess(it) }
    }

    // Generate hardware keys
    val hwActivation =
      pairingTransactionProvider(
        networkType = fullAccountConfig.bitcoinNetworkType,
        appGlobalAuthPublicKey = appKeys.authKey,
        onSuccess = {},
        onCancel = {},
        isHardwareFake = fullAccountConfig.isHardwareFake
      ).let { transaction ->
        nfcTransactor.fakeTransact(
          transaction = transaction::session
        ).getOrThrow().also { transaction.onSuccess(it) }
      }
    require(hwActivation is PairingTransactionResponse.FingerprintEnrolled)
    val appGlobalAuthKeyHwSignature = signChallengeWithHardware(
      appKeys.authKey.value
    ).let(::AppGlobalAuthKeyHwSignature)
    val keyCrossAppHwDraft = KeyCrossDraft.WithAppKeysAndHardwareKeys(
      appKeyBundle = appKeys,
      hardwareKeyBundle = hwActivation.keyBundle,
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      config = fullAccountConfig
    )

    // Create f8e account
    val account = fullAccountCreator.createAccount(keyCrossAppHwDraft).getOrThrow()
    println("Created account ${account.accountId}")

    if (shouldSetUpNotifications) {
      val addedTouchpoint =
        notificationTouchpointService.addTouchpoint(
          f8eEnvironment = fullAccountConfig.f8eEnvironment,
          fullAccountId = account.accountId,
          touchpoint =
            NotificationTouchpoint.EmailTouchpoint(
              touchpointId = "",
              value = Email("integration-test@wallet.build") // This is a fake email
            )
        ).mapError { it.error }.getOrThrow()
      notificationTouchpointService.verifyTouchpoint(
        f8eEnvironment = fullAccountConfig.f8eEnvironment,
        fullAccountId = account.accountId,
        touchpointId = addedTouchpoint.touchpointId,
        verificationCode = "123456" // This code always works for Test Accounts
      ).mapError { it.error }.getOrThrow()
      notificationTouchpointService.activateTouchpoint(
        f8eEnvironment = fullAccountConfig.f8eEnvironment,
        fullAccountId = account.accountId,
        touchpointId = addedTouchpoint.touchpointId,
        hwFactorProofOfPossession = null
      ).getOrThrow()
    }

    if (cloudStoreAccountForBackup != null) {
      val backup =
        app.fullAccountCloudBackupCreator.create(
          keybox = account.keybox,
          sealedCsek = hwActivation.sealedCsek,
          trustedContacts = emptyList()
        )
          .getOrThrow()
      app.cloudBackupRepository.writeBackup(
        account.accountId,
        cloudStoreAccountForBackup,
        backup,
        true
      )
        .getOrThrow()
      (app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
        .set(cloudStoreAccountForBackup)
        .getOrThrow()
    }

    onboardingService.completeOnboarding(
      f8eEnvironment = fullAccountConfig.f8eEnvironment,
      fullAccountId = account.accountId
    ).getOrThrow()

    appComponent.keyboxDao.activateNewKeyboxAndCompleteOnboarding(
      account.keybox
    ).getOrThrow()

    return account
  }
}

/**
 * Onboard Lite Account by accepting a Trusted Contact invitation.
 */
suspend fun AppTester.onboardLiteAccountFromInvitation(
  inviteCode: String,
  protectedCustomerName: String,
  cloudStoreAccountForBackup: CloudStoreAccountFake? = null,
): LiteAccount {
  app.run {
    // Create Lite Account
    val account =
      liteAccountCreator
        .createAccount(
          LiteAccountConfig(
            bitcoinNetworkType = initialBitcoinNetworkType,
            f8eEnvironment = initialF8eEnvironment,
            isTestAccount = true,
            isUsingSocRecFakes = isUsingSocRecFakes
          )
        )
        .getOrThrow()

    // Set Lite Account as active in the app
    appComponent.accountRepository.setActiveAccount(account).getOrThrow()

    // Accept TC invitation from Protected Customer
    val protectedCustomerAlias = ProtectedCustomerAlias(protectedCustomerName)
    val invitation =
      socRecRelationshipsRepository
        .retrieveInvitation(account, inviteCode)
        .unwrap()
    val delegatedDecryptionKey =
      socRecKeysRepository.getOrCreateKey<DelegatedDecryptionKey>()
        .getOrThrow()
    val protectedCustomer =
      socRecRelationshipsRepository
        .acceptInvitation(
          account,
          invitation,
          protectedCustomerAlias,
          delegatedDecryptionKey,
          inviteCode
        )
        .unwrap()
    protectedCustomer.alias.shouldBe(protectedCustomerAlias)

    if (cloudStoreAccountForBackup != null) {
      val backup =
        app.liteAccountCloudBackupCreator.create(account).getOrThrow()
      app.cloudBackupRepository.writeBackup(
        account.accountId,
        cloudStoreAccountForBackup,
        backup,
        true
      )
        .getOrThrow()
    }

    return account
  }
}
