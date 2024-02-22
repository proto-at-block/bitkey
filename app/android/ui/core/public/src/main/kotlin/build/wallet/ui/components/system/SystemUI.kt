package build.wallet.ui.components.system

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.SystemUIModel

@Composable
fun SystemUI(model: SystemUIModel) {
  when (model) {
    is SystemUIModel.MediaPickerModel -> MediaPicker(model)
  }
}
