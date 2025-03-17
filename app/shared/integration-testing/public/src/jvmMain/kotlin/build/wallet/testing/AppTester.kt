@file:OptIn(KotestInternal::class)

package build.wallet.testing

import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.BdkDescriptorFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyFactoryImpl
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.blockchain.BlockchainControl
import build.wallet.bitcoin.blockchain.NoopBlockchainControl
import build.wallet.bitcoin.blockchain.RegtestControl
import build.wallet.bitcoin.treasury.TreasuryWallet
import build.wallet.bitcoin.treasury.TreasuryWalletFactory
import build.wallet.cloud.store.*
import build.wallet.coroutines.createBackgroundScope
import build.wallet.di.JvmActivityComponent
import build.wallet.di.JvmAppComponent
import build.wallet.di.JvmAppComponentImpl
import build.wallet.di.create
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.logging.LogLevel
import build.wallet.logging.Logger
import build.wallet.logging.logTesting
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import build.wallet.platform.data.databasesDir
import build.wallet.platform.data.filesDir
import build.wallet.platform.random.uuid
import build.wallet.store.KeyValueStoreFactoryImpl
import io.kotest.common.KotestInternal
import io.kotest.core.test.TestScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.ZERO

const val BITCOIN_NETWORK_ENV_VAR_NAME = "BITCOIN_NETWORK"
const val F8E_ENV_ENV_VAR_NAME = "F8E_ENVIRONMENT"

/**
 * @param testScope parent coroutine scope of this [build.wallet.testing.AppTester] instance, in this
 * case it's the coroutine scope tied to the test itself.
 */
@Suppress("TooManyFunctions")
class AppTester(
  private val testScope: TestScope,
  appComponent: JvmAppComponent,
  activityComponent: JvmActivityComponent,
  internal val blockchainControl: BlockchainControl,
  internal val initialF8eEnvironment: F8eEnvironment,
  val initialBitcoinNetworkType: BitcoinNetworkType,
  val isUsingSocRecFakes: Boolean,
) : JvmAppComponent by appComponent, JvmActivityComponent by activityComponent {
  val treasuryWallet: TreasuryWallet by lazy {
    runBlocking {
      TreasuryWalletFactory(
        bitcoinBlockchain = appComponent.bitcoinBlockchain,
        blockchainControl = blockchainControl,
        spendingWalletProvider = appComponent.spendingWalletProvider,
        bdkDescriptorSecretKeyFactory = BdkDescriptorSecretKeyFactoryImpl(),
        bdkDescriptorFactory = BdkDescriptorFactoryImpl(),
        feeEstimator = appComponent.bitcoinFeeRateEstimator
      ).create(initialBitcoinNetworkType)
    }
  }

  /**
   * Creates a new [AppTester] that share data with an existing app instance.
   * It is not safe to continue using the previous [AppTester] instance after calling this method.
   */
  suspend fun relaunchApp(
    bdkBlockchainFactory: BdkBlockchainFactory? = null,
    f8eEnvironment: F8eEnvironment? = null,
    executeWorkers: Boolean = true,
  ): AppTester {
    // Cancel
    appCoroutineScope.run {
      cancel()
      coroutineContext.job.cancelAndJoin()
    }
    return testScope.launchApp(
      existingAppDir = fileDirectoryProvider.appDir(),
      bdkBlockchainFactory = bdkBlockchainFactory,
      f8eEnvironment = f8eEnvironment,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudKeyValueStore = cloudKeyValueStore,
      isUsingSocRecFakes = isUsingSocRecFakes,
      hardwareSeed = fakeHardwareKeyStore.getSeed(),
      executeWorkers = executeWorkers
    )
  }

  companion object {
    /**
     * Creates a brand new [AppTester].
     */
    suspend fun TestScope.launchNewApp(
      bdkBlockchainFactory: BdkBlockchainFactory? = null,
      f8eEnvironment: F8eEnvironment? = null,
      bitcoinNetworkType: BitcoinNetworkType? = null,
      cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
      cloudKeyValueStore: CloudKeyValueStore? = null,
      hardwareSeed: FakeHardwareKeyStore.Seed? = null,
      isUsingSocRecFakes: Boolean = false,
      executeWorkers: Boolean = true,
    ): AppTester {
      return launchApp(
        existingAppDir = null,
        bdkBlockchainFactory,
        f8eEnvironment,
        bitcoinNetworkType,
        cloudStoreAccountRepository,
        cloudKeyValueStore,
        hardwareSeed,
        isUsingSocRecFakes,
        executeWorkers
      )
    }

    /**
     * Creates an [AppTester] instance. Note that each [AppTester] instance is tied to a coroutine
     * scope of the test, see [AppTester.testScope].
     *
     * Usage:
     * ```kotlin
     * test("my test") {
     *   val app = launchApp()
     *   // use AppTester
     *
     *   // AppTester's coroutine scope and its jobs will be auto-cancelled
     *   // at the end of a test.
     * }
     * ```
     *
     * @param existingAppDir Specify where application data (databases) should be saved.
     * If there is existing data in the directory, it will be used by the new app.
     * @param executeWorkers if true, all [AppWorker]s will be executed.
     */
    @Suppress("NAME_SHADOWING")
    private suspend fun TestScope.launchApp(
      existingAppDir: String? = null,
      bdkBlockchainFactory: BdkBlockchainFactory? = null,
      f8eEnvironment: F8eEnvironment? = null,
      bitcoinNetworkType: BitcoinNetworkType? = null,
      cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
      cloudKeyValueStore: CloudKeyValueStore? = null,
      hardwareSeed: FakeHardwareKeyStore.Seed? = null,
      isUsingSocRecFakes: Boolean,
      executeWorkers: Boolean = true,
    ): AppTester {
      // "Disable" default kermit logger until we have our own custom logger setup.
      // Use Error as minimum log level and use no loger writers to "suppress" logs in meantime.
      Logger.configure(tag = "", minimumLogLevel = LogLevel.Error, logWriters = emptyList())
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

      val appTesterId = uuid()
      val appDir = initAppDir(appTesterId = appTesterId, existingAppDir = existingAppDir)

      val appScope = createBackgroundScope(CoroutineName("AppTester-$appTesterId"))
      val appComponent = createAppComponent(
        appScope = appScope,
        appDir = appDir,
        bdkBlockchainFactory = bdkBlockchainFactory,
        cloudStoreAccountRepositoryOverride = cloudStoreAccountRepository,
        cloudKeyValueStoreOverride = cloudKeyValueStore
      )
      appComponent.loggerInitializer.initialize()
      if (hardwareSeed != null) {
        appComponent.fakeHardwareKeyStore.setSeed(hardwareSeed)
      }
      val blockchainControl = createBlockchainControl(bitcoinNetworkType)
      val activityComponent = appComponent.activityComponent()

      appComponent.defaultAccountConfigService.apply {
        setBitcoinNetworkType(bitcoinNetworkType)
        setIsHardwareFake(true)
        setF8eEnvironment(f8eEnvironment)
        setIsTestAccount(true)
        setUsingSocRecFakes(isUsingSocRecFakes)
        setDelayNotifyDuration(ZERO)
      }

      // TODO(W-9704): execute workers by default
      if (executeWorkers) {
        appComponent.appCoroutineScope.launch {
          appComponent.appWorkerExecutor.executeAll()
        }

        // Wait for feature flags to be initialized
        appComponent.featureFlagService.flagsInitialized
          .filter { initialized -> initialized }
          .first() // Suspend until first `true` value
      }

      return AppTester(
        testScope = this,
        appComponent = appComponent,
        activityComponent = activityComponent,
        blockchainControl = blockchainControl,
        initialF8eEnvironment = f8eEnvironment,
        initialBitcoinNetworkType = bitcoinNetworkType,
        isUsingSocRecFakes = isUsingSocRecFakes
      )
    }
  }
}

