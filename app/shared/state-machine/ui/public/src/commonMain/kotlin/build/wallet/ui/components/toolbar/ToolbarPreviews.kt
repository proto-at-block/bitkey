package build.wallet.ui.components.toolbar

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType.Label3
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun ToolbarWithLeadingContentOnlyPreview() {
  PreviewWalletTheme {
    Toolbar(
      leadingContent = {
        Label(text = "Leading", type = Label3)
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithMiddleContentOnlyPreview() {
  PreviewWalletTheme {
    Toolbar(
      middleContent = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Label(text = "Middle", type = Label3)
          Label(text = "Hello, there", type = Label3)
        }
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithTrailingContentOnlyLabelPreview() {
  PreviewWalletTheme {
    Toolbar(
      trailingContent = {
        Label(text = "Trailing", type = Label3)
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithTrailingContentOnlyButtonPreview() {
  PreviewWalletTheme {
    Toolbar(
      model = ToolbarModel(
        trailingAccessory = ToolbarAccessoryModel.ButtonAccessory(
          ButtonModel(
            text = "Trailing Button",
            size = ButtonModel.Size.Compact,
            treatment = ButtonModel.Treatment.TertiaryDestructive,
            onClick = StandardClick {}
          )
        )
      )
    )
  }
}

@Preview
@Composable
fun ToolbarWithLeadingAndMiddleContentPreview() {
  PreviewWalletTheme {
    Toolbar(
      leadingContent = {
        Label(text = "Leading", type = Label3)
      },
      middleContent = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Label(text = "Middle", type = Label3)
          Label(text = "Hello, there", type = Label3)
        }
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithLeadingAndTrailingContentPreview() {
  PreviewWalletTheme {
    Toolbar(
      leadingContent = {
        Label(text = "Leading", type = Label3)
      },
      trailingContent = {
        Label(text = "Trailing", type = Label3)
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithMiddleAndTrailingContentPreview() {
  PreviewWalletTheme {
    Toolbar(
      middleContent = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Label(text = "Middle", type = Label3)
          Label(text = "Hello, there", type = Label3)
        }
      },
      trailingContent = {
        Label(text = "Trailing", type = Label3)
      }
    )
  }
}

@Preview
@Composable
fun ToolbarWithAllContentsPreview() {
  PreviewWalletTheme {
    Toolbar(
      leadingContent = {
        Label(text = "Leading", type = Label3)
      },
      middleContent = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Label(text = "Middle", type = Label3)
          Label(text = "Hello, there", type = Label3)
        }
      },
      trailingContent = {
        Label(text = "Trailing", type = Label3)
      }
    )
  }
}
