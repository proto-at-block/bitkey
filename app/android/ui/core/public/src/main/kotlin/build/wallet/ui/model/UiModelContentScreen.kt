package build.wallet.ui.model

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.ui.components.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlin.time.Duration.Companion.milliseconds
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

/**
 * This class is a bridge between our Bitkey [ScreenModel]s and voyager [VoyagerScreen]s.
 *
 * @property model - [ScreenModel] to be used for rendering [Screen].
 *
 * This class implements [Parcelable] in order to confirm to the Voyager API. However, it the since
 * we don't currently support saving state, we provide an empty implementation
 */
class UiModelContentScreen(model: ScreenModel) : VoyagerScreen, Parcelable {
  /**
   * The screen model for the current screen has a setter which we use to update
   * the screen. This also updates the [modelState] which is used trigger an update on the current
   * screen composable
   */
  private val modelState: MutableState<ScreenModel> = mutableStateOf(model)

  var model = model
    set(value) {
      field = value
      modelState.value = value
    }

  override val key: ScreenKey
    get() = model.key

  @Composable
  override fun Content() {
    Screen(model = modelState.value)
  }

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    dest: Parcel,
    flags: Int,
  ) = Unit

  companion object CREATOR : Parcelable.Creator<UiModelContentScreen> {
    override fun createFromParcel(parcel: Parcel): UiModelContentScreen {
      // This is hard coded to recreate back to the splash screen. Since we don't support saving
      // state yet, this is the intended behavior.
      return UiModelContentScreen(
        model =
          ScreenModel(
            body =
              SplashBodyModel(
                bitkeyWordMarkAnimationDelay = 0.milliseconds,
                bitkeyWordMarkAnimationDuration = 0.milliseconds
              )
          )
      )
    }

    override fun newArray(size: Int): Array<UiModelContentScreen?> {
      return arrayOfNulls(size)
    }
  }
}
