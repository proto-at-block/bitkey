package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import bitkey.ui.framework_public.generated.resources.*
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Interface for theme-specific icon providers
private interface StyleDictionaryIcons {
  fun getDrawableResource(icon: Icon): DrawableResource
}

private class LightStyleDictionaryIcons : StyleDictionaryIcons {
  override fun getDrawableResource(icon: Icon): DrawableResource =
    when (icon) {
      Bitcoin -> Res.drawable.bitcoin
      BitcoinOrange -> Res.drawable.bitcoin_orange
      BitcoinConsolidation -> Res.drawable.bitcoin_consolidation
      BitcoinBadged -> Res.drawable.bitcoin_badged
      BitkeyDevice3D -> Res.drawable.bitkey_device_3d
      BitkeyDeviceRaised -> Res.drawable.bitkey_device_raised
      BitkeyDeviceRaisedSmall -> Res.drawable.bitkey_device_raised_small
      BitkeyLogo -> Res.drawable.bitkey_logo
      BuyOwnBitkeyHero -> Res.drawable.buy_own_bitkey_hero
      CloudBackupEmergencyExitKit -> Res.drawable.cloud_backup_emergency_access_kit
      CloudBackupMobileKey -> Res.drawable.cloud_backup_mobile_key
      InheritanceShowcase -> Res.drawable.inheritance_showcase
      InheritancePlanHero -> Res.drawable.inheritance_plan_hero
      LargeIconAdd -> Res.drawable.large_icon_add
      LargeIconConsolidationFilled -> Res.drawable.large_icon_consolidation_filled
      LargeIconMinus -> Res.drawable.large_icon_minus
      LargeIconCheckFilled -> Res.drawable.large_icon_check_filled
      LargeIconCheckStroked -> Res.drawable.large_icon_check_stroked
      LargeIconEllipsisFilled -> Res.drawable.large_icon_ellipsis_filled
      LargeIconNetworkError -> Res.drawable.large_icon_network_error
      LargeIconReceive -> Res.drawable.large_icon_receive
      LargeIconSend -> Res.drawable.large_icon_send
      LargeIconShieldPerson -> Res.drawable.large_icon_shield_person
      LargeIconSpeedometer -> Res.drawable.large_icon_speedometer
      LargeIconWarning -> Res.drawable.large_icon_warning
      LargeIconWarningFilled -> Res.drawable.large_icon_warning_filled
      LargeIconWarningStroked -> Res.drawable.large_icon_warning_stroked
      MediumIconQrCode -> Res.drawable.medium_icon_qr_code
      MediumIconTrustedContact -> Res.drawable.medium_icon_trusted_contact
      MoneyHomeHero -> Res.drawable.money_home_hero
      SecurityHubEducationTrustedContact -> Res.drawable.hero_recovery_contacts
      SecurityHubEducationMultipleFingerprints -> Res.drawable.hero_multiple_fingerprints
      SecurityHubEducationEmergencyExit -> Res.drawable.hero_eak
      SecurityHubEducationCriticalAlerts -> Res.drawable.hero_critical_alerts
      SmallIconAccount -> Res.drawable.small_icon_account
      SmallIconAnnouncement -> Res.drawable.small_icon_announcement
      SmallIconArrowDown -> Res.drawable.small_icon_arrow_down
      SmallIconArrowLeft -> Res.drawable.small_icon_arrow_left
      SmallIconArrowRight -> Res.drawable.small_icon_arrow_right
      SmallIconArrowUp -> Res.drawable.small_icon_arrow_up
      SmallIconArrowUpRight -> Res.drawable.small_icon_arrow_up_right
      SmallIconBitcoinStroked -> Res.drawable.small_icon_bitcoin_stroked
      SmallIconBitkey -> Res.drawable.small_icon_bitkey
      SmallIconBitkeyReceive -> Res.drawable.small_icon_bitkey_receive
      SmallIconBitkeySend -> Res.drawable.small_icon_bitkey_send
      SmallIconCaretDown -> Res.drawable.small_icon_caret_down
      SmallIconCaretLeft -> Res.drawable.small_icon_caret_left
      SmallIconCaretRight -> Res.drawable.small_icon_caret_right
      SmallIconCaretUp -> Res.drawable.small_icon_caret_up
      SmallIconCheck -> Res.drawable.small_icon_check
      SmallIconCheckInheritance -> Res.drawable.small_icon_check_inheritance
      SmallIconCheckbox -> Res.drawable.small_icon_checkbox
      SmallIconCheckboxSelected -> Res.drawable.small_icon_checkbox_selected
      SmallIconCheckFilled -> Res.drawable.small_icon_check_filled
      SmallIconCheckStroked -> Res.drawable.small_icon_check_stroked
      SmallIconCircleStroked -> Res.drawable.small_icon_circle_stroked
      SmallIconClipboard -> Res.drawable.small_icon_clipboard
      SmallIconClock -> Res.drawable.small_icon_clock
      SmallIconClockHands -> Res.drawable.small_icon_clock_hands
      SmallIconCloud -> Res.drawable.small_icon_cloud
      SmallIconCloudError -> Res.drawable.small_icon_cloud_error
      SmallIconConsolidation -> Res.drawable.small_icon_consolidation
      SmallIconCopy -> Res.drawable.small_icon_copy
      SmallIconCurrency -> Res.drawable.small_icon_currency
      SmallIconDigitOne -> Res.drawable.small_icon_digit_one
      SmallIconDigitThree -> Res.drawable.small_icon_digit_three
      SmallIconDigitTwo -> Res.drawable.small_icon_digit_two
      SmallIconDocument -> Res.drawable.small_icon_document
      SmallIconDownload -> Res.drawable.small_icon_download
      SmallIconElectrum -> Res.drawable.small_icon_electrum
      SmallIconEmail -> Res.drawable.small_icon_email
      SmallIconExternal -> Res.drawable.small_icon_external
      SmallIconFingerprint -> Res.drawable.small_icon_fingerprint
      SmallIconInformation -> Res.drawable.small_icon_information
      SmallIconInformationFilled -> Res.drawable.small_icon_information_filled
      SmallIconInheritance -> Res.drawable.small_icon_inheritance
      SmallIconKey -> Res.drawable.small_icon_key
      SmallIconKeyboard -> Res.drawable.small_icon_keyboard
      SmallIconLightning -> Res.drawable.small_icon_lightning
      SmallIconLock -> Res.drawable.small_icon_lock
      SmallIconMessage -> Res.drawable.small_icon_message
      SmallIconMinus -> Res.drawable.small_icon_minus
      SmallIconMinusFilled -> Res.drawable.small_icon_minus_filled
      SmallIconMinusStroked -> Res.drawable.small_icon_minus_stroked
      SmallIconMobileKey -> Res.drawable.small_icon_mobile_key
      SmallIconMobileLimit -> Res.drawable.small_icon_mobile_limit
      SmallIconNotification -> Res.drawable.small_icon_notification
      SmallIconPaintBrush -> Res.drawable.small_icon_paint_brush
      SmallIconPause -> Res.drawable.small_icon_pause
      SmallIconPauseFilled -> Res.drawable.small_icon_pause_filled
      SmallIconPauseStroked -> Res.drawable.small_icon_pause_stroked
      SmallIconPhone -> Res.drawable.small_icon_phone
      SmallIconPlus -> Res.drawable.small_icon_plus
      SmallIconPlusFilled -> Res.drawable.small_icon_plus_filled
      SmallIconPlusStroked -> Res.drawable.small_icon_plus_stroked
      SmallIconPushNotification -> Res.drawable.small_icon_push_notification
      SmallIconQrCode -> Res.drawable.small_icon_qr_code
      SmallIconQuestion -> Res.drawable.small_icon_question
      SmallIconQuestionNoOutline -> Res.drawable.small_icon_question_no_outline
      SmallIconRecovery -> Res.drawable.small_icon_recovery
      SmallIconRefresh -> Res.drawable.small_icon_refresh
      SmallIconScan -> Res.drawable.small_icon_scan
      SmallIconSettings -> Res.drawable.small_icon_settings
      SmallIconSettingsBadged -> Res.drawable.small_icon_settings_badged
      SmallIconShare -> Res.drawable.small_icon_share
      SmallIconShield -> Res.drawable.small_icon_shield
      SmallIconShieldFilled -> Res.drawable.small_icon_shield_filled
      SmallIconShieldCheck -> Res.drawable.small_icon_shield_check
      SmallIconShieldPerson -> Res.drawable.small_icon_shield_person
      SmallIconSpeed -> Res.drawable.small_icon_speed
      SmallIconStar -> Res.drawable.small_icon_star
      SmallIconSwap -> Res.drawable.small_icon_swap
      SmallIconSync -> Res.drawable.small_icon_sync
      SmallIconTicket -> Res.drawable.small_icon_ticket
      SmallIconVideo -> Res.drawable.small_icon_video
      SmallIconWallet -> Res.drawable.small_icon_wallet
      SmallIconWalletFilled -> Res.drawable.small_icon_wallet_filled
      SmallIconWarning -> Res.drawable.small_icon_warning
      SmallIconWarningFilled -> Res.drawable.small_icon_warning_filled
      SmallIconX -> Res.drawable.small_icon_x
      SmallIconXFilled -> Res.drawable.small_icon_xfilled
      SmallIconXStroked -> Res.drawable.small_icon_xstroked
      CalloutArrow -> Res.drawable.callout_arrow
      ThemeLight -> Res.drawable.theme_light
      ThemeDark -> Res.drawable.theme_dark
      ThemeSystem -> Res.drawable.theme_system
      WarningBadge -> Res.drawable.warning_badge
      Insights -> Res.drawable.insights
    }
}

private class DarkStyleDictionaryIcons(
  private val lightIcons: StyleDictionaryIcons,
) : StyleDictionaryIcons {
  override fun getDrawableResource(icon: Icon): DrawableResource =
    when (icon) {
      BitcoinBadged -> Res.drawable.bitcoin_badged_dark
      MoneyHomeHero -> Res.drawable.money_home_hero_dark
      LargeIconNetworkError -> Res.drawable.large_icon_network_error_dark
      SmallIconSettingsBadged -> Res.drawable.small_icon_settings_badged_dark

      // For all other icons, fall back to the light theme icons
      else -> lightIcons.getDrawableResource(icon)
    }
}

@Composable
fun Icon.painter() = painterResource(getDrawableResourceForCurrentTheme())

@Composable
private fun Icon.getDrawableResourceForCurrentTheme(): DrawableResource {
  val theme = LocalTheme.current
  val lightIcons = LightStyleDictionaryIcons()

  return when (theme) {
    Theme.LIGHT -> lightIcons.getDrawableResource(this)
    Theme.DARK -> DarkStyleDictionaryIcons(lightIcons).getDrawableResource(this)
  }
}
