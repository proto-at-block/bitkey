package bitkey.ui.framework

/**
 * Provides a way to imperatively navigate between different [Screen]s through [goTo] method.
 *
 * [ScreenPresenter]s of associated [Screen]s can use the [Navigator] to navigate to other [Screen]s.
 *
 * Note that in order to be able to navigate to a [Screen], an appropriate [ScreenPresenter] should
 * be registered in the [ScreenPresenterRegistry].
 *
 * If a [StateMachine] wants to navigate to a [Screen], it needs to use the [NavigatorPresenter]
 * as an interoperable way to navigate to other [Screen]s.
 *
 * [Navigator] should never be created manually.
 */
interface Navigator {
  /**
   * Navigates to the given screen.
   *
   * @param screen The screen to navigate to.
   */
  fun goTo(screen: Screen)

  fun exit()
}
