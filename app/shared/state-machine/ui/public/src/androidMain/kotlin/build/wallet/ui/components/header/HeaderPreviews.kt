package build.wallet.ui.components.header

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun HeaderWithIconHeadlineAndSublinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = AnnotatedString("Subline")
    )
  }
}

@Preview
@Composable
fun HeaderWithIconAndHeadlinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = null
    )
  }
}

@Preview
@Composable
fun HeaderWithHeadlineAndSublinePreview() {
  PreviewWalletTheme {
    Header(
      iconModel = null,
      headline = "Headline",
      subline = AnnotatedString("Subline")
    )
  }
}

@Preview
@Composable
internal fun HeaderWithHeadlineAndSublineDarkPreview() {
  PreviewWalletTheme {
    Header(
      modifier = Modifier.background(Color.Black),
      model =
        FormHeaderModel(
          headline = "Headline",
          subline = "Subline"
        ),
      colorMode = ScreenColorMode.Dark
    )
  }
}

@Preview
@Composable
fun HeaderWithHeadlineAndSublineCenteredPreview() {
  PreviewWalletTheme {
    Header(
      iconModel = null,
      headline = "Headline",
      subline = AnnotatedString("Subline"),
      horizontalAlignment = Alignment.CenterHorizontally
    )
  }
}

@Preview
@Composable
fun HeaderWithIconHeadlineAndSublineCenteredPreview() {
  PreviewWalletTheme {
    Header(
      iconModel = IconModel(Icon.LargeIconCheckStroked, IconSize.Avatar),
      headline = "Headline",
      subline = AnnotatedString("Subline"),
      horizontalAlignment = Alignment.CenterHorizontally
    )
  }
}
