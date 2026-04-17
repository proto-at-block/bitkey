@file:Suppress("TooManyFunctions")

package build.wallet.ui.app.nfc

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.catchingResult
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme
import com.github.michaelbull.result.get

@Preview
@Composable
internal fun FwupNfcSearchingPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = Searching(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "FWUP NFC Android DSV2 Ready")
@Composable
internal fun FwupNfcSearchingAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalV2(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = Searching(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "FWUP NFC iOS DSV2 Ready",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
internal fun FwupNfcSearchingIosPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalIos(
      backgroundPainter = fwupIosPreviewBackgroundPainter(),
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = Searching(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "FWUP iOS Background Asset",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
internal fun FwupNfcIosBackgroundAssetPreview() {
  PreviewWalletTheme {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Black)
    ) {
      Image(
        painter = fwupIosPreviewBackgroundPainter(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier =
          Modifier
            .padding(top = 40.dp)
            .fillMaxWidth()
            .align(Alignment.TopCenter)
      )
    }
  }
}

@Preview
@Composable
internal fun FwupNfcProgressPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = InProgress(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "FWUP NFC Android DSV2 Updating")
@Composable
internal fun FwupNfcProgressAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalV2(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = InProgress(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "FWUP NFC iOS DSV2 Updating",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
internal fun FwupNfcProgressIosPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalIos(
      backgroundPainter = fwupIosPreviewBackgroundPainter(),
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = InProgress(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun FwupNfcLostConnectionPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = LostConnection(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "FWUP NFC Android DSV2 Lost Connection")
@Composable
internal fun FwupNfcLostConnectionAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalV2(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = LostConnection(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun FwupNfcSuccessPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = null,
          status = Success(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "FWUP NFC Android DSV2 Success")
@Composable
internal fun FwupNfcSuccessAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
    designSystemUpdatesEnabled = true
  ) {
    FwupNfcScreenInternalV2(
      model =
        FwupNfcBodyModel(
          onCancel = null,
          status = Success(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Composable
private fun fwupIosPreviewBackgroundPainter(): Painter {
  val context = LocalContext.current
  val bitmap = remember {
    sequenceOf(
      catchingResult { context.assets.open("ios_nfc_background_preview.png") }.get(),
      context::class.java.classLoader?.getResourceAsStream("assets/ios_nfc_background_preview.png"),
      Thread.currentThread().contextClassLoader?.getResourceAsStream("assets/ios_nfc_background_preview.png"),
      context::class.java.classLoader?.getResourceAsStream("ios_nfc_background_preview.png"),
      Thread.currentThread().contextClassLoader?.getResourceAsStream("ios_nfc_background_preview.png")
    ).filterNotNull().firstNotNullOfOrNull { inputStream ->
      inputStream.use { BitmapFactory.decodeStream(it) }
    }
  }

  checkNotNull(bitmap) { "Failed to decode preview drawable ios_nfc_background_preview" }
  return BitmapPainter(bitmap.asImageBitmap())
}
