package bitkey.ui.framework

/**
 * Provides a [ScreenPresenter] for a given [Screen].
 * Each [Screen] should have a corresponding [ScreenPresenter].
 *
 * Note that [SimpleScreen]s do not need to be registered in the [ScreenPresenterRegistry],
 * they should be automatically handled by the [NavigatorPresenter].
 *
 * A likely implementation should look like this:
 *
 * ```kotlin
 * class MyAppScreenPresenterRegistry(
 *   private val settingsScreenPresenter: SettingsScreenPresenter,
 *   private val moneyHomeScreenPresenter: MoneyHomeScreenPresenter
 * ): ScreenPresenterRegistry {
 *    override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
 *      return when (screen) {
 *        is SettingsScreen -> settingsScreenPresenter
 *        is MoneyHomeScreen -> moneyHomeScreenPresenter
 *        else -> error("Unknown screen: ${screen::class.simpleName}")
 *   } as ScreenPresenter<ScreenT>
 * ```
 */
interface ScreenPresenterRegistry {
  fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT>
}
