package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.inter_medium
import bitkey.ui.framework_public.generated.resources.inter_regular
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.components.callout.CalloutButton
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.market.MarketIcons
import org.jetbrains.compose.resources.Font

@Composable
fun PendingClaimContent(
  model: CardModel.CardContent.PendingClaim,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTheme.current
  val useMonochromeStyle =
    LocalDesignSystemUpdatesEnabled.current && model.useMonochromeStyleInDesignSystemV2
  val cornerRadius = if (LocalDesignSystemUpdatesEnabled.current) 8.dp else 16.dp
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = if (useMonochromeStyle) WalletTheme.colors.secondary else WalletTheme.colors.calloutInformationBackground,
        shape = RoundedCornerShape(size = cornerRadius)
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = pendingClaimVerticalAlignment(
        useMonochromeStyle = useMonochromeStyle,
        isPendingClaim = model.isPendingClaim
      ),
      horizontalArrangement = Arrangement.Start
    ) {
      PendingClaimLeadingIcon(
        model = model,
        useMonochromeStyle = useMonochromeStyle,
        theme = theme
      )

      Column(
        modifier = Modifier
          .padding(start = 16.dp)
          .weight(1f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
      ) {
        Label(
          text = model.title,
          style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily(Font(Res.font.inter_medium)),
            fontWeight = FontWeight(500),
            color = if (useMonochromeStyle) WalletTheme.colors.foreground else WalletTheme.colors.calloutInformationTitle
          )
        )
        Label(
          model = LabelModel.StringModel(model.subtitle),
          modifier = Modifier
            .padding(top = 4.dp)
            .alpha(if (useMonochromeStyle) 1f else 0.6f),
          style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily(Font(Res.font.inter_regular)),
            fontWeight = FontWeight(400),
            color = if (useMonochromeStyle) WalletTheme.colors.foreground60 else WalletTheme.colors.calloutInformationSubtitle
          )
        )
      }

      PendingClaimAction(
        model = model,
        useMonochromeStyle = useMonochromeStyle,
        theme = theme
      )
    }
  }
}

private fun pendingClaimVerticalAlignment(
  useMonochromeStyle: Boolean,
  isPendingClaim: Boolean,
) =
  when {
    useMonochromeStyle -> Alignment.CenterVertically
    isPendingClaim -> Alignment.Top
    else -> Alignment.CenterVertically
  }

@Composable
private fun PendingClaimLeadingIcon(
  model: CardModel.CardContent.PendingClaim,
  useMonochromeStyle: Boolean,
  theme: Theme,
) {
  Box {
    IconImage(
      model = pendingClaimLeadingIconModel(
        isPendingClaim = model.isPendingClaim,
        useMonochromeStyle = useMonochromeStyle,
        theme = theme
      )
    )
    if (showPendingClaimProgressIndicator(useMonochromeStyle, model.isPendingClaim)) {
      CircularProgressIndicator(
        progress = model.progress.value,
        direction = TimerDirection.Clockwise,
        remainingSeconds = model.timeRemaining.inWholeSeconds,
        size = 40.dp,
        indicatorColor = pendingClaimProgressColor(useMonochromeStyle, theme),
        backgroundColor = Color.Unspecified,
        strokeWidth = if (useMonochromeStyle) 3.dp else 4.dp
      )
    }
  }
}

private fun pendingClaimLeadingIconModel(
  isPendingClaim: Boolean,
  useMonochromeStyle: Boolean,
  theme: Theme,
): IconModel {
  if (useMonochromeStyle && isPendingClaim) {
    return IconModel(
      icon = MarketIcons.ShieldHuman,
      iconSize = IconSize.Accessory,
      iconTint = IconTint.On60,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.Large,
        color = IconBackgroundType.Circle.CircleColor.SubtleBackground
      )
    )
  }

  return IconModel(
    icon = if (isPendingClaim) Icon.SmallIconClockHands else Icon.SmallIconCheckInheritance,
    iconSize = IconSize.Accessory,
    iconTint = when {
      useMonochromeStyle -> IconTint.On60
      theme == Theme.LIGHT -> IconTint.Information
      else -> IconTint.Foreground
    },
    iconBackgroundType = IconBackgroundType.Circle(
      circleSize = IconSize.Large,
      color = when {
        useMonochromeStyle -> IconBackgroundType.Circle.CircleColor.SubtleBackground
        theme == Theme.LIGHT -> IconBackgroundType.Circle.CircleColor.Information
        else -> IconBackgroundType.Circle.CircleColor.TransparentForeground
      }
    )
  )
}

private fun showPendingClaimProgressIndicator(
  useMonochromeStyle: Boolean,
  isPendingClaim: Boolean,
) = !useMonochromeStyle || isPendingClaim

@Composable
private fun pendingClaimProgressColor(
  useMonochromeStyle: Boolean,
  theme: Theme,
) =
  when {
    useMonochromeStyle -> WalletTheme.colors.foreground30
    theme == Theme.LIGHT -> WalletTheme.colors.calloutInformationTrailingIconBackground.copy(alpha = 0.33f)
    else -> WalletTheme.colors.foreground
  }

@Composable
private fun PendingClaimAction(
  model: CardModel.CardContent.PendingClaim,
  useMonochromeStyle: Boolean,
  theme: Theme,
) {
  Column {
    if (model.isPendingClaim) {
      model.onClick?.let { onClick ->
        IconButton(
          modifier = Modifier.padding(start = 12.dp, end = 0.dp),
          iconModel = IconModel(
            icon = Icon.SmallIconXFilled,
            iconSize = IconSize.Accessory,
            iconTint = pendingClaimDismissIconTint(
              useMonochromeStyle = useMonochromeStyle,
              theme = theme
            )
          ),
          onClick = {
            onClick.invoke()
          }
        )
      }
    } else {
      CalloutButton(
        Icon.SmallIconArrowRight,
        if (useMonochromeStyle) WalletTheme.colors.foreground60 else WalletTheme.colors.calloutDefaultTrailingIcon,
        if (useMonochromeStyle) CalloutModel.Treatment.Default else CalloutModel.Treatment.Information,
        StandardClick { model.onClick?.invoke() },
        useInverseButtonStyle = useMonochromeStyle
      )
    }
  }
}

private fun pendingClaimDismissIconTint(
  useMonochromeStyle: Boolean,
  theme: Theme,
) =
  when {
    useMonochromeStyle -> IconTint.On60
    theme == Theme.LIGHT -> IconTint.Information
    else -> IconTint.On30
  }
