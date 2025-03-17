package build.wallet.statemachine.core

import build.wallet.platform.data.MimeType
import okio.Source

sealed interface SystemUIModel {
  data class MediaPickerModel(
    val onMediaPicked: (List<Media>) -> Unit,
  ) : SystemUIModel {
    data class Media(
      val name: String,
      val mimeType: MimeType,
      val data: suspend () -> Source?,
    )
  }
}
