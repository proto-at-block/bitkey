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
      BitcoinConsolidation -> Res.drawable.bitcoin_consolidation
      BitcoinBadged -> Res.drawable.bitcoin_badged
      BitkeyDevice3D -> Res.drawable.bitkey_device_3d
      BitkeyFrontLit -> Res.drawable.bitkey_front_lit
      SmallIconBitkeyReset -> Res.drawable.bitkey_reset
      BitkeyLogo -> Res.drawable.bitkey_logo
      BuyOwnBitkeyHero -> Res.drawable.buy_own_bitkey_hero
      CloudBackupMobileKey -> Res.drawable.cloud_backup_mobile_key
      InheritanceShowcase -> Res.drawable.inheritance_showcase
      LiteMoneyHomeInheritanceHero -> Res.drawable.lite_money_home_inheritance_hero
      LargeIconMinus -> Res.drawable.market_minus
      LargeIconCheckFilled -> Res.drawable.large_icon_check_filled
      LargeIconCheckStroked -> Res.drawable.large_icon_check_stroked
      LargeIconNetworkError -> Res.drawable.large_icon_network_error
      LargeIconShieldPerson -> Res.drawable.large_icon_shield_person
      LargeIconWarning -> Res.drawable.large_icon_warning
      LargeIconWarningFilled -> Res.drawable.large_icon_warning_filled
      LargeIconWarningStroked -> Res.drawable.large_icon_warning_stroked
      MediumIconTrustedContact -> Res.drawable.medium_icon_trusted_contact
      MoneyHomeHero -> Res.drawable.money_home_hero
      MoneyHomeHeroLightWithGraph -> Res.drawable.money_home_hero_light_with_graph
      MoneyHomeHeroLightNoGraph -> Res.drawable.money_home_hero_light_no_graph
      MoneyHomeHeroDarkWithGraph -> Res.drawable.money_home_hero_dark_with_graph
      MoneyHomeHeroDarkNoGraph -> Res.drawable.money_home_hero_dark_no_graph
      SecurityHubEducationTrustedContact -> Res.drawable.hero_recovery_contacts
      SecurityHubEducationMultipleFingerprints -> Res.drawable.hero_multiple_fingerprints
      SecurityHubEducationEmergencyExit -> Res.drawable.hero_eak
      SecurityHubEducationCriticalAlerts -> Res.drawable.hero_critical_alerts
      SecurityHubEducationTransactionVerification -> Res.drawable.hero_transaction_verification
      SmallIconAccount -> Res.drawable.small_icon_account
      SmallIconAnnouncement -> Res.drawable.market_loud_speaker
      SmallIconArrowDown -> Res.drawable.market_arrow_down
      SmallIconArrowLeft -> Res.drawable.small_icon_arrow_left
      SmallIconArrowRight -> Res.drawable.market_arrow_right
      SmallIconArrowUp -> Res.drawable.market_arrow_up
      SmallIconArrowUpRight -> Res.drawable.small_icon_arrow_up_right
      SmallIconBitcoinStroked -> Res.drawable.market_bitcoin
      SmallIconBitkey -> Res.drawable.market_bitkey_fill
      SmallIconBitkeySend -> Res.drawable.market_bitkey_arrow_up
      SmallIconCaretDown -> Res.drawable.small_icon_caret_down
      SmallIconCaretLeft -> Res.drawable.small_icon_caret_left
      SmallIconCaretRight -> Res.drawable.market_chevron_right
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
      SmallIconCloud -> Res.drawable.market_cloud_1
      SmallIconCloudError -> Res.drawable.small_icon_cloud_error
      SmallIconConsolidation -> Res.drawable.market_arrows_converge_vertical
      SmallIconCopy -> Res.drawable.market_copy
      SmallIconDigitOne -> Res.drawable.small_icon_digit_one
      SmallIconDigitThree -> Res.drawable.small_icon_digit_three
      SmallIconDigitTwo -> Res.drawable.small_icon_digit_two
      SmallIconDocument -> Res.drawable.market_file_download
      SmallIconElectrum -> Res.drawable.market_stack
      SmallIconEmail -> Res.drawable.market_envelope
      SmallIconFingerprint -> Res.drawable.market_fingerprint
      SmallIconInformation -> Res.drawable.market_i_circle
      SmallIconInheritance -> Res.drawable.market_donation
      SmallIconLightning -> Res.drawable.small_icon_lightning
      SmallIconLock -> Res.drawable.market_lock_on
      SmallIconMessage -> Res.drawable.market_message
      SmallIconMinusFilled -> Res.drawable.small_icon_minus_filled
      SmallIconMinusStroked -> Res.drawable.small_icon_minus_stroked
      SmallIconMobileKey -> Res.drawable.small_icon_mobile_key
      SmallIconMobileLimit -> Res.drawable.market_right_left
      SmallIconNotification -> Res.drawable.market_notification_square
      SmallIconPaintBrush -> Res.drawable.market_palette
      SmallIconPhone -> Res.drawable.market_phone
      SmallIconPlus -> Res.drawable.market_plus
      SmallIconQrCode -> Res.drawable.small_icon_qr_code
      SmallIconQuestion -> Res.drawable.market_question_mark_circle
      SmallIconQuestionNoOutline -> Res.drawable.small_icon_question_no_outline
      SmallIconRecovery -> Res.drawable.market_float
      SmallIconRefresh -> Res.drawable.market_arrow_rotate_counterclockwise
      SmallIconScan -> Res.drawable.small_icon_scan
      SmallIconSettings -> Res.drawable.market_gear
      SmallIconSettingsBadged -> Res.drawable.small_icon_settings_badged
      SmallIconShare -> Res.drawable.small_icon_share
      SmallIconShield -> Res.drawable.market_shield_empty
      SmallIconShieldFilled -> Res.drawable.market_shield_fill
      SmallIconShieldCheck -> Res.drawable.market_shield_check
      SmallIconShieldPerson -> Res.drawable.market_shield_human
      SmallIconSpeed -> Res.drawable.small_icon_speed
      SmallIconSwap -> Res.drawable.small_icon_swap
      SmallIconTicket -> Res.drawable.small_icon_ticket
      SmallIconVideo -> Res.drawable.small_icon_video
      SmallIconWallet -> Res.drawable.market_card_line
      SmallIconWalletFilled -> Res.drawable.market_card_line_fill
      SmallIconWarning -> Res.drawable.market_exclamation_circle
      SmallIconWarningFilled -> Res.drawable.small_icon_warning_filled
      SmallIconX -> Res.drawable.small_icon_x
      SmallIconXFilled -> Res.drawable.small_icon_xfilled
      DotAddressVerification -> Res.drawable.dot_address_verification
      DotAppKey -> Res.drawable.dot_app_key
      DotAppSecurity -> Res.drawable.dot_app_security
      DotBitcoin -> Res.drawable.dot_bitcoin
      DotBitkey -> Res.drawable.dot_bitkey
      DotCloud -> Res.drawable.dot_cloud
      DotCloudBackup -> Res.drawable.dot_cloud_backup
      DotCoins -> Res.drawable.dot_coins
      DotCommunication -> Res.drawable.dot_communication
      DotCriticalAlerts -> Res.drawable.dot_critical_alerts
      DotDevelopers -> Res.drawable.dot_developers
      DotEmergency -> Res.drawable.dot_emergency
      DotEmptyState -> Res.drawable.dot_empty_state
      DotFingerprint -> Res.drawable.dot_fingerprint
      DotFingerprintsMultiple -> Res.drawable.dot_fingerprints_multiple
      DotIconsSearch -> Res.drawable.dot_icons_search
      DotInheritance -> Res.drawable.dot_inheritance
      DotLab -> Res.drawable.dot_lab
      DotLoading -> Res.drawable.dot_loading
      DotMobile -> Res.drawable.dot_mobile
      DotNews -> Res.drawable.dot_news
      DotNotifyEmail -> Res.drawable.dot_notify_email
      DotNotifyPush -> Res.drawable.dot_notify_push
      DotNotifySms -> Res.drawable.dot_notify_sms
      DotPair -> Res.drawable.dot_pair
      DotPrivacy -> Res.drawable.dot_privacy
      DotRecoveryContact -> Res.drawable.dot_recovery_contact
      DotSecurity -> Res.drawable.dot_security
      DotServer -> Res.drawable.dot_server
      DotTakes -> Res.drawable.dot_takes
      DotVaults -> Res.drawable.dot_vaults
      DotVerification -> Res.drawable.dot_verification
      DotWorld -> Res.drawable.dot_world
      CalloutArrow -> Res.drawable.callout_arrow
      ThemeLight -> Res.drawable.market_brightness
      ThemeDark -> Res.drawable.market_moon
      WarningBadge -> Res.drawable.warning_badge
      Insights -> Res.drawable.insights
    }
}

private class DarkStyleDictionaryIcons(
  private val lightIcons: StyleDictionaryIcons,
) : StyleDictionaryIcons {
  override fun getDrawableResource(icon: Icon): DrawableResource =
    when (icon) {
      Bitcoin -> Res.drawable.bitcoin_dark
      BitcoinConsolidation -> Res.drawable.bitcoin_consolidation_dark
      BitcoinBadged -> Res.drawable.bitcoin_badged_dark
      InheritanceShowcase -> Res.drawable.inheritance_showcase_dark
      LiteMoneyHomeInheritanceHero -> Res.drawable.lite_money_home_inheritance_hero_dark
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
