package build.wallet.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import build.wallet.bdk.bindings.BdkBlockchainFactoryImpl
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.cloud.backup.CloudBackupStoreImpl
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudKeyValueStoreImpl
import build.wallet.cloud.store.CloudStoreAccountRepositoryImpl
import build.wallet.di.JvmAppComponentImpl
import build.wallet.di.create
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.feature.setFlagValue
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.data.FileManagerImpl
import build.wallet.platform.data.databasesDir
import build.wallet.platform.data.filesDir
import build.wallet.logging.logError
import build.wallet.logging.logInfo
import build.wallet.store.KeyValueStoreFactoryImpl
import build.wallet.ui.app.App
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.ZERO

/**
 * Entry point for the Bitkey Desktop host (W-17308).
 *
 * Boots the shared app runtime on the JVM in fake mode by reusing the same DI graph the JVM
 * integration tests use ([JvmAppComponentImpl] / [build.wallet.di.JvmActivityComponent]), then
 * renders the shared [App] composable inside a resizable Compose Desktop window. Hardware, NFC,
 * cloud, and social-recovery flows use fakes; F8e is pointed at the local Fromagerie environment.
 *
 * The bootstrap mirrors `AppTester` (shared/integration-testing). Unifying the two behind a shared
 * non-test helper is a tracked follow-up (it would require decoupling AppTester from Kotest).
 */
fun main() {
  val appScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineName("BitkeyDesktop")
  )

  val appDataDir = initAppDataDir()
  val fileDirectoryProvider = appDataDir.fileDirectoryProvider
  val appComponent = createAppComponent(appScope, fileDirectoryProvider)

  val activityComponent = runBlocking {
    appComponent.loggerInitializer.initialize()
    appComponent.bootstrapFakeMode()
    appComponent.activityComponent()
  }
  // App workers (incl. feature-flag init) are started by AppUiStateMachine on first composition,
  // mirroring Android's MainActivity; the host does not call executeAll() itself.

  val deviceInfo = appComponent.deviceInfoProvider.getDeviceInfo()

  application {
    // Phone-like default; the window stays freely resizable so layouts can be reviewed across
    // sizes. The dev control panel (W-17315) also offers size presets (phone/tablet/split).
    val windowState = rememberWindowState(size = DpSize(width = 420.dp, height = 880.dp))

    // Tracks which size preset the dev panel last applied, for menu selection state. The window
    // stays resizable, so once the user drags a corner this reverts to [WindowSizePreset.Free].
    var selectedSizePreset by remember { mutableStateOf(WindowSizePreset.Free) }
    // Set after reset-to-fresh completes or is refused, to show the appropriate result dialog.
    var resetDialogState by remember { mutableStateOf<ResetDialogState?>(null) }

    // Applying a preset drives the window size; any user drag is detected below and flips the
    // selection back to Free so the menu reflects reality.
    LaunchedEffect(selectedSizePreset) {
      selectedSizePreset.size?.let { windowState.size = it }
    }
    LaunchedEffect(windowState) {
      snapshotFlow { windowState.size }.collect { size ->
        if (selectedSizePreset.size != null && size != selectedSizePreset.size) {
          selectedSizePreset = WindowSizePreset.Free
        }
      }
    }

    Window(
      onCloseRequest = {
        runBlocking {
          appScope.coroutineContext[Job]?.cancelAndJoin()
        }
        exitApplication()
      },
      state = windowState,
      title = "Bitkey Desktop",
      icon = painterResource("desktop_icon.png")
    ) {
      val scope = rememberCoroutineScope()

      DevControlPanel(
        scope = scope,
        accountConfigService = appComponent.defaultAccountConfigService,
        chaincodeDelegationFeatureFlag = appComponent.chaincodeDelegationFeatureFlag,
        selectedSizePreset = selectedSizePreset,
        onSelectSizePreset = { selectedSizePreset = it },
        onResetToFresh = {
          // Wipe both the in-process app data (via AppDataDeleter, which also clears the live DI
          // graph's caches) and the verified on-disk data dir, then prompt for restart. We do
          // not relaunch the process in-place (a clean re-bootstrap of the DI graph from an
          // empty dir is brittle); a wipe + restart prompt mirrors `AppTester.launchNewApp()`
          // intent. W-17315.
          appScope.launch {
            if (!appDataDir.resetAllowed) {
              val message = appDataDir.resetRefusalMessage()
              logError { "Dev reset refused: $message" }
              resetDialogState = ResetDialogState.Failure(message)
              return@launch
            }

            appComponent.appDataDeleter.deleteAll().onFailure { error ->
              logError(throwable = error) {
                "Dev reset: in-process app data delete failed " +
                  "(continuing with on-disk wipe): $error"
              }
            }
            runCatching { wipeAppDataDir(appDataDir) }
              .onSuccess {
                logInfo { "Dev reset: app data wiped at ${appDataDir.root}; restart required." }
                resetDialogState = ResetDialogState.Success
              }
              .onFailure { error ->
                logError(throwable = error) { "Dev reset: failed to wipe app data dir" }
                resetDialogState = ResetDialogState.Failure(
                  "Reset to fresh failed while wiping app data. " +
                    "App data was not fully wiped: $error"
                )
              }
          }
        }
      )

      // Map window focus to app session lifecycle, mirroring Android's AppLifecycleObserver.
      val windowInfo = LocalWindowInfo.current
      LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
          if (focused) {
            appComponent.appSessionManager.appDidEnterForeground()
          } else {
            appComponent.appSessionManager.appDidEnterBackground()
          }
        }
      }

      App(
        model = activityComponent.appUiStateMachine.model(Unit),
        deviceInfo = deviceInfo,
        // Desktop exposes no accelerometer (no JVM binding); haptics resolves to a no-op JVM impl.
        accelerometer = null,
        themePreferenceService = activityComponent.themePreferenceService,
        haptics = appComponent.haptics
      )
    }

    // Modal prompt shown after reset-to-fresh completes or fails. After a successful wipe, quitting
    // is the only action: the in-memory app still references the now-deleted data.
    resetDialogState?.let { dialogState ->
      DialogWindow(
        onCloseRequest = {
          when (dialogState) {
            ResetDialogState.Success -> {
              appScope.cancel()
              exitApplication()
            }
            is ResetDialogState.Failure -> resetDialogState = null
          }
        },
        title = when (dialogState) {
          ResetDialogState.Success -> "Restart required"
          is ResetDialogState.Failure -> "Reset failed"
        }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
          Text(
            when (dialogState) {
              ResetDialogState.Success ->
                "App data was wiped. Quit and relaunch Bitkey Desktop to start fresh."
              is ResetDialogState.Failure -> dialogState.message
            }
          )
          Button(
            onClick = {
              when (dialogState) {
                ResetDialogState.Success -> {
                  appScope.cancel()
                  exitApplication()
                }
                is ResetDialogState.Failure -> resetDialogState = null
              }
            }
          ) {
            Text(
              when (dialogState) {
                ResetDialogState.Success -> "Quit now"
                is ResetDialogState.Failure -> "Dismiss"
              }
            )
          }
        }
      }
    }
  }
}

