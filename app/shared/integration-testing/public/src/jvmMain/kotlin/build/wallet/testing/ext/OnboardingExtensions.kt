package build.wallet.testing.ext

import bitkey.account.LiteAccountConfig
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.testing.AppTester
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
  fakeNfcCommands.wipeDevice()
  defaultAccountConfigService.apply {
    setBitcoinNetworkType(initialBitcoinNetworkType)
    setIsHardwareFake(true)
    setF8eEnvironment(initialF8eEnvironment)
    setIsTestAccount(true)
    setUsingSocRecFakes(isUsingSocRecFakes)
    setDelayNotifyDuration(delayNotifyDuration)
  }

  // Generate app keys
  val appKeys = onboardFullAccountService.createAppKeys().getOrThrow()
  // Activate hardware
  val hwActivation = startAndCompleteFingerprintEnrolment(appKeys.appKeyBundle.authKey)

  // Create f8e account
  val account = onboardFullAccountService.createAccount(
    context = CreateFullAccountContext.NewFullAccount,
    appKeys = appKeys,
    hwActivation = hwActivation
  ).getOrThrow()

  if (shouldSetUpNotifications) {
    val addedTouchpoint =
      notificationTouchpointF8eClient.addTouchpoint(
        f8eEnvironment = initialF8eEnvironment,
        accountId = account.accountId,
        touchpoint = NotificationTouchpoint.EmailTouchpoint(
          touchpointId = "",
          value = Email("integration-test@wallet.build") // This is a fake email
        )
      ).mapError { it.error }.getOrThrow()
    notificationTouchpointF8eClient.verifyTouchpoint(
      f8eEnvironment = initialF8eEnvironment,
      accountId = account.accountId,
      touchpointId = addedTouchpoint.touchpointId,
      verificationCode = "123456" // This code always works for Test Accounts
    ).mapError { it.error }.getOrThrow()
    notificationTouchpointF8eClient.activateTouchpoint(
      f8eEnvironment = initialF8eEnvironment,
      accountId = account.accountId,
      touchpointId = addedTouchpoint.touchpointId,
      hwFactorProofOfPossession = null
    ).getOrThrow()
  }

  if (cloudStoreAccountForBackup != null) {
    val backup = fullAccountCloudBackupCreator
      .create(
        keybox = account.keybox,
        sealedCsek = hwActivation.sealedCsek
      )
      .getOrThrow()
    cloudBackupRepository.writeBackup(
      account.accountId,
      cloudStoreAccountForBackup,
      backup,
      true
    )
      .getOrThrow()
    (cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(cloudStoreAccountForBackup)
      .getOrThrow()
  }

  // Mark account as active
  onboardFullAccountService.activateAccount(keybox = account.keybox).getOrThrow()

  return account
}

/**
 * Onboard Lite Account by accepting a Recovery Contact invitation.
 */
suspend fun AppTester.onboardLiteAccountFromInvitation(
  inviteCode: String,
  protectedCustomerName: String,
  cloudStoreAccountForBackup: CloudStoreAccountFake? = null,
): LiteAccount {
  // Create Lite Account
  val account =
    createLiteAccountService
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
  accountService.setActiveAccount(account).getOrThrow()

  // Accept RC invitation from Protected Customer
  val protectedCustomerAlias = ProtectedCustomerAlias(protectedCustomerName)
  val invitation = relationshipsService
    .retrieveInvitation(account, inviteCode)
    .unwrap()
  val delegatedDecryptionKey =
    relationshipsKeysRepository.getOrCreateKey<DelegatedDecryptionKey>()
      .getOrThrow()
  val protectedCustomer = relationshipsService
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
    val backup = liteAccountCloudBackupCreator.create(account).getOrThrow()
    cloudBackupRepository.writeBackup(
      account.accountId,
      cloudStoreAccountForBackup,
      backup,
      true
    ).getOrThrow()
  }
  return account
}
