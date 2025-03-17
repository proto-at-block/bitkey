package build.wallet.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
actual class UiModelContentScreen actual constructor(model: ScreenModel) : VoyagerScreen {
  /**
   * The screen model for the current screen has a setter which we use to update
   * the screen. This also updates the [modelState] which is used trigger an update on the current
   * screen composable
   */
  private val modelState: MutableState<ScreenModel> = mutableStateOf(model)

  actual var model = model
    set(value) {
      field = value
      modelState.value = value
    }

  actual override val key: ScreenKey
    get() = model.key

  @Composable
  actual override fun Content() {
    Screen(model = modelState.value)
  }
}