private fun initAppDir(
  appTesterId: String,
  existingAppDir: String?,
): String {
  val appDir =
    if (existingAppDir != null) {
      existingAppDir
    } else {
      val rootDir = (System.getProperty("user.dir") + "/_build/bitkey/appdata")
      rootDir.join(appTesterId)
    }
  logTesting { "App data directory is $appDir" }
  val fileDirectoryProvider = FileDirectoryProviderImpl(appDir)
  Files.createDirectories(Path.of(fileDirectoryProvider.databasesDir()))
  Files.createDirectories(Path.of(fileDirectoryProvider.filesDir()))
  return appDir
}

private fun createAppComponent(
  appScope: CoroutineScope,
  appDir: String,
  bdkBlockchainFactory: BdkBlockchainFactory,
  cloudStoreAccountRepositoryOverride: CloudStoreAccountRepository? = null,
  cloudKeyValueStoreOverride: CloudKeyValueStore? = null,
): JvmAppComponentImpl {
  val fileDirectoryProvider = FileDirectoryProviderImpl(appDir)
  val fileManager = FileManagerImpl(fileDirectoryProvider)
  val keyValueStoreFactory = KeyValueStoreFactoryImpl(fileManager)
  val writableCloudStoreAccountRepository =
    (cloudStoreAccountRepositoryOverride as? WritableCloudStoreAccountRepository)
      ?: CloudStoreAccountRepositoryImpl(keyValueStoreFactory)
  val cloudKeyValueStore =
    cloudKeyValueStoreOverride ?: CloudKeyValueStoreImpl(keyValueStoreFactory)
  val cloudFileStore = CloudFileStoreFake(
    parentDir = fileDirectoryProvider.filesDir(),
    fileManager = fileManager
  )
  return JvmAppComponentImpl::class.create(
    appCoroutineScope = appScope,
    appDir = appDir,
    bdkBlockchainFactory = bdkBlockchainFactory,
    writableCloudStoreAccountRepository = writableCloudStoreAccountRepository,
    cloudKeyValueStore = cloudKeyValueStore,
    cloudFileStore = cloudFileStore
  )
}

private fun createBlockchainControl(networkType: BitcoinNetworkType): BlockchainControl =
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