/**
 * Constructs the JVM app component with fake-backed hardware/NFC/cloud, mirroring
 * `AppTester.createAppComponent`.
 */
private fun createAppComponent(
  appScope: CoroutineScope,
  fileDirectoryProvider: FileDirectoryProvider,
): JvmAppComponentImpl {
  val fileManager = FileManagerImpl(fileDirectoryProvider)
  val keyValueStoreFactory = KeyValueStoreFactoryImpl(fileManager)
  return JvmAppComponentImpl::class.create(
    appCoroutineScope = appScope,
    appDir = fileDirectoryProvider.appDir(),
    bdkBlockchainFactory = BdkBlockchainFactoryImpl(),
    writableCloudStoreAccountRepository = CloudStoreAccountRepositoryImpl(keyValueStoreFactory),
    cloudBackupStore = CloudBackupStoreImpl(keyValueStoreFactory),
    cloudKeyValueStore = CloudKeyValueStoreImpl(keyValueStoreFactory),
    cloudFileStore = CloudFileStoreFake(
      parentDir = fileDirectoryProvider.filesDir(),
      fileManager = fileManager
    )
  )
}

/**
 * Sets the fake-mode bootstrap flags before the UI composes, so app workers observe the right
 * config. Mirrors `AppTester.launchApp`.
 */
private suspend fun JvmAppComponentImpl.bootstrapFakeMode() {
  defaultAccountConfigService.apply {
    setBitcoinNetworkType(REGTEST)
    setIsHardwareFake(true)
    setF8eEnvironment(Local)
    setIsTestAccount(true)
    setUsingSocRecFakes(true)
    setDelayNotifyDuration(ZERO)
  }
  chaincodeDelegationFeatureFlag.setFlagValue(true)
}

