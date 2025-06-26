package build.wallet.ui.components.amount

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun HeroAmountWithFullGhosted() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount =
        buildAnnotatedString {
          append("$1,221.00")
          addStyle(
            style = SpanStyle(color = WalletTheme.colors.foreground30),
            start = 6,
            end = 9
          )
        },
      contextLine = "4,792,442 sats"
    )
  }
}

@Preview
@Composable
fun HeroAmountWithSomeGhosted() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount =
        buildAnnotatedString {
          append("$1,221.00")
          addStyle(
            style = SpanStyle(color = WalletTheme.colors.foreground30),
            start = 8,
            end = 9
          )
        },
      contextLine = "4,792,442 sats"
    )
  }
}

@Preview
@Composable
fun HeroAmountWithNoGhosted() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$1,221.00"),
      contextLine = "4,792,442 sats"
    )
  }
}

@Preview
@Composable
fun HeroAmountSwappable() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$1,221.00"),
      contextLine = "4,792,442 sats",
      onSwapClick = {}
    )
  }
}

@Preview
@Composable
fun HeroAmountDisabled() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$1,221.00"),
      contextLine = "4,792,442 sats",
      disabled = true,
      onSwapClick = {}
    )
  }
}

@Preview
@Composable
fun HeroAmountNoSecondary() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$1,221.00"),
      contextLine = null,
      disabled = true,
      onSwapClick = {}
    )
  }
}

@Preview
@Composable
fun HeroAmountHideAmount() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$1,221.00"),
      contextLine = "test",
      disabled = true,
      hideBalance = true,
      onSwapClick = {}
    )
  }
}

@Preview
@Composable
fun HeroAmountLargeAmount() {
  PreviewWalletTheme {
    HeroAmount(
      primaryAmount = AnnotatedString("$88,888,888.88"),
      contextLine = "153,984,147,317 sats",
      onSwapClick = {}
    )
  }
}
