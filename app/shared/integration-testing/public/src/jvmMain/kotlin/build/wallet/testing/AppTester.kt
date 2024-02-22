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
import build.wallet.bitcoin.treasury.TreasuryWallet
import build.wallet.bitcoin.treasury.TreasuryWalletFactory
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudKeyValueStoreImpl
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.CloudStoreAccountRepositoryImpl
import build.wallet.cloud.store.CloudStoreServiceProviderFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.datadog.DatadogRumMonitorImpl
import build.wallet.di.ActivityComponentImpl
import build.wallet.di.AppComponentImpl
import build.wallet.di.makeAppComponent
import build.wallet.email.Email
import build.wallet.encrypt.HkdfImpl
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.Secp256k1SharedSecretImpl
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
import build.wallet.money.FiatMoney
import build.wallet.nfc.FakeHardwareKeyStoreImpl
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
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
import build.wallet.statemachine.cloud.CloudSignInUiStateMachineFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.store.EncryptedKeyValueStoreFactoryImpl
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val BITCOIN_NETWORK_ENV_VAR_NAME = "BITCOIN_NETWORK"
const val F8E_ENV_ENV_VAR_NAME = "F8E_ENVIRONMENT"

@Suppress("TooManyFunctions")
class AppTester(
  val app: ActivityComponentImpl,
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
      val keyboxConfig =
        KeyboxConfig(
          networkType = initialBitcoinNetworkType,
          isHardwareFake = true,
          f8eEnvironment = initialF8eEnvironment,
          isTestAccount = true,
          isUsingSocRecFakes = isUsingSocRecFakes,
          delayNotifyDuration = delayNotifyDuration
        )
      appComponent.templateKeyboxConfigDao.set(keyboxConfig)
      val keyCrossAppDraft = keyCrossBuilder.createNewKeyCross(keyboxConfig)

      val hwActivation =
        pairingTransactionProvider(
          networkType = keyboxConfig.networkType,
          onSuccess = {},
          onCancel = {},
          isHardwareFake = keyboxConfig.isHardwareFake
        ).let { transaction ->
          nfcTransactor.fakeTransact(
            transaction = transaction::session
          ).getOrThrow().also { transaction.onSuccess(it) }
        }
      require(hwActivation is FingerprintEnrolled)
      val hwKeyBundle = hwActivation.keyBundle
      val keyCrossAppHwDraft =
        keyCrossBuilder.addHardwareKeyBundle(
          keyCrossAppDraft,
          hwKeyBundle
        )
      val account = fullAccountCreator.createAccount(keyCrossAppHwDraft).getOrThrow()
      println("Created account ${account.accountId}")

      if (shouldSetUpNotifications) {
        val addedTouchpoint =
          notificationTouchpointService.addTouchpoint(
            f8eEnvironment = keyboxConfig.f8eEnvironment,
            fullAccountId = account.accountId,
            touchpoint =
              NotificationTouchpoint.EmailTouchpoint(
                touchpointId = "",
                value = Email("integration-test@wallet.build") // This is a fake email
              )
          ).mapError { it.error }.getOrThrow()
        notificationTouchpointService.verifyTouchpoint(
          f8eEnvironment = keyboxConfig.f8eEnvironment,
          fullAccountId = account.accountId,
          touchpointId = addedTouchpoint.touchpointId,
          verificationCode = "123456" // This code always works for Test Accounts
        ).mapError { it.error }.getOrThrow()
        notificationTouchpointService.activateTouchpoint(
          f8eEnvironment = keyboxConfig.f8eEnvironment,
          fullAccountId = account.accountId,
          touchpointId = addedTouchpoint.touchpointId,
          hwFactorProofOfPossession = null
        ).getOrThrow()
      }

      if (cloudStoreAccountForBackup != null) {
        val backup =
          app.fullAccountCloudBackupCreator.create(account.keybox, hwActivation.sealedCsek, emptyList())
            .getOrThrow()
        app.cloudBackupRepository.writeBackup(account.accountId, cloudStoreAccountForBackup, backup)
          .getOrThrow()
        (app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
          .set(cloudStoreAccountForBackup)
          .getOrThrow()
      }

      onboardingService.completeOnboarding(
        f8eEnvironment = keyboxConfig.f8eEnvironment,
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
  suspend fun createTcInvite(
    account: FullAccount,
    tcName: String,
  ): Invitation {
    val hwPop = getHardwareFactorProofOfPossession(account.keybox)
    return app.socRecRelationshipsRepository
      .createInvitation(
        account = account,
        trustedContactAlias = TrustedContactAlias(tcName),
        hardwareProofOfPossession = hwPop
      )
      .getOrThrow()
  }

  /**
   * Onboard Lite Account by accepting a Trusted Contact invitation.
   */
  suspend fun onboardLiteAccountFromInvitation(
    invitation: Invitation,
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
      // TODO(BKR-529) Generate a real public key
      val protectedCustomer =
        socRecRelationshipsRepository
          .acceptInvitation(
            account,
            invitation,
            protectedCustomerAlias,
            TrustedContactIdentityKey(AppKey.fromPublicKey("TODO"))
          )
          .getOrThrow { it.error }
      protectedCustomer.alias.shouldBe(protectedCustomerAlias)

      if (cloudStoreAccountForBackup != null) {
        val backup =
          app.liteAccountCloudBackupCreator.create(account).getOrThrow()
        app.cloudBackupRepository.writeBackup(account.accountId, cloudStoreAccountForBackup, backup)
          .getOrThrow()
      }

      return account
    }
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

  /**
   * Returns and asserts the active keybox
   */
  suspend fun getActiveFullAccount(): FullAccount {
    val accountStatus = app.appComponent.accountRepository.accountStatus().first().getOrThrow()
    return (accountStatus as? ActiveAccount)?.account as? FullAccount
      ?: error("active Full Account not found")
  }

  /**
   * Returns and asserts the active lite account
   */
  suspend fun getActiveLiteAccount(): LiteAccount {
    val accountStatus = app.appComponent.accountRepository.accountStatus().first().getOrThrow()
    return (accountStatus as? ActiveAccount)?.account as? LiteAccount
      ?: error("active Lite Account not found")
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
   * Delete real cloud backups from fake, local cloud accounts.
   */
  suspend fun deleteBackupsFromFakeCloud() {
    val fakeCloudAccounts = listOf(CloudStoreAccount1Fake)
    fakeCloudAccounts.forEach { fakeCloudAccount ->
      app.cloudBackupRepository.clear(fakeCloudAccount)
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
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
): AppTester =
  launchApp(
    bdkBlockchainFactory = bdkBlockchainFactory,
    f8eEnvironment = f8eEnvironment,
    existingAppDir = app.appComponent.fileDirectoryProvider.appDir(),
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudKeyValueStore = cloudKeyValueStore,
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
  isUsingSocRecFakes: Boolean = false,
): AppTester =
  launchApp(
    existingAppDir = null,
    bdkBlockchainFactory,
    f8eEnvironment,
    bitcoinNetworkType,
    cloudStoreAccountRepository,
    cloudKeyValueStore,
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
  val fakeHardwareKeyStore = createFakeHardwareKeyStore(appComponent.secureStoreFactory)
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

  val keyboxConfig =
    KeyboxConfig(
      networkType = bitcoinNetworkType,
      isHardwareFake = true,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = true,
      isUsingSocRecFakes = isUsingSocRecFakes,
      delayNotifyDuration = 0.seconds
    )
  runBlocking {
    appComponent.templateKeyboxConfigDao.set(keyboxConfig)
  }

  return AppTester(
    activityComponent,
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
    deviceTokenConfigProvider =
      DeviceTokenConfigProviderImpl(
        DeviceTokenConfig("fake-device-token", FcmTeam)
      ),
    messageSigner = MessageSignerImpl(),
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
    cloudKeyValueStore = cloudKeyValueStore ?: CloudKeyValueStoreImpl(appComponent.keyValueStoreFactory),
    cloudFileStore = CloudFileStoreFake(fileManager = appComponent.fileManager),
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
    secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl(),
    xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
    secp256k1SharedSecret = Secp256k1SharedSecretImpl(),
    hkdf = HkdfImpl(),
    xNonceGenerator = XNonceGeneratorImpl(),
    pdfAnnotatorFactory = PdfAnnotatorFactoryImpl()
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
