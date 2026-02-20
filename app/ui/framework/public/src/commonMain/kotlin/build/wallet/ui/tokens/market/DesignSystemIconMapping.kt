package build.wallet.ui.tokens.market

import build.wallet.statemachine.core.Icon

/**
 * Provides mappings from legacy [Icon] enum values to [MarketIcon] equivalents
 * for use when the Design System Updates feature flag is enabled.
 *
 * This allows gradual migration to the new Market icon library while maintaining
 * backward compatibility with existing icons.
 */
object DesignSystemIconMapping {
  /**
   * Returns the [MarketIcon] equivalent for the given [Icon], or null if no mapping exists.
   * When null is returned, the original icon should be used.
   */
  fun getMarketIcon(icon: Icon): MarketIcon? {
    return when (icon) {
      // Settings page - General section
      Icon.SmallIconMobileLimit -> MarketIcons.RightLeft // transfers -> right-left
      Icon.SmallIconPaintBrush -> MarketIcons.Palette // appearance -> palette
      Icon.SmallIconNotification -> MarketIcons.NotificationSquare // notifications -> notification-square
      Icon.SmallIconPhone -> MarketIcons.Phone // mobile_devices -> phone
      Icon.SmallIconInheritance -> MarketIcons.Donation // inheritance -> donation

      // Settings page - Security & Recovery section
      Icon.SmallIconShieldPerson -> MarketIcons.ShieldHuman // recovery_contacts -> shield-human
      Icon.SmallIconBitkey -> MarketIcons.BitkeyWallet // bitkey_device -> bitkey-wallet
      Icon.SmallIconFingerprint -> MarketIcons.Fingerprint // fingerprints -> fingerprint

      // Settings page - Advanced section
      Icon.SmallIconConsolidation -> MarketIcons.ArrowsConvergeVertical // utxo_consolidation -> arrows-converge-vertical
      Icon.SmallIconElectrum -> MarketIcons.Stack // custom_electrum_server -> stack
      Icon.SmallIconDocument -> MarketIcons.FileDownload // exports -> file-download

      // Settings page - Support section
      Icon.SmallIconMessage -> MarketIcons.Message // contact_us -> message
      Icon.SmallIconQuestion -> MarketIcons.QuestionMarkCircle // help_center -> question-circle-mark

      // Settings page - Other
      Icon.SmallIconArrowRight -> MarketIcons.ArrowRight // arrow_right -> arrow-right
      Icon.SmallIconCloud -> MarketIcons.Cloud1 // cloud_backups -> cloud-1
      Icon.SmallIconLock -> MarketIcons.LockOn // app_security -> lock-on

      // Emergency Exit Kit
      Icon.CloudBackupEmergencyExitKit -> MarketIcons.Float // emergency_exit_kit header -> float
      Icon.SmallIconRecovery -> MarketIcons.Float // emergency_exit_kit tile (security hub) -> float

      // Warning & Alerts
      Icon.SmallIconWarning -> MarketIcons.ExclamationCircle // warning -> exclamation-circle
      Icon.SmallIconAnnouncement -> MarketIcons.LoudSpeaker // critical_alerts (security hub) -> loudspeaker

      // Appearance mode icons
      Icon.ThemeLight -> MarketIcons.Brightness // light_mode -> brightness
      Icon.ThemeDark -> MarketIcons.Moon // dark_mode -> moon
      Icon.ThemeSystem -> MarketIcons.Phone // system_mode -> phone

      // Contact field icons
      Icon.SmallIconEmail -> MarketIcons.Envelope // email -> envelope

      // Home page icons
      Icon.SmallIconSettings -> MarketIcons.Gear // settings -> gear
      Icon.LargeIconAdd -> MarketIcons.Plus // buy -> plus
      Icon.SmallIconPlus -> MarketIcons.Plus // plus variants
      Icon.LargeIconMinus -> MarketIcons.Minus // sell -> minus
      Icon.SmallIconMinus -> MarketIcons.Minus // minus variants
      Icon.LargeIconSend -> MarketIcons.ArrowUp // send -> arrow-up
      Icon.LargeIconReceive -> MarketIcons.ArrowDown // receive -> arrow-down

      // Arrow icons
      Icon.SmallIconArrowUp -> MarketIcons.ArrowUp // arrow up
      Icon.SmallIconArrowDown -> MarketIcons.ArrowDown // arrow down

      // Info icons
      Icon.SmallIconInformation -> MarketIcons.ICircle // info -> i-circle
      Icon.SmallIconInformationFilled -> MarketIcons.ICircle // info filled -> i-circle

      // Tab bar icons
      Icon.SmallIconWallet -> MarketIcons.CardLine // wallet tab -> card-line
      Icon.SmallIconWalletFilled -> MarketIcons.CardLineFill // wallet tab filled -> card-line-fill
      Icon.SmallIconShield -> MarketIcons.ShieldEmpty // shield tab -> shield-empty
      Icon.SmallIconShieldFilled -> MarketIcons.ShieldFill // shield tab filled -> shield-fill

      // No mapping - use original icon
      else -> null
    }
  }
}
