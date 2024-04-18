package build.wallet

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATION_OPEN
import build.wallet.bitcoin.lightning.LightningInvoiceParserImpl
import build.wallet.cloud.store.CloudFileStoreImpl
import build.wallet.cloud.store.CloudKeyValueStoreImpl
import build.wallet.cloud.store.CloudStoreAccountRepositoryImpl
import build.wallet.cloud.store.GoogleAccountRepositoryImpl
import build.wallet.cloud.store.GoogleDriveFileStoreImpl
import build.wallet.cloud.store.GoogleDriveKeyValueStoreImpl
import build.wallet.cloud.store.GoogleDriveServiceImpl
import build.wallet.crypto.Spake2Impl
import build.wallet.datadog.DatadogRumMonitorImpl
import build.wallet.di.ActivityComponent
import build.wallet.di.ActivityComponentImpl
import build.wallet.di.AppComponent
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.google.signin.GoogleSignInClientProviderImpl
import build.wallet.google.signin.GoogleSignInLauncherImpl
import build.wallet.google.signin.GoogleSignOutActionImpl
import build.wallet.logging.log
import build.wallet.nfc.AndroidNfcAdapterProvider
import build.wallet.nfc.AndroidNfcTagScanner
import build.wallet.nfc.FakeHardwareKeyStoreImpl
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.NfcCommandsFake
import build.wallet.nfc.NfcCommandsImpl
import build.wallet.nfc.NfcSessionProviderImpl
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.phonenumber.PhoneNumberLibBindingsImpl
import build.wallet.platform.notifications.NotificationChannelRepository
import build.wallet.platform.pdf.PdfAnnotatorFactoryImpl
import build.wallet.platform.settings.SystemSettingsLauncherImpl
import build.wallet.platform.sharing.SharingManagerImpl
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.platform.web.InAppBrowserNavigatorImpl
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.recovery.cloud.CloudSignInUiStateMachineImpl
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachineImpl
import build.wallet.ui.app.AnimatedApp
import build.wallet.ui.app.AppUiModelMap

class MainActivity : ComponentActivity() {
  private lateinit var appComponent: AppComponent
  private lateinit var inAppBrowserNavigator: InAppBrowserNavigator
  private lateinit var activityComponent: ActivityComponent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Enable support for Splash Screen API for
    // proper Android 12+ support
    installSplashScreen()
    appComponent = (application as BitkeyApplication).appComponent
    logAppLaunchState(savedInstanceState, application as BitkeyApplication)

    lockOrientationToPortrait()
    drawContentBehindSystemBars()

    activityComponent = initializeActivityComponent()
    Router.route = Route.fromUrl(intent.dataString)

