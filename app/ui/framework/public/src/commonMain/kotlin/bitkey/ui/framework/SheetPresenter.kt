package bitkey.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel

/**
 * Responsible for managing presentation logic and producing [ScreenModel]s
 * for the corresponding [ScreenT].
 *
 * A [SheetPresenter] should use the [Navigator] to navigate to other [Screen]s.
 *
 * The [SheetPresenter] has to be registered with a [ScreenPresenterRegistry],
 * in order for the [NavigatorPresenter] to be able to produce the right [ScreenModel] for a
 * given [Screen]. Not doing so will result in a runtime error.
 */
fun interface SheetPresenter<SheetT : Sheet> {
  @Composable
  fun model(
    navigator: Navigator,
    sheet: SheetT,
  ): SheetModel
}
