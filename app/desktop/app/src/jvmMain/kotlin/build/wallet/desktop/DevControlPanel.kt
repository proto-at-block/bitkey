package build.wallet.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import bitkey.account.AccountConfigService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.feature.flags.ChaincodeDelegationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.feature.setFlagValue
import build.wallet.logging.logError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days

/**
 * Window-size presets surfaced in the dev control panel. These are pure host-side conveniences for
 * reviewing the shared layout across common device form factors; the window itself stays freely
 * resizable so any custom size is still reachable ([Free] is a marker meaning "leave size as-is").
 *
 * Sizes are approximate logical (dp) dimensions for representative devices.
 */
enum class WindowSizePreset(
  val label: String,
  val size: DpSize?,
) {
  SmallPhone("Small phone (360 x 780)", DpSize(360.dp, 780.dp)),
  LargePhone("Large phone (430 x 930)", DpSize(430.dp, 930.dp)),
  Tablet("Tablet (834 x 1112)", DpSize(834.dp, 1112.dp)),
  SplitScreen("Split screen (215 x 930)", DpSize(215.dp, 930.dp)),
  Free("Free resize", null),
}

/**
 * In-window developer control panel for the Bitkey Desktop host (W-17315).
 *
 * Rendered as a Compose Desktop top [MenuBar] (least intrusive to the shared
 * [build.wallet.ui.app.App] content). It is strictly a desktop-host concern and does not touch any
 * shared module:
 *
 *  - **Window** menu: drives [onSelectSizePreset] to resize the host window to common form factors.
 *  - **Fake Mode** menu: toggles the fake-mode flags on [accountConfigService] and the
 *    [chaincodeDelegationFeatureFlag]. These setters are suspend, so they run on [scope].
 *  - **Dev** menu: triggers [onResetToFresh], which wipes the on-disk app data dir and prompts a
 *    restart (mirrors `AppTester.launchNewApp()` intent).
 *
 * Current flag values are read from the config/flag state flows and reflected in the menu items.
 *
 * @param scope a long-lived coroutine scope used to invoke the suspend config/flag setters. Callers
 *   pass the host's app scope; this composable does not own or cancel it.
 * @param selectedSizePreset the preset currently reflected by the window size, for menu selection
 *   state. The window stays resizable, so this is best-effort.
 */
@Composable
fun FrameWindowScope.DevControlPanel(
  scope: CoroutineScope,
  accountConfigService: AccountConfigService,
  chaincodeDelegationFeatureFlag: ChaincodeDelegationFeatureFlag,
  selectedSizePreset: WindowSizePreset,
  onSelectSizePreset: (WindowSizePreset) -> Unit,
  onResetToFresh: () -> Unit,
) {
  val config by accountConfigService.defaultConfig().collectAsState()
  val chaincodeEnabled by chaincodeDelegationFeatureFlag.flagValue().collectAsState()

  // Runs a suspend AccountConfigService setter and logs (rather than surfaces) any failure: these
  // are dev-only mutations and a failure should not crash the host.
  fun updateConfig(block: suspend AccountConfigService.() -> Result<Unit, Error>) {
    scope.launch {
      accountConfigService.block().onFailure { error ->
        logError(throwable = error) {
          "Dev control panel failed to update account config: $error"
        }
      }
    }
  }

  MenuBar {
    Menu(text = "Window", mnemonic = 'W') {
      WindowSizePreset.entries.forEach { preset ->
        RadioButtonItem(
          text = preset.label,
          selected = preset == selectedSizePreset,
          onClick = { onSelectSizePreset(preset) }
        )
      }
    }

    Menu(text = "Fake Mode", mnemonic = 'F') {
      CheckboxItem(
        text = "Fake hardware",
        checked = config.isHardwareFake,
        onCheckedChange = { checked -> updateConfig { setIsHardwareFake(checked) } }
      )
      CheckboxItem(
        text = "Test account",
        checked = config.isTestAccount,
        onCheckedChange = { checked -> updateConfig { setIsTestAccount(checked) } }
      )
      CheckboxItem(
        text = "Use SocRec fakes",
        checked = config.isUsingSocRecFakes,
        onCheckedChange = { checked -> updateConfig { setUsingSocRecFakes(checked) } }
      )

      Separator()

      Menu(text = "Bitcoin network") {
        BitcoinNetworkType.entries.forEach { network ->
          RadioButtonItem(
            text = network.name,
            selected = config.bitcoinNetworkType == network,
            onClick = { updateConfig { setBitcoinNetworkType(network) } }
          )
        }
      }

      Menu(text = "Delay & Notify duration") {
        DelayNotifyPreset.entries.forEach { preset ->
          RadioButtonItem(
            text = preset.label,
            selected = config.delayNotifyDuration == preset.duration,
            onClick = { updateConfig { setDelayNotifyDuration(preset.duration) } }
          )
        }
      }

      Separator()

      CheckboxItem(
        text = "Chaincode delegation",
        checked = chaincodeEnabled.isEnabled(),
        onCheckedChange = { checked ->
          scope.launch { chaincodeDelegationFeatureFlag.setFlagValue(checked) }
        }
      )
    }

    Menu(text = "Dev", mnemonic = 'D') {
      Item(
        text = "Reset to fresh (wipe data)…",
        onClick = onResetToFresh
      )
    }
  }
}

/**
 * Delay & Notify duration presets exposed in the dev panel. `null` means "unset" (no override);
 * [ZERO] is the fake-mode default used by the host bootstrap so recovery flows don't actually wait.
 */
private enum class DelayNotifyPreset(
  val label: String,
  val duration: Duration?,
) {
  Instant("Instant (0s)", ZERO),
  SevenDays("7 days (real)", 7.days),
  Unset("Unset", null),
}
