package build.wallet.statemachine.recovery.sweep

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.sweep.SweepContext

internal data class SweepScreenIds(
  val generatingPsbts: EventTrackerScreenId,
  val generatePsbtsError: EventTrackerScreenId,
  val zeroBalance: EventTrackerScreenId,
  val signPsbtsPrompt: EventTrackerScreenId,
  val multipleTransactionsWarning: EventTrackerScreenId,
  val broadcasting: EventTrackerScreenId,
  val success: EventTrackerScreenId,
  val failed: EventTrackerScreenId,
  val hardwareVerificationRequired: EventTrackerScreenId,
  val help: EventTrackerScreenId,
)

internal val SweepContext.screenIds: SweepScreenIds
  get() = when (this) {
    is SweepContext.Recovery -> when (recoveredFactor) {
      App -> appRecoveryScreenIds
      Hardware -> hardwareRecoveryScreenIds
    }
    SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> migrationScreenIds
    SweepContext.InactiveWallet, is SweepContext.InactiveHardware -> inactiveWalletScreenIds
  }

private val inactiveWalletScreenIds = SweepScreenIds(
  generatingPsbts = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS,
  generatePsbtsError = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATE_PSBTS_ERROR,
  zeroBalance = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_ZERO_BALANCE,
  signPsbtsPrompt = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT,
  multipleTransactionsWarning = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,
  broadcasting = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_BROADCASTING,
  success = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SUCCESS,
  failed = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_FAILED,
  hardwareVerificationRequired = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_HARDWARE_VERIFICATION_REQUIRED,
  help = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP
)

private val appRecoveryScreenIds = SweepScreenIds(
  generatingPsbts = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS,
  generatePsbtsError = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR,
  zeroBalance = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE,
  signPsbtsPrompt = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT,
  multipleTransactionsWarning = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,
  broadcasting = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING,
  success = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS,
  failed = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED,
  hardwareVerificationRequired = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_HARDWARE_VERIFICATION_REQUIRED,
  help = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP
)

private val hardwareRecoveryScreenIds = SweepScreenIds(
  generatingPsbts = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS,
  generatePsbtsError = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR,
  zeroBalance = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE,
  signPsbtsPrompt = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT,
  multipleTransactionsWarning = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,
  broadcasting = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING,
  success = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS,
  failed = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_FAILED,
  hardwareVerificationRequired = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_HARDWARE_VERIFICATION_REQUIRED,
  help = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP
)

private val migrationScreenIds = SweepScreenIds(
  generatingPsbts = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS,
  generatePsbtsError = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS_ERROR,
  zeroBalance = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_ZERO_BALANCE,
  signPsbtsPrompt = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SIGN_PSBTS_PROMPT,
  multipleTransactionsWarning = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,
  broadcasting = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING,
  success = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS,
  failed = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_FAILED,
  hardwareVerificationRequired = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_HARDWARE_VERIFICATION_REQUIRED,
  help = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP
)
