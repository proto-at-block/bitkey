package build.wallet.ui.components.loading

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.dot_icons_search_loader
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconImage.DrawableResourceImage
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.XLarge
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import build.wallet.ui.components.icon.IconImage as WalletIconImage

@Composable
fun DesignSystemDotIndicator(
  modifier: Modifier = Modifier,
  icon: IconImage,
) {
  Crossfade(targetState = icon, label = "design-system-dot-indicator") { currentIcon ->
    WalletIconImage(
      modifier = modifier,
      model = IconModel(
        iconImage = currentIcon,
        iconSize = XLarge,
        iconTint = Foreground
      )
    )
  }
}

@Composable
fun DesignSystemDotIndicator(
  modifier: Modifier = Modifier,
  icon: Icon,
) {
  DesignSystemDotIndicator(
    modifier = modifier,
    icon = LocalImage(icon)
  )
}

@Composable
fun DesignSystemDotLoadingIndicator(modifier: Modifier = Modifier) {
  DesignSystemDotIndicator(
    modifier = modifier,
    icon = rememberShuffledDotLoadingIcon().toDesignSystemDotIndicatorIcon()
  )
}

@Composable
fun rememberShuffledDotLoadingIcon(enabled: Boolean = true): Icon {
  if (LocalIsPreviewTheme.current) {
    return Icon.DotLoading
  }

  var shuffledIcons by remember { mutableStateOf(shuffleDotIcons()) }
  var currentIconIndex by remember { mutableIntStateOf(0) }

  LaunchedEffect(enabled) {
    if (!enabled) return@LaunchedEffect

    while (true) {
      delay(400.milliseconds)

      if (currentIconIndex == shuffledIcons.lastIndex) {
        val previousIcon = shuffledIcons[currentIconIndex]
        shuffledIcons = shuffleDotIcons(previousIcon = previousIcon)
        currentIconIndex = 0
      } else {
        currentIconIndex += 1
      }
    }
  }

  return shuffledIcons[currentIconIndex]
}

private fun shuffleDotIcons(previousIcon: Icon? = null): List<Icon> {
  val shuffledIcons = designSystemDotIcons.shuffled()
  if (previousIcon == null || shuffledIcons.size <= 1 || shuffledIcons.first() != previousIcon) {
    return shuffledIcons
  }

  return shuffledIcons.drop(1) + shuffledIcons.first()
}

private val designSystemDotIcons =
  listOf(
    Icon.DotAddressVerification,
    Icon.DotAppKey,
    Icon.DotAppSecurity,
    Icon.DotBitcoin,
    Icon.DotBitkey,
    Icon.DotCloud,
    Icon.DotCloudBackup,
    Icon.DotCoins,
    Icon.DotCommunication,
    Icon.DotCriticalAlerts,
    Icon.DotDevelopers,
    Icon.DotEmergency,
    Icon.DotEmptyState,
    Icon.DotFingerprint,
    Icon.DotFingerprintsMultiple,
    Icon.DotIconsSearch,
    Icon.DotInheritance,
    Icon.DotLab,
    Icon.DotLoading,
    Icon.DotMobile,
    Icon.DotNews,
    Icon.DotNotifyEmail,
    Icon.DotNotifyPush,
    Icon.DotNotifySms,
    Icon.DotPair,
    Icon.DotPrivacy,
    Icon.DotRecoveryContact,
    Icon.DotSecurity,
    Icon.DotServer,
    Icon.DotTakes,
    Icon.DotVaults,
    Icon.DotWorld
  )

private fun Icon.toDesignSystemDotIndicatorIcon(): IconImage =
  when (this) {
    Icon.DotIconsSearch -> DrawableResourceImage(Res.drawable.dot_icons_search_loader)
    else -> LocalImage(this)
  }
