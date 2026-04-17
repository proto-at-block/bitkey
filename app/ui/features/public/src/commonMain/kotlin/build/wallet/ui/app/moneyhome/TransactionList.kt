package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.list.ListModel
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.ListHeader
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun TransactionList(
  modifier: Modifier = Modifier,
  model: ListModel,
  hideValue: Boolean = false,
) {
  Column(
    modifier = modifier.background(WalletTheme.colors.background)
  ) {
    val headerType =
      if (LocalDesignSystemUpdatesEnabled.current) {
        LabelType.Body3Mono
      } else {
        LabelType.Title2
      }

    model.headerText?.let {
      ListHeader(
        title = it,
        titleType = headerType
      )
    }

    val isEmpty = model.sections.isEmpty() || model.sections.all { it.items.isEmpty() }
    if (isEmpty) {
      EmptyTransactionState()
    } else {
      Column {
        model.sections.forEach { section ->
          ListGroup(
            model = section,
            collapseContent = hideValue
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyTransactionState() {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val primaryLabelType = if (isDesignSystemV2Enabled) {
    LabelType.Body2Regular
  } else {
    LabelType.Title3
  }
  val secondaryLabelType = if (isDesignSystemV2Enabled) {
    LabelType.Body3Mono
  } else {
    LabelType.Body1Regular
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (isDesignSystemV2Enabled) {
      IconImage(
        model = IconModel(
          icon = Icon.DotIconsSearch,
          iconSize = IconSize.XLarge,
          iconOpacity = 0.5f
        )
      )
    } else {
      IconImage(
        model = IconModel(
          icon = Icon.SmallIconClock,
          iconSize = IconSize.XLarge,
          iconTint = IconTint.On60
        )
      )
    }
    Spacer(Modifier.height(16.dp))
    Label(
      text = "Nothing to see, yet",
      type = primaryLabelType,
      alignment = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Label(
      text = "Your transactions will appear here",
      type = secondaryLabelType,
      color = WalletTheme.colors.foreground60,
      alignment = TextAlign.Center
    )
  }
}
