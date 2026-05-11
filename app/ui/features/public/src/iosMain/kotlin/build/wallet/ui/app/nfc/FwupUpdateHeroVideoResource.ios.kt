package build.wallet.ui.app.nfc

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.theme.Theme
import kotlinx.cinterop.useContents
import org.jetbrains.compose.resources.painterResource
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView

@Composable
internal actual fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String? {
  return Res.getVideoResource(
    when (hardwareType) {
      HardwareType.W1 -> "pair"
      HardwareType.W3 ->
        if (theme == Theme.DARK) {
          "firmware_update_dark"
        } else {
          "firmware_update_light"
        }
    }
  )
}

@Composable
internal actual fun FwupUpdateHeroPlatformImage(
  modifier: Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
) {
  val imageContent: @Composable (Modifier) -> Unit = if (hardwareType == HardwareType.W1) {
    { heroModifier ->
      FwupUpdateHeroPairImage(
        modifier = heroModifier,
        theme = theme,
        hardwareType = hardwareType,
        alpha = alpha,
        contentScale = contentScale
      )
    }
  } else {
    { heroModifier ->
      FwupUpdateHeroIosStillImage(
        modifier = heroModifier,
        theme = theme,
        alpha = alpha,
        contentScale = contentScale
      )
    }
  }

  imageContent(modifier)
}

@Composable
private fun FwupUpdateHeroPairImage(
  modifier: Modifier = Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
) {
  Image(
    painter = painterResource(updateFirmwareHeroImageResource(theme, hardwareType)),
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    alpha = alpha
  )
}

@Composable
private fun FwupUpdateHeroIosStillImage(
  modifier: Modifier = Modifier,
  theme: Theme,
  alpha: Float,
  contentScale: ContentScale,
) {
  UIKitView(
    factory = {
      object : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
        private val imageView = UIImageView().apply {
          clipsToBounds = true
        }
        var imageName: String = ""
        var currentContentScale: ContentScale = ContentScale.Crop

        init {
          clipsToBounds = true
          addSubview(imageView)
        }

        fun updateImage() {
          val imagePath = requireNotNull(
            NSBundle.mainBundle.pathForResource(
              name = imageName,
              ofType = "png"
            )
          ) {
            "Missing FWUP iOS image resource for name $imageName"
          }
          imageView.image = UIImage.imageWithContentsOfFile(imagePath)
          setNeedsLayout()
        }

        override fun layoutSubviews() {
          super.layoutSubviews()

          val image = imageView.image ?: return
          val boundsWidth = bounds.useContents { size.width }
          val boundsHeight = bounds.useContents { size.height }
          if (boundsWidth <= 0.0 || boundsHeight <= 0.0) return

          val imageWidth = image.size.useContents { width }
          val imageHeight = image.size.useContents { height }
          if (imageWidth <= 0.0 || imageHeight <= 0.0) return

          val scale = when (currentContentScale) {
            ContentScale.Fit -> minOf(boundsWidth / imageWidth, boundsHeight / imageHeight)
            else -> maxOf(boundsWidth / imageWidth, boundsHeight / imageHeight)
          }
          val scaledWidth = imageWidth * scale
          val scaledHeight = imageHeight * scale
          val x = (boundsWidth - scaledWidth) / 2.0
          val y = (boundsHeight - scaledHeight) / 2.0

          imageView.setFrame(CGRectMake(x, y, scaledWidth, scaledHeight))
        }
      }
    },
    modifier = modifier,
    update = { imageView ->
      val nextImageName = if (theme == Theme.DARK) "fwup_update_ios_dark" else "fwup_update_ios_light"
      if (imageView.imageName != nextImageName) {
        imageView.imageName = nextImageName
        imageView.updateImage()
      }
      imageView.currentContentScale = contentScale
      imageView.alpha = alpha.toDouble()
      imageView.setNeedsLayout()
    },
    properties = UIKitInteropProperties(
      isInteractive = false,
      isNativeAccessibilityEnabled = false
    )
  )
}
