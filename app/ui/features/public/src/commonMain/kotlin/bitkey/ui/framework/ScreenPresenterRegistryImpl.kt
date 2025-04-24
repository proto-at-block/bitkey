package bitkey.ui.framework

import bitkey.ui.screens.demo.*
import bitkey.ui.screens.onboarding.AccountAccessOptionsScreen
import bitkey.ui.screens.onboarding.AccountAccessOptionsScreenPresenter
import bitkey.ui.screens.onboarding.WelcomeScreen
import bitkey.ui.screens.onboarding.WelcomeScreenPresenter
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreen
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreenPresenter
import bitkey.ui.screens.securityhub.SecurityHubPresenter
import bitkey.ui.screens.securityhub.SecurityHubScreen
import bitkey.ui.screens.trustedcontact.ReinviteTrustedContactScreen
import bitkey.ui.screens.trustedcontact.ReinviteTrustedContactScreenPresenter
import bitkey.ui.screens.trustedcontact.RemoveTrustedContactScreen
import bitkey.ui.screens.trustedcontact.RemoveTrustedContactScreenPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.biometric.BiometricSettingScreen
import build.wallet.statemachine.biometric.BiometricSettingScreenPresenter
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreen
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreenPresenter
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.statemachine.dev.DebugMenuScreenPresenter
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugScreen
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugScreenPresenter
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.fwup.FwupScreenPresenter
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementPresenter
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreenPresenter

/**
 * Registry of [ScreenPresenter]'s for the app.
 *
 * TODO: use DI generation
 */
@BitkeyInject(ActivityScope::class)
class ScreenPresenterRegistryImpl(
  private val welcomeScreenPresenter: WelcomeScreenPresenter,
  private val accountAccessOptionsScreenPresenter: AccountAccessOptionsScreenPresenter,
  private val demoModeEnabledScreenPresenter: DemoModeEnabledScreenPresenter,
  private val demoModeCodeEntryScreenPresenter: DemoModeCodeEntryScreenPresenter,
  private val demoModeDisabledScreenPresenter: DemoModeDisabledScreenPresenter,
  private val demoCodeEntrySubmissionScreenPresenter: DemoCodeEntrySubmissionScreenPresenter,
  private val bitcoinWalletDebugScreenPresenter: BitcoinWalletDebugScreenPresenter,
  private val debugMenuScreenPresenter: DebugMenuScreenPresenter,
  private val securityHubPresenter: SecurityHubPresenter,
  private val managingFingerprintsScreenPresenter: ManagingFingerprintsScreenPresenter,
  private val trustedContactManagementScreenPresenter: TrustedContactManagementPresenter,
  private val cloudBackupHealthDashboardScreenPresenter: CloudBackupHealthDashboardScreenPresenter,
  private val fwupScreenPresenter: FwupScreenPresenter,
  private val recoveryChannelSettingsScreenPresenter: RecoveryChannelSettingsScreenPresenter,
  private val biometricSettingScreenPresenter: BiometricSettingScreenPresenter,
  private val reinviteTrustedContactScreenPresenter: ReinviteTrustedContactScreenPresenter,
  private val removeTrustedContactScreenPresenter: RemoveTrustedContactScreenPresenter,
) : ScreenPresenterRegistry {
  override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
    @Suppress("UNCHECKED_CAST")
    return when (screen) {
      is WelcomeScreen -> welcomeScreenPresenter
      is AccountAccessOptionsScreen -> accountAccessOptionsScreenPresenter
      is DemoModeEnabledScreen -> demoModeEnabledScreenPresenter
      is DemoModeCodeEntryScreen -> demoModeCodeEntryScreenPresenter
      is DemoModeDisabledScreen -> demoModeDisabledScreenPresenter
      is DemoCodeEntrySubmissionScreen -> demoCodeEntrySubmissionScreenPresenter
      is BitcoinWalletDebugScreen -> bitcoinWalletDebugScreenPresenter
      is DebugMenuScreen -> debugMenuScreenPresenter
      is SecurityHubScreen -> securityHubPresenter
      is ManagingFingerprintsScreen -> managingFingerprintsScreenPresenter
      is TrustedContactManagementScreen -> trustedContactManagementScreenPresenter
      is CloudBackupHealthDashboardScreen -> cloudBackupHealthDashboardScreenPresenter
      is FwupScreen -> fwupScreenPresenter
      is RecoveryChannelSettingsScreen -> recoveryChannelSettingsScreenPresenter
      is BiometricSettingScreen -> biometricSettingScreenPresenter
      is ReinviteTrustedContactScreen -> reinviteTrustedContactScreenPresenter
      is RemoveTrustedContactScreen -> removeTrustedContactScreenPresenter
      else -> error("Did not find presenter for $screen")
    } as ScreenPresenter<ScreenT>
  }
}
