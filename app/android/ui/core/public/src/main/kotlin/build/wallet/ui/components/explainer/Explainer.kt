package build.wallet.ui.components.explainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

/**
 * Slot-based implementation, meant to be used with [Statement].
 */
@Composable
fun Explainer(
  modifier: Modifier = Modifier,
  statementsContent: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    statementsContent()
  }
}

@Preview
@Composable
internal fun ExplainerWithSingleStatementPreview() {
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
internal fun ExplainerWithManyStatementsPreview() {
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
internal fun NumberedExplainerWithManyStatementsPreview() {
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
