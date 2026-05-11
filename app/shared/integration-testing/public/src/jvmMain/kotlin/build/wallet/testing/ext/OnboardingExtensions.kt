package build.wallet.testing.ext

import bitkey.account.HardwareType
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
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.nfc.platform.requireW3
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.unwrap
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Convenience method to get the application into a fully onboarded state with a Full Account.
 * There is an active spending keyset and an account created on the server.
 *
 * @param shouldSetUpNotifications Whether the account should be set up with
 * notifications as part of onboarding. If true, the F8eEnvironment will be [Staging].
 * @param shouldUploadDescriptorBackups Whether descriptor backups should be uploaded. Set this to
 * false to test accounts created prior to descriptor backups being introduced.
 * @param cloudStoreAccountForBackup If provided, the fake cloud store account instance to use
 * for backing up the keybox. If none is provided, the keybox will not be backed up.
 * @param hardwareType The hardware type to use for the fake hardware. If omitted, the helper
 * prefers the default app config's hardware type and falls back to W1.
 */
suspend fun AppTester.onboardFullAccountWithFakeHardware(
  shouldSetUpNotifications: Boolean = false,
  shouldUploadDescriptorBackups: Boolean = true,
  cloudStoreAccountForBackup: CloudStoreAccountFake? = null,
  delayNotifyDuration: Duration = 1.seconds,
  hardwareType: HardwareType? = null,
): FullAccount {
  fakeNfcCommands.wipeDevice()
  fakeW3NfcCommands.wipeDevice()

  defaultAccountConfigService.apply {
    setBitcoinNetworkType(initialBitcoinNetworkType)
    setIsHardwareFake(true)
    setF8eEnvironment(initialF8eEnvironment)
    setIsTestAccount(true)
    setUsingSocRecFakes(isUsingSocRecFakes)
    setDelayNotifyDuration(delayNotifyDuration)
    hardwareType?.let { setHardwareType(it) }
  }

  // Wait for StateFlow to be updated with the config changes we just made.
  val settledConfig = defaultAccountConfigService.defaultConfig().first { config ->
    config.delayNotifyDuration == delayNotifyDuration
  }

  val resolvedHardwareType =
    hardwareType ?: settledConfig.hardwareType ?: HardwareType.W1

  // Generate app keys
  val appKeys = onboardFullAccountService.createAppKeys().getOrThrow()
  val hwActivation = startAndCompleteFingerprintEnrolment(
    appAuthKey = appKeys.appKeyBundle.authKey,
    hardwareType = resolvedHardwareType
  )

  // Create f8e account
  var account = onboardFullAccountService.createAccount(
    context = CreateFullAccountContext.NewFullAccount,
    appKeys = appKeys,
    hwActivation = hwActivation
  ).getOrThrow()

  if (resolvedHardwareType == HardwareType.W3) {
    account = account.copy(
      keybox = signW3AppGlobalAuthKeyHwSignature(account.keybox, appKeys.appKeyBundle.authKey)
    )
  }

  if (shouldUploadDescriptorBackups) {
    descriptorBackupService.uploadOnboardingDescriptorBackup(
      accountId = account.accountId,
      sealedSsekForEncryption = hwActivation.sealedSsek,
      appAuthKey = appKeys.appKeyBundle.authKey,
      keysetsToEncrypt = account.keybox.keysets
    ).getOrThrow()
  }

  // W3 onboarding step: deliver the wallet descriptor to the hardware device.
  // In real onboarding, BuildHardwareDescriptorUiStateMachine drives this via NFC.
  // Here we call verifyKeysAndBuildDescriptor directly on the fake so it can
  // derive addresses and sign transactions.
  if (resolvedHardwareType == HardwareType.W3) {
    nfcTransactor.fakeTransact(hardwareType = HardwareType.W3) { session, commands ->
      commands.requireW3(session).verifyKeysAndBuildDescriptor(
        session = session,
        appSpendingKey = "00".repeat(33).decodeHex(),
        appSpendingKeyChaincode = "00".repeat(32).decodeHex(),
        networkMainnet = initialBitcoinNetworkType == build.wallet.bitcoin.BitcoinNetworkType.BITCOIN,
        appAuthKey = "00".repeat(33).decodeHex(),
        serverSpendingKey = "00".repeat(33).decodeHex(),
        serverSpendingKeyChaincode = "00".repeat(32).decodeHex(),
        wsmSignature = "00".repeat(64).decodeHex(),
      )
    }.getOrThrow()
  }

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

    val activationProof: PrivilegedActionProof? =
      if (resolvedHardwareType == HardwareType.W3) {
        val emailTouchpoint = addedTouchpoint as? NotificationTouchpoint.EmailTouchpoint
          ?: error("Expected email touchpoint during onboarding")

        buildW3HardwareActionProof(
          actionProofType = ActionProofType.SetRecoveryEmail(
            email = emailTouchpoint.value.value,
            touchpointId = emailTouchpoint.touchpointId
          ),
          appAuthKey = appKeys.appKeyBundle.authKey,
          accountId = account.accountId
        )
      } else {
        null
      }

    notificationTouchpointF8eClient.activateTouchpoint(
      f8eEnvironment = initialF8eEnvironment,
      accountId = account.accountId,
      touchpointId = addedTouchpoint.touchpointId,
      proof = activationProof
    ).getOrThrow()
  }

  if (cloudStoreAccountForBackup != null) {
    val backup = fullAccountCloudBackupCreator
      .create(
        keybox = account.keybox,
        sealedCsek = hwActivation.sealedCsek
      )
      .getOrThrow()
    cloudBackupService.writeBackup(
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

  // Verify the account was created with the expected hardware type
  account.keybox.config.hardwareType shouldBe resolvedHardwareType

  return account
}

/**
 * Onboard Lite Account by accepting a Trusted Contact invitation.
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

  // Accept TC invitation from Protected Customer
  val protectedCustomerAlias = ProtectedCustomerAlias(protectedCustomerName)
  val invitation = relationshipsService
    .retrieveInvitation(account, inviteCode, expectedRole = null)
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
    cloudBackupService.writeBackup(
      account.accountId,
      cloudStoreAccountForBackup,
      backup,
      true
    ).getOrThrow()
  }
  return account
}
