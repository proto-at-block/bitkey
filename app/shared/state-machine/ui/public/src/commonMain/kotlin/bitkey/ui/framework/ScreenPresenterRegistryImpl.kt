package bitkey.ui.framework

import bitkey.ui.screens.demo.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject

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
) : ScreenPresenterRegistry {
  override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
    @Suppress("UNCHECKED_CAST")
    return when (screen) {
      is DemoModeEnabledScreen -> demoModeEnabledScreenPresenter
      is DemoModeCodeEntryScreen -> demoModeCodeEntryScreenPresenter
      is DemoModeDisabledScreen -> demoModeDisabledScreenPresenter
      is DemoCodeEntrySubmissionScreen -> demoCodeEntrySubmissionScreenPresenter
      else -> error("Did not find presenter for $screen")
    } as ScreenPresenter<ScreenT>
  }
}
