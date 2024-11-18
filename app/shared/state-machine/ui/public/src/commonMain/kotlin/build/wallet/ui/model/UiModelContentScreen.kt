package build.wallet.ui.model

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.components.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

/**
 * This class is a bridge between our Bitkey [ScreenModel]s and voyager [VoyagerScreen]s.
 *
 * @property model - [ScreenModel] to be used for rendering [Screen].
 *
 * This class implements [Parcelable] in order to confirm to the Voyager API. However, it the since
 * we don't currently support saving state, we provide an empty implementation
 */
expect class UiModelContentScreen(model: ScreenModel) : VoyagerScreen {
  var model: ScreenModel

  override val key: ScreenKey

  @Composable
  override fun Content()
}
