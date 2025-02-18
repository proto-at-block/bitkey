@file:OptIn(ExperimentalForeignApi::class)

package build.wallet.ui.components.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import build.wallet.platform.data.MimeType
import build.wallet.statemachine.core.SystemUIModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSError
import platform.Foundation.NSProgress
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudiovisualContent
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Composable
actual fun MediaPicker(model: SystemUIModel.MediaPickerModel) {
  val uiViewController = LocalUIViewController.current
  val pickerDelegate = remember {
    object : NSObject(), PHPickerViewControllerDelegateProtocol {
      override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
      ) {
        val media = didFinishPicking
          .filterIsInstance<PHPickerResult>()
          .mapNotNull(::media)
        model.onMediaPicked(media)

        picker.dismissViewControllerAnimated(flag = true, completion = {})
      }

      fun media(result: PHPickerResult): SystemUIModel.MediaPickerModel.Media? {
        val itemProvider = result.itemProvider
        val supportedTypes = listOf(UTTypeImage, UTTypeAudiovisualContent)

        val mediaType = supportedTypes
          .firstOrNull { itemProvider.hasItemConformingToTypeIdentifier(it.identifier) }
          ?: run {
            // TODO: We should inform the user that we'll skip this media and why.
            return null
          }

        val conformingRegisteredContentTypes = itemProvider.registeredTypeIdentifiers
          .filterIsInstance<String>()
          .mapNotNull { identifierString ->
            val identifier = checkNotNull(UTType.typeWithIdentifier(identifierString))
            val preferredFilenameExtension = checkNotNull(identifier.preferredFilenameExtension)
            UTType.typeWithFilenameExtension(preferredFilenameExtension, mediaType)
          }

        val mimeType = conformingRegisteredContentTypes
          .firstNotNullOfOrNull { it.preferredMIMEType }
          ?: run {
            // TODO: We should inform the user that we'll skip this media and why.
            return null
          }

        val fileExtension = conformingRegisteredContentTypes
          .firstNotNullOfOrNull { contentType ->
            contentType.preferredFilenameExtension?.let { ".$it" }
          }.orEmpty()

        return nativeMedia(
          name = buildString {
            append(itemProvider.suggestedName ?: "unknown")
            append(fileExtension)
          },
          mimeType = MimeType(name = mimeType),
          loadUrl = { callback ->
            val identifier = mediaType.identifier
            itemProvider.loadFileRepresentationForTypeIdentifier(identifier) { url, error ->
              if (url == null) {
                callback(null, error)
              } else {
                callback(url, null)
              }
            }
          }
        )
      }
    }
  }

  LaunchedEffect("present-phpicker-viewcontroller") {
    val configuration = PHPickerConfiguration().apply {
      filter = PHPickerFilter.anyFilterMatchingSubfilters(
        listOf(
          PHPickerFilter.imagesFilter,
          PHPickerFilter.screenshotsFilter,
          PHPickerFilter.videosFilter,
          PHPickerFilter.screenRecordingsFilter
        )
      )
      preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
    }

    val controller = PHPickerViewController(configuration)
    controller.setDelegate(pickerDelegate)
    uiViewController.presentViewController(controller, animated = true, completion = null)
  }
}

private fun nativeMedia(
  name: String,
  mimeType: MimeType,
  loadUrl: ((NSURL?, NSError?) -> Unit) -> NSProgress,
): SystemUIModel.MediaPickerModel.Media {
  return SystemUIModel.MediaPickerModel.Media(
    name = name,
    mimeType = mimeType,
    data = {
      suspendCancellableCoroutine { continuation ->
        val progress =
          loadUrl { url, _ ->
            // TODO[W-5828]: Recognize and propagate `NSError` we receive.
            val source =
              url?.path?.toPath()?.let {
                FileSystem.SYSTEM.source(it)
              }
            continuation.resume(source)
          }
        continuation.invokeOnCancellation {
          progress.cancel()
        }
      }
    }
  )
}