    setContent {
      AnimatedApp(
        model = activityComponent.appUiStateMachine.model(Unit),
        appVariant = appComponent.appVariant,
        uiModelMap = AppUiModelMap
      )
    }
    createNotificationChannel()
    logEventIfFromNotification()
  }

  override fun onResume() {
    super.onResume()
    inAppBrowserNavigator.onClose()
  }

  // Handle deep links when the app is already open
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.let {
      Router.route = Route.fromUrl(intent.dataString)
    }
  }

  private fun logAppLaunchState(
    savedInstanceState: Bundle?,
    application: BitkeyApplication,
  ) {
    if (application.isFreshLaunch) {
      application.isFreshLaunch = false
      when (savedInstanceState) {
        // When our "isFreshInstance" variable is true, and savedInstance bundle is null, we know it
        // is a completely fresh launch of the app.
        null -> log(tag = "app_lifecycle") { "Fresh Launch" }
        // When our "isFreshInstance" variable is true, and savedInstance bundle is not null, we
        // assume the app is launching from after a process death.
        else -> log(tag = "app_lifecycle") { "Recovering from process death" }
      }
    } else {
      when (savedInstanceState) {
        // When our "isFreshInstance" variable is false, and savedInstance bundle is null, the app is
        // creating a new instance of the activity
        null -> log(tag = "app_lifecycle") { "Creating activity instance" }
        // When our "isFreshInstance" variable is false, and savedInstance bundle is not null, then
        // we know the app is recovering from a configuration change.
        else -> log(tag = "app_lifecycle") { "Recovering from configuration change" }
      }
    }
  }

  private fun createNotificationChannel() {
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    val notificationChannelRepository =
      NotificationChannelRepository(
        context = this,
        notificationManager = notificationManager
      )
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      notificationChannelRepository.setupChannels()
    }
  }

  private fun logEventIfFromNotification() {
    if (intent.extras?.getBoolean("notification") == true) {
      appComponent.eventTracker.track(ACTION_APP_PUSH_NOTIFICATION_OPEN)
    }
  }

  private fun drawContentBehindSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
  }

  @SuppressLint("SourceLockedOrientationActivity")
  private fun lockOrientationToPortrait() {
    requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
  }

  private fun registerLifecycleObservers() {
    val appLifecycleObserver =
      AppLifecycleObserver(
        appSessionManager = appComponent.appSessionManager
      )
    lifecycle.apply {
      addObserver(appLifecycleObserver)
    }
  }

  private fun initializeActivityComponent(): ActivityComponentImpl {
    val nfcTagScanner =
      AndroidNfcTagScanner(
        nfcAdapterProvider = AndroidNfcAdapterProvider(context = this),
        activity = this,
        lifecycle = lifecycle
      )
    registerLifecycleObservers()

    val googleAccountRepository = GoogleAccountRepositoryImpl(appComponent.platformContext)
    val cloudStoreAccountRepository =
      CloudStoreAccountRepositoryImpl(googleAccountRepository)
    val googleDriveService =
      GoogleDriveServiceImpl(appComponent.appId, appComponent.platformContext)
    val googleDriveFileStore = GoogleDriveFileStoreImpl(googleDriveService)
    val googleDriveKeyValueStore = GoogleDriveKeyValueStoreImpl(googleDriveService)
    val cloudFileStore = CloudFileStoreImpl(googleDriveFileStore)
    val cloudKeyValueStore = CloudKeyValueStoreImpl(googleDriveKeyValueStore)
    val googleSignInLauncher =
      GoogleSignInLauncherImpl(GoogleSignInClientProviderImpl(appComponent.platformContext))
    val googleSignOutAction =
      GoogleSignOutActionImpl(GoogleSignInClientProviderImpl(appComponent.platformContext))
    val cloudSignInUiStateMachine =
      CloudSignInUiStateMachineImpl(
        GoogleSignInStateMachineImpl(
          googleSignInLauncher,
          googleSignOutAction,
          cloudStoreAccountRepository
        )
      )
    val cloudDevOptionsStateMachine =
      CloudDevOptionsStateMachineImpl(
        googleAccountRepository,
        googleSignInLauncher,
        googleSignOutAction
      )
    val publicKeyGenerator = Secp256k1KeyGeneratorImpl()
    val fakeHardwareKeyStore =
      FakeHardwareKeyStoreImpl(
        bdkMnemonicGenerator = appComponent.bdkMnemonicGenerator,
        bdkDescriptorSecretKeyGenerator = appComponent.bdkDescriptorSecretKeyGenerator,
        secp256k1KeyGenerator = publicKeyGenerator,
        encryptedKeyValueStoreFactory = appComponent.secureStoreFactory
      )
    val fakeHardwareSpendingWalletProvider =
      FakeHardwareSpendingWalletProvider(
        spendingWalletProvider = appComponent.spendingWalletProvider,
        descriptorBuilder = appComponent.bitcoinMultiSigDescriptorBuilder,
        fakeHardwareKeyStore = fakeHardwareKeyStore
      )
    inAppBrowserNavigator =
      InAppBrowserNavigatorImpl(
        activity = this,
        platformContext = appComponent.platformContext
      )
    val nfcCommandsProvider =
      NfcCommandsProvider(
        fake =
          NfcCommandsFake(
            messageSigner = appComponent.messageSigner,
            fakeHardwareKeyStore = fakeHardwareKeyStore,
            fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider
          ),
        real = NfcCommandsImpl()
      )
    val nfcSessionProvider = NfcSessionProviderImpl(nfcTagScanner, appComponent.appCoroutineScope)

    return ActivityComponentImpl(
      appComponent = appComponent,
      cloudKeyValueStore = cloudKeyValueStore,
      cloudFileStore = cloudFileStore,
      cloudSignInUiStateMachine = cloudSignInUiStateMachine,
      cloudDevOptionsStateMachine = cloudDevOptionsStateMachine,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      datadogRumMonitor = DatadogRumMonitorImpl(),
      phoneNumberLibBindings = PhoneNumberLibBindingsImpl(),
      symmetricKeyEncryptor = SymmetricKeyEncryptorImpl(),
      symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
      lightningInvoiceParser = LightningInvoiceParserImpl(),
      sharingManager = SharingManagerImpl(activity = this),
      systemSettingsLauncher = SystemSettingsLauncherImpl(activity = this),
      inAppBrowserNavigator = inAppBrowserNavigator,
      nfcCommandsProvider = nfcCommandsProvider,
      nfcSessionProvider = nfcSessionProvider,
      xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
      xNonceGenerator = XNonceGeneratorImpl(),
      pdfAnnotatorFactory = PdfAnnotatorFactoryImpl(applicationContext = this),
      spake2 = Spake2Impl(),
      cryptoBox = CryptoBoxImpl()
    )
  }
}
