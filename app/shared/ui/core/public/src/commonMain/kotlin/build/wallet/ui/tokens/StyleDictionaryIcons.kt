// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import bitkey.shared.ui_core_public.generated.resources.*
import bitkey.shared.ui_core_public.generated.resources.Res
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.BitcoinOrange
import build.wallet.statemachine.core.Icon.BitkeyDevice3D
import build.wallet.statemachine.core.Icon.BitkeyDeviceRaised
import build.wallet.statemachine.core.Icon.BitkeyDeviceRaisedSmall
import build.wallet.statemachine.core.Icon.BitkeyLogo
import build.wallet.statemachine.core.Icon.BuyOwnBitkeyHero
import build.wallet.statemachine.core.Icon.CalloutArrow
import build.wallet.statemachine.core.Icon.CloudBackupEmergencyAccessKit
import build.wallet.statemachine.core.Icon.CloudBackupMobileKey
import build.wallet.statemachine.core.Icon.LargeIconAdd
import build.wallet.statemachine.core.Icon.LargeIconCheckFilled
import build.wallet.statemachine.core.Icon.LargeIconCheckStroked
import build.wallet.statemachine.core.Icon.LargeIconConsolidationFilled
import build.wallet.statemachine.core.Icon.LargeIconEllipsisFilled
import build.wallet.statemachine.core.Icon.LargeIconMinus
import build.wallet.statemachine.core.Icon.LargeIconNetworkError
import build.wallet.statemachine.core.Icon.LargeIconReceive
import build.wallet.statemachine.core.Icon.LargeIconSend
import build.wallet.statemachine.core.Icon.LargeIconShieldPerson
import build.wallet.statemachine.core.Icon.LargeIconSpeedometer
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.Icon.LargeIconWarningStroked
import build.wallet.statemachine.core.Icon.MediumIconQrCode
import build.wallet.statemachine.core.Icon.MediumIconTrustedContact
import build.wallet.statemachine.core.Icon.MoneyHomeHero
import build.wallet.statemachine.core.Icon.SmallIconAccount
import build.wallet.statemachine.core.Icon.SmallIconAnnouncement
import build.wallet.statemachine.core.Icon.SmallIconArrowDown
import build.wallet.statemachine.core.Icon.SmallIconArrowLeft
import build.wallet.statemachine.core.Icon.SmallIconArrowRight
import build.wallet.statemachine.core.Icon.SmallIconArrowUp
import build.wallet.statemachine.core.Icon.SmallIconArrowUpRight
import build.wallet.statemachine.core.Icon.SmallIconBitcoinStroked
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.Icon.SmallIconBitkeyReceive
import build.wallet.statemachine.core.Icon.SmallIconBitkeySend
import build.wallet.statemachine.core.Icon.SmallIconCaretDown
import build.wallet.statemachine.core.Icon.SmallIconCaretLeft
import build.wallet.statemachine.core.Icon.SmallIconCaretRight
import build.wallet.statemachine.core.Icon.SmallIconCaretUp
import build.wallet.statemachine.core.Icon.SmallIconCheck
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconCheckStroked
import build.wallet.statemachine.core.Icon.SmallIconCheckbox
import build.wallet.statemachine.core.Icon.SmallIconCheckboxSelected
import build.wallet.statemachine.core.Icon.SmallIconCircleStroked
import build.wallet.statemachine.core.Icon.SmallIconClipboard
import build.wallet.statemachine.core.Icon.SmallIconClock
import build.wallet.statemachine.core.Icon.SmallIconCloud
import build.wallet.statemachine.core.Icon.SmallIconCloudError
import build.wallet.statemachine.core.Icon.SmallIconConsolidation
import build.wallet.statemachine.core.Icon.SmallIconCopy
import build.wallet.statemachine.core.Icon.SmallIconCurrency
import build.wallet.statemachine.core.Icon.SmallIconDigitOne
import build.wallet.statemachine.core.Icon.SmallIconDigitThree
import build.wallet.statemachine.core.Icon.SmallIconDigitTwo
import build.wallet.statemachine.core.Icon.SmallIconDocument
import build.wallet.statemachine.core.Icon.SmallIconDownload
import build.wallet.statemachine.core.Icon.SmallIconElectrum
import build.wallet.statemachine.core.Icon.SmallIconEmail
import build.wallet.statemachine.core.Icon.SmallIconExternal
import build.wallet.statemachine.core.Icon.SmallIconFingerprint
import build.wallet.statemachine.core.Icon.SmallIconInformation
import build.wallet.statemachine.core.Icon.SmallIconInformationFilled
import build.wallet.statemachine.core.Icon.SmallIconInheritance
import build.wallet.statemachine.core.Icon.SmallIconKey
import build.wallet.statemachine.core.Icon.SmallIconKeyboard
import build.wallet.statemachine.core.Icon.SmallIconLightning
import build.wallet.statemachine.core.Icon.SmallIconLock
import build.wallet.statemachine.core.Icon.SmallIconMessage
import build.wallet.statemachine.core.Icon.SmallIconMinus
import build.wallet.statemachine.core.Icon.SmallIconMinusFilled
import build.wallet.statemachine.core.Icon.SmallIconMinusStroked
import build.wallet.statemachine.core.Icon.SmallIconMobileKey
import build.wallet.statemachine.core.Icon.SmallIconMobileLimit
import build.wallet.statemachine.core.Icon.SmallIconNotification
import build.wallet.statemachine.core.Icon.SmallIconPaintBrush
import build.wallet.statemachine.core.Icon.SmallIconPause
import build.wallet.statemachine.core.Icon.SmallIconPauseFilled
import build.wallet.statemachine.core.Icon.SmallIconPauseStroked
import build.wallet.statemachine.core.Icon.SmallIconPhone
import build.wallet.statemachine.core.Icon.SmallIconPlus
import build.wallet.statemachine.core.Icon.SmallIconPlusFilled
import build.wallet.statemachine.core.Icon.SmallIconPlusStroked
import build.wallet.statemachine.core.Icon.SmallIconPushNotification
import build.wallet.statemachine.core.Icon.SmallIconQrCode
import build.wallet.statemachine.core.Icon.SmallIconQuestion
import build.wallet.statemachine.core.Icon.SmallIconQuestionNoOutline
import build.wallet.statemachine.core.Icon.SmallIconRecovery
import build.wallet.statemachine.core.Icon.SmallIconRefresh
import build.wallet.statemachine.core.Icon.SmallIconScan
import build.wallet.statemachine.core.Icon.SmallIconSettings
import build.wallet.statemachine.core.Icon.SmallIconSettingsBadged
import build.wallet.statemachine.core.Icon.SmallIconShare
import build.wallet.statemachine.core.Icon.SmallIconShield
import build.wallet.statemachine.core.Icon.SmallIconShieldCheck
import build.wallet.statemachine.core.Icon.SmallIconShieldPerson
import build.wallet.statemachine.core.Icon.SmallIconSpeed
import build.wallet.statemachine.core.Icon.SmallIconStar
import build.wallet.statemachine.core.Icon.SmallIconSwap
import build.wallet.statemachine.core.Icon.SmallIconSync
import build.wallet.statemachine.core.Icon.SmallIconTicket
import build.wallet.statemachine.core.Icon.SmallIconVideo
import build.wallet.statemachine.core.Icon.SmallIconWallet
import build.wallet.statemachine.core.Icon.SmallIconWarning
import build.wallet.statemachine.core.Icon.SmallIconWarningFilled
import build.wallet.statemachine.core.Icon.SmallIconX
import build.wallet.statemachine.core.Icon.SmallIconXFilled
import build.wallet.statemachine.core.Icon.SmallIconXStroked
import build.wallet.statemachine.core.Icon.SubtractLeft
import build.wallet.statemachine.core.Icon.SubtractRight
import build.wallet.statemachine.core.Icon.TabIconHome
import build.wallet.statemachine.core.Icon.TabIconProfile
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun Icon.painter() = painterResource(drawableRes)

