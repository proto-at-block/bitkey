package build.wallet.testing

import androidx.compose.runtime.Composable
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.auth.AuthTokenScope
import build.wallet.bdk.BdkAddressBuilderImpl
import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.BdkBumpFeeTxBuilderFactoryImpl
import build.wallet.bdk.BdkDescriptorFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.BdkPartiallySignedTransactionBuilderImpl
import build.wallet.bdk.BdkTxBuilderFactoryImpl
import build.wallet.bdk.BdkWalletFactoryImpl
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.blockchain.BlockchainControl
import build.wallet.bitcoin.blockchain.NoopBlockchainControl
import build.wallet.bitcoin.blockchain.RegtestControl
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.lightning.LightningInvoiceParserImpl
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.treasury.FundingResult
import build.wallet.bitcoin.treasury.TreasuryWallet
import build.wallet.bitcoin.treasury.TreasuryWalletFactory
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudKeyValueStoreImpl
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.cloudStoreAccountFakes
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.CloudStoreAccountRepositoryImpl
import build.wallet.cloud.store.CloudStoreServiceProviderFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.crypto.Spake2Impl
import build.wallet.datadog.DatadogRumMonitorImpl
import build.wallet.di.ActivityComponentImpl
import build.wallet.di.AppComponentImpl
import build.wallet.di.makeAppComponent
import build.wallet.email.Email
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.firmware.TeltraMock
import build.wallet.limit.SpendingLimit
import build.wallet.logging.log
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.FakeHardwareKeyStoreImpl
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.FakeHwAuthKeypair
import build.wallet.nfc.NfcCommandsFake
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.phonenumber.PhoneNumberLibBindingsImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProviderImpl
import build.wallet.platform.config.TouchpointPlatform.FcmTeam
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.pdf.PdfAnnotatorFactoryImpl
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.socrec.syncAndVerifyRelationships
import build.wallet.statemachine.cloud.CloudSignInUiStateMachineFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.store.EncryptedKeyValueStoreFactoryImpl
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val BITCOIN_NETWORK_ENV_VAR_NAME = "BITCOIN_NETWORK"
const val F8E_ENV_ENV_VAR_NAME = "F8E_ENVIRONMENT"

