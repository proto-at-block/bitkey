package build.wallet.ui.components.explainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun StatementWithIconAndTitleAndBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
fun StatementWithShortTitleAndLongBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title",
      body = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun StatementWithTitleOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = "Statement Title"
    )
  }
}

@Preview
@Composable
fun StatementWithLongTitleOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun StatementWithBodyOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = null,
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
fun StatementWithLongBodyOnlyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.LargeIconCheckStroked,
      title = null,
      body = LONG_TEXT
    )
  }
}

@Preview
@Composable
fun NumberedStatementWithTitleAndBodyPreview() {
  PreviewWalletTheme {
    Statement(
      icon = Icon.SmallIconDigitTwo,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

@Preview
@Composable
internal fun StatementWithNoIconPreview() {
  PreviewWalletTheme {
    Statement(
      icon = null,
      title = "Statement Title",
      body = "Statement Body"
    )
  }
}

private const val LONG_TEXT =
  "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Odio euismod lacinia at quis risus sed vulputate. Vulputate enim nulla aliquet porttitor lacus luctus accumsan tortor."
