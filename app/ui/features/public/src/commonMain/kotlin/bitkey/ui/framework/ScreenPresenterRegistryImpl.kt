package bitkey.ui.framework

import bitkey.ui.screens.demo.*
import bitkey.ui.screens.securityhub.SecurityHubPresenter
import bitkey.ui.screens.securityhub.SecurityHubScreen
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.statemachine.dev.DebugMenuScreenPresenter
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugScreen
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugScreenPresenter

/**
 * Registry of [ScreenPresenter]'s for the app.
 *
 * TODO: use DI generation
 */
@BitkeyInject(ActivityScope::class)
class ScreenPresenterRegistryImpl(
  private val demoModeEnabledScreenPresenter: DemoModeEnabledScreenPresenter,
  private val demoModeCodeEntryScreenPresenter: DemoModeCodeEntryScreenPresenter,
  private val demoModeDisabledScreenPresenter: DemoModeDisabledScreenPresenter,
  private val demoCodeEntrySubmissionScreenPresenter: DemoCodeEntrySubmissionScreenPresenter,
  private val bitcoinWalletDebugScreenPresenter: BitcoinWalletDebugScreenPresenter,
  private val debugMenuScreenPresenter: DebugMenuScreenPresenter,
  private val securityHubPresenter: SecurityHubPresenter,
) : ScreenPresenterRegistry {
  override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
    @Suppress("UNCHECKED_CAST")
    return when (screen) {
      is DemoModeEnabledScreen -> demoModeEnabledScreenPresenter
      is DemoModeCodeEntryScreen -> demoModeCodeEntryScreenPresenter
      is DemoModeDisabledScreen -> demoModeDisabledScreenPresenter
      is DemoCodeEntrySubmissionScreen -> demoCodeEntrySubmissionScreenPresenter
      is BitcoinWalletDebugScreen -> bitcoinWalletDebugScreenPresenter
      is DebugMenuScreen -> debugMenuScreenPresenter
      is SecurityHubScreen -> securityHubPresenter
      else -> error("Did not find presenter for $screen")
    } as ScreenPresenter<ScreenT>
  }
}
