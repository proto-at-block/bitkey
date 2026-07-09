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
      BitkeyFrontLit -> Res.drawable.bitkey_front_lit
      BitkeyReset -> Res.drawable.bitkey_reset
      BitkeyLogo -> Res.drawable.bitkey_logo
      BuyOwnBitkeyHero -> Res.drawable.buy_own_bitkey_hero
      InheritanceShowcase -> Res.drawable.inheritance_showcase
      LiteMoneyHomeInheritanceHero -> Res.drawable.lite_money_home_inheritance_hero
      Minus -> Res.drawable.market_minus
      LargeIconCheckFilled -> Res.drawable.large_icon_check_filled
      LargeIconCheckStroked -> Res.drawable.large_icon_check_stroked
      NetworkError -> Res.drawable.large_icon_network_error
      LargeIconWarningFilled -> Res.drawable.large_icon_warning_filled
      MoneyHomeHeroLightWithGraph -> Res.drawable.money_home_hero_light_with_graph
      MoneyHomeHeroLightNoGraph -> Res.drawable.money_home_hero_light_no_graph
      MoneyHomeHeroDarkWithGraph -> Res.drawable.money_home_hero_dark_with_graph
      MoneyHomeHeroDarkNoGraph -> Res.drawable.money_home_hero_dark_no_graph
      Account -> Res.drawable.small_icon_account
      ArrowDown -> Res.drawable.market_arrow_down
      ArrowLeft -> Res.drawable.small_icon_arrow_left
      ArrowRight -> Res.drawable.market_arrow_right
      ArrowUp -> Res.drawable.market_arrow_up
      ArrowUpRight -> Res.drawable.small_icon_arrow_up_right
      BitcoinStroked -> Res.drawable.market_bitcoin
      Bitkey -> Res.drawable.market_bitkey_fill
      BitkeySend -> Res.drawable.market_bitkey_arrow_up
      CaretDown -> Res.drawable.market_chevron_down
      CaretRight -> Res.drawable.market_chevron_right
      Check -> Res.drawable.small_icon_check
      CheckInheritance -> Res.drawable.small_icon_check_inheritance
      SmallIconCheckFilled -> Res.drawable.small_icon_check_filled
      SmallIconCheckStroked -> Res.drawable.small_icon_check_stroked
      CircleStroked -> Res.drawable.small_icon_circle_stroked
      Clipboard -> Res.drawable.small_icon_clipboard
      Clock -> Res.drawable.small_icon_clock
      ClockHands -> Res.drawable.small_icon_clock_hands
      Cloud -> Res.drawable.market_cloud_1
      CloudError -> Res.drawable.market_cloud_1_slash
      Consolidation -> Res.drawable.market_arrows_converge_vertical
      Copy -> Res.drawable.market_copy
      DigitOne -> Res.drawable.small_icon_digit_one
      DigitThree -> Res.drawable.small_icon_digit_three
      DigitTwo -> Res.drawable.small_icon_digit_two
      Document -> Res.drawable.market_file_download
      Electrum -> Res.drawable.market_stack
      Email -> Res.drawable.market_envelope
      Fingerprint -> Res.drawable.market_fingerprint
      Information -> Res.drawable.market_i_circle
      Inheritance -> Res.drawable.market_donation
      Lightning -> Res.drawable.small_icon_lightning
      Lock -> Res.drawable.market_lock_on
      Message -> Res.drawable.market_message
      MinusFilled -> Res.drawable.small_icon_minus_filled
      MinusStroked -> Res.drawable.small_icon_minus_stroked
      MobileLimit -> Res.drawable.market_right_left
      Notification -> Res.drawable.market_notification_square
      PaintBrush -> Res.drawable.market_palette
      Phone -> Res.drawable.market_phone
      Plus -> Res.drawable.market_plus
      QrCode -> Res.drawable.small_icon_qr_code
      Question -> Res.drawable.market_question_mark_circle
      Recovery -> Res.drawable.market_float
      Refresh -> Res.drawable.market_arrow_rotate_counterclockwise
      ScanQrCode -> Res.drawable.market_scan_qr_code
      Share -> Res.drawable.small_icon_share
      Shield -> Res.drawable.market_shield_empty
      ShieldFilled -> Res.drawable.market_shield_fill
      ShieldCheck -> Res.drawable.market_shield_check
      ShieldPerson -> Res.drawable.market_shield_human
      Ticket -> Res.drawable.small_icon_ticket
      Video -> Res.drawable.small_icon_video
      Wallet -> Res.drawable.market_card_line
      WalletFilled -> Res.drawable.market_card_line_fill
      SmallIconWarning -> Res.drawable.market_exclamation_circle
      SmallIconWarningFilled -> Res.drawable.small_icon_warning_filled
      X -> Res.drawable.small_icon_x
      XFilled -> Res.drawable.small_icon_xfilled
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
      ThemeLight -> Res.drawable.market_brightness
      ThemeDark -> Res.drawable.market_moon
      WarningBadge -> Res.drawable.warning_badge
      Backspace -> Res.drawable.market_backspace
      BitkeyWallet -> Res.drawable.market_bitkey_wallet
      Checkmark -> Res.drawable.market_checkmark
      CheckmarkCircleFill -> Res.drawable.market_checkmark_circle_fill
      CriticalBadgeAlert -> Res.drawable.market_critical_badge_alert
      DualRotatingArrows -> Res.drawable.market_dual_rotating_arrows
      EllipsisHorizontal -> Res.drawable.market_ellipsis_horizontal
      FileUpload -> Res.drawable.market_file_upload
      XCircleFill -> Res.drawable.market_x_circle_fill
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
      NetworkError -> Res.drawable.large_icon_network_error_dark

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