private val Icon.drawableRes: DrawableResource
  get() =
    when (this) {
      Bitcoin -> Res.drawable.bitcoin
      BitcoinOrange -> Res.drawable.bitcoin_orange
      BitkeyDevice3D -> Res.drawable.bitkey_device_3d
      BitkeyDeviceRaised -> Res.drawable.bitkey_device_raised
      BitkeyDeviceRaisedSmall -> Res.drawable.bitkey_device_raised_small
      BitkeyLogo -> Res.drawable.bitkey_logo
      BuyOwnBitkeyHero -> Res.drawable.buy_own_bitkey_hero
      CloudBackupEmergencyAccessKit -> Res.drawable.cloud_backup_emergency_access_kit
      CloudBackupMobileKey -> Res.drawable.cloud_backup_mobile_key
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
      LargeIconWarningFilled -> Res.drawable.large_icon_warning_filled
      LargeIconWarningStroked -> Res.drawable.large_icon_warning_stroked
      MediumIconQrCode -> Res.drawable.medium_icon_qr_code
      MediumIconTrustedContact -> Res.drawable.medium_icon_trusted_contact
      MoneyHomeHero -> Res.drawable.money_home_hero
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
      SmallIconCheckbox -> Res.drawable.small_icon_checkbox
      SmallIconCheckboxSelected -> Res.drawable.small_icon_checkbox_selected
      SmallIconCheckFilled -> Res.drawable.small_icon_check_filled
      SmallIconCheckStroked -> Res.drawable.small_icon_check_stroked
      SmallIconCircleStroked -> Res.drawable.small_icon_circle_stroked
      SmallIconClipboard -> Res.drawable.small_icon_clipboard
      SmallIconClock -> Res.drawable.small_icon_clock
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
      SmallIconShieldCheck -> Res.drawable.small_icon_shield_check
      SmallIconShieldPerson -> Res.drawable.small_icon_shield_person
      SmallIconSpeed -> Res.drawable.small_icon_speed
      SmallIconStar -> Res.drawable.small_icon_star
      SmallIconSwap -> Res.drawable.small_icon_swap
      SmallIconSync -> Res.drawable.small_icon_sync
      SmallIconTicket -> Res.drawable.small_icon_ticket
      SmallIconVideo -> Res.drawable.small_icon_video
      SmallIconWallet -> Res.drawable.small_icon_wallet
      SmallIconWarning -> Res.drawable.small_icon_warning
      SmallIconWarningFilled -> Res.drawable.small_icon_warning_filled
      SmallIconX -> Res.drawable.small_icon_x
      SmallIconXFilled -> Res.drawable.small_icon_xfilled
      SmallIconXStroked -> Res.drawable.small_icon_xstroked
      SubtractLeft -> Res.drawable.subtract_left
      SubtractRight -> Res.drawable.subtract_right
      CalloutArrow -> Res.drawable.callout_arrow
      TabIconHome -> Res.drawable.tab_icon_home
      TabIconProfile -> Res.drawable.tab_icon_profile
    }
