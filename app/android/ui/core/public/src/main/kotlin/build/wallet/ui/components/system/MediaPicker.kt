package build.wallet.ui.components.system

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import build.wallet.platform.data.MimeType
import build.wallet.statemachine.core.SystemUIModel
import okio.source

@Composable
fun MediaPicker(model: SystemUIModel.MediaPickerModel) {
  val context = LocalContext.current
  val launcher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri ->
        val type = uri?.let { context.contentResolver.getType(it) }?.let(::MimeType)
        if (uri != null && type != null) {
          val fileExtension =
            MimeTypeMap.getSingleton().getExtensionFromMimeType(type.name)?.let {
              ".$it"
            }.orEmpty()
          model.onMediaPicked(
            listOf(
              SystemUIModel.MediaPickerModel.Media(
                name = (uri.lastPathSegment ?: "unknown") + fileExtension,
                mimeType = type,
                data = {
                  context.contentResolver.openInputStream(uri)?.source()
                }
              )
            )
          )
        } else {
          model.onMediaPicked(emptyList())
        }
      }
    )

  LaunchedEffect("picking-photo", model) {
    launcher.launch(
      // TODO: Implement configuration for the type of media
      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    )
  }
}
