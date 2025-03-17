package build.wallet.ui.components.system

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.SystemUIModel

@Composable
expect fun MediaPicker(model: SystemUIModel.MediaPickerModel)
