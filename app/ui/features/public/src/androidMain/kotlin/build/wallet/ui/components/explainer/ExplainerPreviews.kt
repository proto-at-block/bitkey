package build.wallet.ui.components.explainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun ExplainerWithSingleStatementPreview() {
  PreviewWalletTheme {
    Explainer {
      Statement(
        icon = Icon.LargeIconCheckStroked,
        title = "First title",
        body = LONG_TEXT
      )
    }
  }
}

@Preview
@Composable
fun ExplainerWithManyStatementsPreview() {
  PreviewWalletTheme {
    Explainer {
      Statement(
        icon = Icon.LargeIconCheckStroked,
        title = "First title",
        body = LONG_TEXT
      )
      Statement(
        icon = Icon.LargeIconCheckStroked,
        title = "Second title",
        body = "Second body"
      )
      Statement(
        icon = Icon.LargeIconCheckStroked,
        title = "Third title",
        body = "Third body"
      )
    }
  }
}

@Preview
@Composable
fun NumberedExplainerWithManyStatementsPreview() {
  PreviewWalletTheme {
    Explainer {
      Statement(
        icon = Icon.SmallIconDigitOne,
        title = "First title",
        body = LONG_TEXT
      )
      Statement(
        icon = Icon.SmallIconDigitTwo,
        title = "Second title",
        body = "Second body"
      )
      Statement(
        icon = Icon.SmallIconDigitThree,
        title = "Third title",
        body = "Third body"
      )
    }
  }
}

private const val LONG_TEXT =
  "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Odio euismod lacinia at quis risus sed vulputate. Vulputate enim nulla aliquet porttitor lacus luctus accumsan tortor."