@Suppress("TooManyFunctions")
class AppTester(
  val app: ActivityComponentImpl,
  val fakeHardwareKeyStore: FakeHardwareKeyStore,
  val fakeNfcCommands: NfcCommandsFake,
  private val sharingManager: SharingManagerFake,
  private val blockchainControl: BlockchainControl,
  val treasuryWallet: TreasuryWallet,
  private val initialF8eEnvironment: F8eEnvironment,
  val initialBitcoinNetworkType: BitcoinNetworkType,
  val isUsingSocRecFakes: Boolean,
) {
  /**
   * Convenience method to get the application into a fully onboarded state with a Full Account.
   * There is an active spending keyset and an account created on the server.
   *
   * @param shouldSetUpNotifications Whether the account should be set up with
   * notifications as part of onboarding. If true, the F8eEnvironment will be [Staging].
   * @param cloudStoreAccountForBackup If provided, the fake cloud store account instance to use
   * for backing up the keybox. If none is provided, the keybox will not be backed up.
   */
  suspend fun onboardFullAccountWithFakeHardware(
    shouldSetUpNotifications: Boolean = false,
    cloudStoreAccountForBackup: CloudStoreAccountFake? = null,
    delayNotifyDuration: Duration = 1.seconds,
  ): FullAccount {
    fakeNfcCommands.clearHardwareKeys()
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
        appComponent.appKeysGenerator.generateKeyBundle(fullAccountConfig.bitcoinNetworkType).getOrThrow()

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
      require(hwActivation is FingerprintEnrolled)
      val appGlobalAuthKeyHwSignature = signChallengeWithHardware(
        appKeys.authKey.pubKey.value
      ).let(::AppGlobalAuthKeyHwSignature)
      val keyCrossAppHwDraft = WithAppKeysAndHardwareKeys(
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
   * Full Account creates a Trusted Contact [Invitation].
   */
  suspend fun createTcInvite(tcName: String): TrustedContactFullInvite {
    val account = getActiveFullAccount()
    val hwPop = getHardwareFactorProofOfPossession(account.keybox)
    val invitation = app.socRecRelationshipsRepository
      .createInvitation(
        account = account,
        trustedContactAlias = TrustedContactAlias(tcName),
        hardwareProofOfPossession = hwPop
      )
      .getOrThrow()
    val pakeData = app.socRecEnrollmentAuthenticationDao
      .getByRelationshipId(invitation.invitation.recoveryRelationshipId)
      .getOrThrow()
      .shouldNotBeNull()
    return TrustedContactFullInvite(
      invitation.inviteCode,
      IncomingInvitation(
        invitation.invitation.recoveryRelationshipId,
        invitation.invitation.code,
        pakeData.protectedCustomerEnrollmentPakeKey.copy(AppKey.fromPublicKey(pakeData.protectedCustomerEnrollmentPakeKey.publicKey.value))
      )
    )
  }

  data class TrustedContactFullInvite(
    val inviteCode: String,
    val invitation: IncomingInvitation,
  )

  /**
   * Onboard Lite Account by accepting a Trusted Contact invitation.
   */
  suspend fun onboardLiteAccountFromInvitation(
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
      val tcIdentityKey =
        socRecKeysRepository.getOrCreateKey(::DelegatedDecryptionKey).getOrThrow()
      val protectedCustomer =
        socRecRelationshipsRepository
          .acceptInvitation(
            account,
            invitation,
            protectedCustomerAlias,
            tcIdentityKey,
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

  /**
   * Attempts to do PAKE confirmation on all unendorsed TCs and then endorses and verifies
   * the key certificates for any that succeed.
   *
   * The app must already be logged into the cloud and have an existing cloud backup,
   * or method will fail.
   *
   * @param relationshipId the relationship that must be endorsed and verified, or this method
   *   will fail the test.
   */
  suspend fun endorseAndVerifyTc(relationshipId: String) =
    withClue("endorse and verify TC") {
      val account = getActiveFullAccount()
      // PAKE confirmation and endorsement
      val unendorsedTcs = app.socRecRelationshipsRepository.syncRelationshipsWithoutVerification(
        account.accountId,
        account.config.f8eEnvironment
      ).getOrThrow()
        .unendorsedTrustedContacts
      unendorsedTcs.first { it.recoveryRelationshipId == relationshipId }
      app.trustedContactKeyAuthenticator
        .authenticateAndEndorse(unendorsedTcs, account)

      // Verify endorsement
      app.socRecRelationshipsRepository.syncAndVerifyRelationships(account).getOrThrow()
        .trustedContacts
        .first { it.recoveryRelationshipId == relationshipId }
        .authenticationState
        .shouldBe(TrustedContactAuthenticationState.VERIFIED)

      app.bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackup(account).getOrThrow()
      app.cloudBackupDao.get(account.accountId.serverId).getOrThrow().shouldNotBeNull()
        .shouldBeTypeOf<CloudBackupV2>()
        .fullAccountFields.shouldNotBeNull()
        .socRecSealedDekMap
        .shouldHaveKey(relationshipId)
    }

  /**
   * Returns funds to the treasury wallet.
   */
  suspend fun returnFundsToTreasury(account: FullAccount) {
    app.apply {
      val spendingWallet =
        appComponent.appSpendingWalletProvider.getSpendingWallet(
          account.keybox.activeSpendingKeyset
        ).getOrThrow()

      spendingWallet.sync().getOrThrow()

      val appSignedPsbt =
        spendingWallet
          .createSignedPsbt(
            SpendingWallet.PsbtConstructionMethod.Regular(
              recipientAddress = treasuryWallet.getReturnAddress(),
              amount = SendAll,
              feePolicy = FeePolicy.MinRelayRate
            )
          )
          .getOrThrow()

      val appAndHwSignedPsbt =
        nfcTransactor.fakeTransact(
          transaction = { session, commands ->
            commands.signTransaction(session, appSignedPsbt, account.keybox.activeSpendingKeyset)
          }
        ).getOrThrow()
      bitcoinBlockchain.broadcast(appAndHwSignedPsbt).getOrThrow()
      mineBlock()
    }
  }

  /**
   * Mines a new block if on Regtest. Noop otherwise. Call this after sending transactions
   * to make tests runnable on Regtest and other networks. Mining rewards are sent to the Treasury.
   */
  suspend fun mineBlock() {
    blockchainControl.mineBlocks(1)
  }

  suspend fun getActiveAccount(): Account {
    val accountStatus = app.appComponent.accountRepository.accountStatus().first().getOrThrow()
    return (accountStatus as? ActiveAccount)?.account ?: error("active account not found")
  }

  /**
   * Returns and asserts the active keybox
   */
  suspend fun getActiveFullAccount(): FullAccount {
    return getActiveAccount() as? FullAccount ?: error("active Full Account not found")
  }

  /**
   * Add some funds from treasury to active Full account.
   *
   * Please return back to treasury later using [returnFundsToTreasury]!
   */
  suspend fun addSomeFunds(amount: BitcoinMoney = BitcoinMoney.sats(10_000L)): FundingResult {
    val keybox = getActiveFullAccount().keybox
    val wallet = app.appComponent.appSpendingWalletProvider
      .getSpendingWallet(keybox.activeSpendingKeyset)
      .getOrThrow()
    return treasuryWallet.fund(wallet, amount)
  }

  /**
   * Syncs wallet of active Full account until it has some funds - balance is not zero.
   */
  suspend fun waitForFunds() {
    val activeAccount = getActiveFullAccount()
    val activeWallet = app.appComponent.appSpendingWalletProvider
      .getSpendingWallet(activeAccount.keybox.activeSpendingKeyset)
      .getOrThrow()
    eventually(
      eventuallyConfig {
        duration = 60.seconds
        interval = 1.seconds
        initialDelay = 1.seconds
      }
    ) {
      activeWallet.sync().shouldBeOk()
      val balance = activeWallet.balance().first().shouldBeLoaded()
      balance.total.shouldBeGreaterThan(BitcoinMoney.sats(0))
      // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
    }
  }

  /**
   * Syncs wallet of active Full account.
   */
  suspend fun syncWallet() {
    val activeAccount = getActiveFullAccount()
    val activeWallet = app.appComponent.appSpendingWalletProvider
      .getSpendingWallet(activeAccount.keybox.activeSpendingKeyset)
      .getOrThrow()
    activeWallet.sync().shouldBeOk()
  }

  /**
   * Returns and asserts the active lite account
   */
  suspend fun getActiveLiteAccount(): LiteAccount {
    return getActiveAccount() as? LiteAccount ?: error("active Lite Account not found")
  }

  suspend fun getActiveAppGlobalAuthKey(): AppGlobalAuthKeypair {
    val account = getActiveFullAccount()
    val appGlobalAuthPublicKey = account.keybox.activeAppKeyBundle.authKey
    val appGlobalAuthPrivateKey =
      requireNotNull(
        app.appComponent.appPrivateKeyDao.getGlobalAuthKey(appGlobalAuthPublicKey).getOrThrow()
      )
    return AppGlobalAuthKeypair(appGlobalAuthPublicKey, appGlobalAuthPrivateKey)
  }

  suspend fun getActiveHwAuthKey(): FakeHwAuthKeypair {
    return fakeHardwareKeyStore.getAuthKeypair()
  }

  /**
   * Returns the active
   */
  suspend fun getActiveWallet(): SpendingWallet {
    val keybox = getActiveFullAccount().keybox
    return app.appComponent.appSpendingWalletProvider.getSpendingWallet(
      keybox.activeSpendingKeyset
    ).getOrThrow()
  }

  /**
   * Sets up mobile pay limits.
   */
  suspend fun setupMobilePay(
    account: FullAccount,
    limit: FiatMoney,
  ): SpendingLimit {
    return app.run {
      val accessToken =
        appComponent.authTokensRepository
          .getAuthTokens(account.accountId, AuthTokenScope.Global)
          .toErrorIfNull { IllegalStateException("Auth tokens missing.") }
          .getOrThrow()
          .accessToken
      val signResponse =
        nfcTransactor.fakeTransact { session, command ->
          command.signAccessToken(session, accessToken)
        }.getOrThrow()
      val spendingLimit = SpendingLimit(true, limit, TimeZone.UTC)
      spendingLimitService.setSpendingLimit(
        account.config.f8eEnvironment,
        account.accountId,
        spendingLimit,
        HwFactorProofOfPossession(signResponse)
      ).getOrThrow()
      spendingLimit
    }
  }

  suspend fun getHardwareFactorProofOfPossession(keybox: Keybox): HwFactorProofOfPossession {
    val accessToken =
      app.appComponent.authTokensRepository
        .getAuthTokens(keybox.fullAccountId, AuthTokenScope.Global)
        .toErrorIfNull { IllegalStateException("Auth tokens missing.") }
        .getOrThrow()
        .accessToken
    val signResponse =
      app.nfcTransactor.fakeTransact { session, command ->
        command.signAccessToken(session, accessToken)
      }.getOrThrow()
    return HwFactorProofOfPossession(signResponse)
  }

  /**
   * Signs some challenge with the fake hardware's auth private key.
   */
  suspend fun signChallengeWithHardware(challenge: ByteString): String {
    return app.nfcTransactor.fakeTransact { session, commands ->
      commands.signChallenge(session, challenge)
    }.getOrThrow()
  }

  suspend fun signChallengeWithHardware(challenge: String): String {
    return signChallengeWithHardware(challenge.encodeUtf8())
  }

  /**
   * Delete real cloud backups from fake, local cloud accounts.
   */
  suspend fun deleteBackupsFromFakeCloud() {
    cloudStoreAccountFakes.forEach { fakeCloudAccount ->
      app.cloudBackupRepository.clear(fakeCloudAccount, clearRemoteOnly = true)
    }
  }

  /**
   * Returns the last text sent to platform Share Sheet.
   */
  val lastSharedText get() = sharingManager.lastSharedText.value
}

/**
 * Creates a new [AppTester] that share data with an existing app instance.
 * It is not safe to continue using the previous [AppTester] instance after calling this method.
 */
fun AppTester.relaunchApp(
  bdkBlockchainFactory: BdkBlockchainFactory? = null,
  f8eEnvironment: F8eEnvironment? = null,
): AppTester =
  launchApp(
    existingAppDir = app.appComponent.fileDirectoryProvider.appDir(),
    bdkBlockchainFactory = bdkBlockchainFactory,
    f8eEnvironment = f8eEnvironment,
    cloudStoreAccountRepository = app.cloudStoreAccountRepository,
    cloudKeyValueStore = app.cloudKeyValueStore,
    isUsingSocRecFakes = isUsingSocRecFakes
  )

/**
 * Creates a brand new [AppTester].
 */
fun launchNewApp(
  bdkBlockchainFactory: BdkBlockchainFactory? = null,
  f8eEnvironment: F8eEnvironment? = null,
  bitcoinNetworkType: BitcoinNetworkType? = null,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  isUsingSocRecFakes: Boolean = false,
): AppTester =
  launchApp(
    existingAppDir = null,
    bdkBlockchainFactory,
    f8eEnvironment,
    bitcoinNetworkType,
    cloudStoreAccountRepository,
    cloudKeyValueStore,
    hardwareSeed,
    isUsingSocRecFakes
  )

/**
 * Creates an [AppTester] instance
 * @param existingAppDir Specify where application data (databases) should be saved.
 * If there is existing data in the directory, it will be used by the new app.
 */
@Suppress("NAME_SHADOWING")
private fun launchApp(
  existingAppDir: String? = null,
  bdkBlockchainFactory: BdkBlockchainFactory? = null,
  f8eEnvironment: F8eEnvironment? = null,
  bitcoinNetworkType: BitcoinNetworkType? = null,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  isUsingSocRecFakes: Boolean,
): AppTester {
  /**
   * Get the `F8eEnvironment` from the environment variables, falling back to local.
   * Should only be used when first setting up the keybox. Once the keybox is set up,
   * callers should use the environment from the keybox's config.
   */
  val f8eEnvironment =
    f8eEnvironment ?: System.getenv(F8E_ENV_ENV_VAR_NAME)?.let {
      F8eEnvironment.parseString(it)
    } ?: Local
  val bitcoinNetworkType =
    bitcoinNetworkType ?: System.getenv(BITCOIN_NETWORK_ENV_VAR_NAME)?.let {
      BitcoinNetworkType.valueOf(it.uppercase())
    } ?: REGTEST
  val bdkBlockchainFactory = bdkBlockchainFactory ?: BdkBlockchainFactoryImpl()

  val platformContext = initPlatform(existingAppDir)
  val appComponent = createAppComponent(platformContext, bdkBlockchainFactory)
  val blockchainControl = createBlockchainControl(bitcoinNetworkType)
  val fakeHardwareKeyStore =
    createFakeHardwareKeyStore(appComponent.secureStoreFactory, hardwareSeed)
  val fakeHardwareSpendingWalletProvider =
    FakeHardwareSpendingWalletProvider(
      spendingWalletProvider = appComponent.spendingWalletProvider,
      descriptorBuilder = appComponent.bitcoinMultiSigDescriptorBuilder,
      fakeHardwareKeyStore = fakeHardwareKeyStore
    )
  val fakeNfcCommands =
    NfcCommandsFake(
      messageSigner = MessageSignerImpl(),
      fakeHardwareKeyStore = fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider
    )
  val fakeSharingManager = SharingManagerFake()
  val activityComponent =
    createActivityComponent(
      appComponent = appComponent,
      fakeNfcCommands = fakeNfcCommands,
      sharingManager = fakeSharingManager,
      cloudStoreAccRepository = cloudStoreAccountRepository,
      cloudKeyValueStore = cloudKeyValueStore
    )

  val treasury =
    runBlocking {
      TreasuryWalletFactory(
        activityComponent.bitcoinBlockchain,
        blockchainControl,
        activityComponent.appComponent.spendingWalletProvider,
        BdkDescriptorSecretKeyFactoryImpl(),
        BdkDescriptorFactoryImpl()
      ).create(bitcoinNetworkType)
    }

  val fullAccountConfig =
    FullAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      isHardwareFake = true,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = true,
      isUsingSocRecFakes = isUsingSocRecFakes,
      delayNotifyDuration = 0.seconds
    )
  runBlocking {
    appComponent.templateFullAccountConfigDao.set(fullAccountConfig)
  }

  return AppTester(
    activityComponent,
    fakeHardwareKeyStore,
    fakeNfcCommands,
    fakeSharingManager,
    blockchainControl,
    treasury,
    f8eEnvironment,
    bitcoinNetworkType,
    isUsingSocRecFakes
  )
}

private fun initPlatform(existingAppDir: String?): PlatformContext {
  val appDir =
    if (existingAppDir != null) {
      existingAppDir
    } else {
      val rootDir = (System.getProperty("user.dir") + "/_build/bitkey/appdata")
      val now = Clock.System.now().toString()
      rootDir.join(now)
    }
  log { "App data directory is $appDir" }
  val platformContext = PlatformContext(appDirOverride = appDir)
  val fileDirectoryProvider = FileDirectoryProviderImpl(platformContext)
  Files.createDirectories(Path.of(fileDirectoryProvider.databasesDir()))
  Files.createDirectories(Path.of(fileDirectoryProvider.filesDir()))
  return platformContext
}

private fun createAppComponent(
  platformContext: PlatformContext,
  bdkBlockchainFactory: BdkBlockchainFactory,
): AppComponentImpl {
  return makeAppComponent(
    bdkAddressBuilder = BdkAddressBuilderImpl(),
    bdkBlockchainFactory = bdkBlockchainFactory,
    bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryImpl(),
    bdkPartiallySignedTransactionBuilder = BdkPartiallySignedTransactionBuilderImpl(),
    bdkTxBuilderFactory = BdkTxBuilderFactoryImpl(),
    bdkWalletFactory = BdkWalletFactoryImpl(),
    datadogRumMonitor = DatadogRumMonitorImpl(),
    delayer = ControlledDelayer(),
    deviceTokenConfigProvider =
      DeviceTokenConfigProviderImpl(
        DeviceTokenConfig("fake-device-token", FcmTeam)
      ),
    messageSigner = MessageSignerImpl(),
    signatureVerifier = SignatureVerifierImpl(),
    platformContext = platformContext,
    teltra = TeltraMock()
  )
}

private fun createActivityComponent(
  appComponent: AppComponentImpl,
  fakeNfcCommands: NfcCommands,
  sharingManager: SharingManager,
  cloudStoreAccRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
): ActivityComponentImpl {
  val cloudStoreAccountRepository = cloudStoreAccRepository
    ?: CloudStoreAccountRepositoryImpl(
      appComponent.keyValueStoreFactory
    )

  return ActivityComponentImpl(
    appComponent = appComponent,
    cloudKeyValueStore = cloudKeyValueStore
      ?: CloudKeyValueStoreImpl(appComponent.keyValueStoreFactory),
    cloudFileStore = CloudFileStoreFake(
      parentDir = appComponent.fileDirectoryProvider.filesDir(),
      fileManager = appComponent.fileManager
    ),
    cloudSignInUiStateMachine =
      CloudSignInUiStateMachineFake(
        cloudStoreAccountRepository as WritableCloudStoreAccountRepository,
        CloudStoreServiceProviderFake
      ),
    cloudDevOptionsStateMachine = cloudDevOptionsStateMachineNoop,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    datadogRumMonitor = DatadogRumMonitorImpl(),
    phoneNumberLibBindings = PhoneNumberLibBindingsImpl(),
    symmetricKeyEncryptor = SymmetricKeyEncryptorImpl(),
    symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
    lightningInvoiceParser = LightningInvoiceParserImpl(),
    nfcCommandsProvider = NfcCommandsProvider(fake = fakeNfcCommands, real = fakeNfcCommands),
    nfcSessionProvider = NfcSessionFake,
    sharingManager = sharingManager,
    systemSettingsLauncher = systemSettingsLauncher,
    inAppBrowserNavigator = inAppBrowserNavigator,
    xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
    xNonceGenerator = XNonceGeneratorImpl(),
    pdfAnnotatorFactory = PdfAnnotatorFactoryImpl(),
    spake2 = Spake2Impl(),
    cryptoBox = CryptoBoxImpl()
  )
}

private fun createBlockchainControl(networkType: BitcoinNetworkType): BlockchainControl =
  runBlocking {
    when (networkType) {
      REGTEST -> {
        val electrumUrl = System.getenv("ELECTRUM_HTTP_URL") ?: "http://localhost:8100"
        val bitcoindDomain = System.getenv("BITCOIND_DOMAIN") ?: "localhost:18443"
        val bitcoindUser = System.getenv("BITCOIND_USER") ?: "test"
        val bitcoindPassword = System.getenv("BITCOIND_PASSWORD") ?: "test"
        RegtestControl.create(
          bitcoindDomain = bitcoindDomain,
          bitcoindUser = bitcoindUser,
          bitcoindPassword = bitcoindPassword,
          electrumHttpApiUrl = electrumUrl
        )
      }

      else -> NoopBlockchainControl()
    }
  }

private fun createFakeHardwareKeyStore(
  secureStoreFactory: EncryptedKeyValueStoreFactoryImpl,
  hardwareSeed: FakeHardwareKeyStore.Seed?,
): FakeHardwareKeyStoreImpl {
  val bdkMnemonicGenerator = BdkMnemonicGeneratorImpl()
  val bdkDescriptorSecretKeyGenerator = BdkDescriptorSecretKeyGeneratorImpl()
  val publicKeyGenerator = Secp256k1KeyGeneratorImpl()
  val fakeHardwareKeyStore =
    FakeHardwareKeyStoreImpl(
      bdkMnemonicGenerator = bdkMnemonicGenerator,
      bdkDescriptorSecretKeyGenerator = bdkDescriptorSecretKeyGenerator,
      secp256k1KeyGenerator = publicKeyGenerator,
      encryptedKeyValueStoreFactory = secureStoreFactory
    )
  if (hardwareSeed != null) {
    runBlocking {
      fakeHardwareKeyStore.setSeed(hardwareSeed)
    }
  }
  return fakeHardwareKeyStore
}

private val inAppBrowserNavigator =
  object : InAppBrowserNavigator {
    override fun open(
      url: String,
      onClose: () -> Unit,
    ) {
      log { "Opened URL: $url " }
      onClose()
    }

    override fun onClose() = Unit
  }

private val systemSettingsLauncher =
  object : SystemSettingsLauncher {
    override fun launchSettings() {
      log { "Launch Settings" }
    }
  }

private val cloudDevOptionsStateMachineNoop = object : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    error("Not implemented")
  }
}
