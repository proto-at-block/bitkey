package build.wallet.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.ContainerStyle.BORDERLESS
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

@Composable
fun DataGroup(
  modifier: Modifier = Modifier,
  rows: DataList,
) {
  val isDesignSystemV2Enabled = true
  val lineColor =
    if (isDesignSystemV2Enabled && LocalTheme.current == Theme.DARK) {
      WalletTheme.colors.foreground30
    } else {
      WalletTheme.colors.foreground10
    }
  val useBorderlessContainer = rows.containerStyle == BORDERLESS
  val useContainedDesignSystemV2Typography = isDesignSystemV2Enabled && !useBorderlessContainer
  val contentHorizontalPadding = if (useBorderlessContainer) 0.dp else 16.dp
  val cornerRadius = if (isDesignSystemV2Enabled) 8.dp else 16.dp
  val containerShape = RoundedCornerShape(cornerRadius)
  val totalDividerModifier =
    if (isDesignSystemV2Enabled && !useBorderlessContainer) {
      Modifier.padding(horizontal = contentHorizontalPadding)
    } else {
      Modifier
    }
  val backgroundColor =
    if (isDesignSystemV2Enabled && !useBorderlessContainer) {
      WalletTheme.colors.secondary
    } else {
      WalletTheme.colors.background
    }
  Column(
    modifier =
      modifier
        .background(
          color = backgroundColor,
          shape = containerShape
        )
        .thenIf(!useBorderlessContainer && !isDesignSystemV2Enabled) {
          Modifier.border(
            width = 2.dp,
            color = lineColor,
            shape = containerShape
          )
        }
        .clip(containerShape),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    rows.hero?.let {
      Spacer(Modifier.height(20.dp))
      DataHero(model = it)
    }
    rows.items.forEachIndexed { idx, element ->
      DataRowRegular(
        model = element,
        isFirst = idx == 0,
        contentHorizontalPadding = contentHorizontalPadding,
        useContainedDesignSystemV2Typography = useContainedDesignSystemV2Typography
      )
    }
    rows.total?.let {
      Divider(
        modifier = totalDividerModifier,
        color = lineColor
      )
      DataRowTotal(
        model = it,
        contentHorizontalPadding = contentHorizontalPadding,
        useContainedDesignSystemV2Typography = useContainedDesignSystemV2Typography
      )
    }
    rows.buttons.forEach {
      Button(modifier = Modifier.padding(vertical = 8.dp), model = it)
      Spacer(Modifier.height(20.dp))
    }
  }
}
