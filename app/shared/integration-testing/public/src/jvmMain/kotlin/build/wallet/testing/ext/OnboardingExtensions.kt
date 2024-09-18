package build.wallet.testing.ext

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
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
import kotlinx.coroutines.flow.first
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
    appComponent.debugOptionsService.apply {
      setBitcoinNetworkType(initialBitcoinNetworkType)
      setIsHardwareFake(true)
      setF8eEnvironment(initialF8eEnvironment)
      setIsTestAccount(true)
      setUsingSocRecFakes(isUsingSocRecFakes)
      setDelayNotifyDuration(delayNotifyDuration)
    }

    // Generate app keys
    val appKeys = appComponent.appKeysGenerator.generateKeyBundle(initialBitcoinNetworkType)
      .getOrThrow()

    // Start fingerprint enrollment, which is just a pairing attempt before fingerprint enrollment
    pairingTransactionProvider(
      networkType = initialBitcoinNetworkType,
      appGlobalAuthPublicKey = appKeys.authKey,
      onSuccess = {},
      onCancel = {},
      isHardwareFake = true
    ).let { transaction ->
      nfcTransactor.fakeTransact(
        transaction = transaction::session
      ).getOrThrow().also { transaction.onSuccess(it) }
    }

    // Generate hardware keys
    val hwActivation =
      pairingTransactionProvider(
        networkType = initialBitcoinNetworkType,
        appGlobalAuthPublicKey = appKeys.authKey,
        onSuccess = {},
        onCancel = {},
        isHardwareFake = true
      ).let { transaction ->
        nfcTransactor.fakeTransact(
          transaction = transaction::session
        ).getOrThrow().also { transaction.onSuccess(it) }
      }
    require(hwActivation is PairingTransactionResponse.FingerprintEnrolled)
    val appGlobalAuthKeyHwSignature = signChallengeWithHardware(
      appKeys.authKey.value
    ).let(::AppGlobalAuthKeyHwSignature)
    val debugOptions = app.appComponent.debugOptionsService.options().first()
    val keyCrossAppHwDraft = KeyCrossDraft.WithAppKeysAndHardwareKeys(
      appKeyBundle = appKeys,
      hardwareKeyBundle = hwActivation.keyBundle,
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      config = debugOptions.toFullAccountConfig()
    )

    // Create f8e account
    val account = fullAccountCreator.createAccount(keyCrossAppHwDraft).getOrThrow()
    println("Created account ${account.accountId}")

    if (shouldSetUpNotifications) {
      val addedTouchpoint =
        appComponent.notificationTouchpointF8eClient.addTouchpoint(
          f8eEnvironment = initialF8eEnvironment,
          accountId = account.accountId,
          touchpoint = NotificationTouchpoint.EmailTouchpoint(
            touchpointId = "",
            value = Email("integration-test@wallet.build") // This is a fake email
          )
        ).mapError { it.error }.getOrThrow()
      appComponent.notificationTouchpointF8eClient.verifyTouchpoint(
        f8eEnvironment = initialF8eEnvironment,
        accountId = account.accountId,
        touchpointId = addedTouchpoint.touchpointId,
        verificationCode = "123456" // This code always works for Test Accounts
      ).mapError { it.error }.getOrThrow()
      appComponent.notificationTouchpointF8eClient.activateTouchpoint(
        f8eEnvironment = initialF8eEnvironment,
        accountId = account.accountId,
        touchpointId = addedTouchpoint.touchpointId,
        hwFactorProofOfPossession = null
      ).getOrThrow()
    }

    if (cloudStoreAccountForBackup != null) {
      val backup = app.fullAccountCloudBackupCreator
        .create(
          keybox = account.keybox,
          sealedCsek = hwActivation.sealedCsek
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

    onboardingF8eClient.completeOnboarding(
      f8eEnvironment = initialF8eEnvironment,
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
    appComponent.accountService.setActiveAccount(account).getOrThrow()

    // Accept TC invitation from Protected Customer
    val protectedCustomerAlias = ProtectedCustomerAlias(protectedCustomerName)
    val invitation = appComponent.socRecService
      .retrieveInvitation(account, inviteCode)
      .unwrap()
    val delegatedDecryptionKey =
      socRecKeysRepository.getOrCreateKey<DelegatedDecryptionKey>()
        .getOrThrow()
    val protectedCustomer = appComponent.socRecService
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