/**
 * The resolved on-disk app data directory. Reset-to-fresh only wipes [root] when it is the default
 * desktop app-data path, was created by this app run, or already carries [markerFile].
 */
private class AppDataDir(
  val root: Path,
  val markerFile: Path,
  val resetAllowed: Boolean,
  val fileDirectoryProvider: FileDirectoryProvider,
)

/**
 * Resolves the on-disk app data directory, ensures its databases/files subdirectories exist, and
 * returns an [AppDataDir] describing it.
 *
 * Location precedence (first match wins):
 *  1. `-Dbitkey.desktop.appDataDir=<path>` system property
 *  2. `BITKEY_DESKTOP_APP_DATA_DIR` environment variable
 *  3. default: `~/.bitkey-desktop/appdata`
 *
 * Overriding the location enables isolated instances and the reset-to-fresh dev action (W-17315).
 */
private fun initAppDataDir(): AppDataDir {
  val root = appDataRootFromConfig()
  val defaultRoot = defaultAppDataRoot()
  val existedAtStartup = root.exists()
  val markerFile = root.resolve(APP_DATA_DIR_MARKER_FILE)
  val hasMarker = markerFile.exists()
  val resetAllowed = root == defaultRoot || !existedAtStartup || hasMarker

  Files.createDirectories(root)
  if (resetAllowed && !hasMarker) {
    runCatching { Files.createFile(markerFile) }
      .onFailure { error ->
        logError(throwable = error) {
          "Failed to create Bitkey Desktop app-data marker at $markerFile"
        }
      }
  }

  if (!resetAllowed) {
    logInfo {
      "Desktop reset-to-fresh disabled for unmarked app data directory: $root"
    }
  }

  val provider = FileDirectoryProviderImpl(root.toString())
  Files.createDirectories(Path.of(provider.databasesDir()))
  Files.createDirectories(Path.of(provider.filesDir()))
  return AppDataDir(
    root = root,
    markerFile = markerFile,
    resetAllowed = resetAllowed,
    fileDirectoryProvider = provider
  )
}

/**
 * Recursively deletes everything under the verified app data [root][AppDataDir.root], while
 * preserving the root and marker file. Mirrors the intent of
 * `AppTester.launchNewApp()` (a from-scratch instance) for the desktop host: after this runs the
 * host must be restarted to re-bootstrap against the now-empty directory.
 *
 * We delete the on-disk directory directly (rather than going through `AppDataDeleter`) because the
 * host owns the verified directory and a child wipe of DBs, key-value stores, and cloud fakes is
 * the cleanest way to guarantee a truly fresh instance; the live DI graph is discarded on restart
 * anyway.
 */
private fun wipeAppDataDir(appDataDir: AppDataDir) {
  check(appDataDir.resetAllowed) {
    appDataDir.resetRefusalMessage()
  }

  val root = appDataDir.root
  if (!root.exists()) return
  Files.walk(root).use { paths ->
    // Delete children before parents (reverse-sorted depth) so directories are empty when removed,
    // but preserve the app-data root and marker so future resets remain safe.
    paths
      .sorted(Comparator.reverseOrder())
      .filter { path -> path != root && path != appDataDir.markerFile }
      .forEach(Files::delete)
  }
}

private fun appDataRootFromConfig(): Path {
  val configuredAppDir = System.getProperty(APP_DATA_DIR_PROPERTY)
    ?: System.getenv(APP_DATA_DIR_ENV_VAR)

  return if (configuredAppDir != null) {
    Path.of(configuredAppDir)
  } else {
    defaultAppDataRoot()
  }.toAbsolutePath().normalize()
}

private fun defaultAppDataRoot(): Path =
  Path.of(System.getProperty("user.home"), ".bitkey-desktop", "appdata")
    .toAbsolutePath()
    .normalize()

private fun AppDataDir.resetRefusalMessage(): String =
  "Reset to fresh was refused because $root is not a verified Bitkey Desktop app-data " +
    "directory. Use the default app-data path or a new/marked override directory."

private sealed interface ResetDialogState {
  data object Success : ResetDialogState

  data class Failure(val message: String) : ResetDialogState
}

private const val APP_DATA_DIR_PROPERTY = "bitkey.desktop.appDataDir"
private const val APP_DATA_DIR_ENV_VAR = "BITKEY_DESKTOP_APP_DATA_DIR"
private const val APP_DATA_DIR_MARKER_FILE = ".bitkey-desktop-appdata"
