package build.wallet.di

import build.wallet.nfc.NfcTransactor
import build.wallet.statemachine.inheritance.InheritanceManagementUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachine
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachine
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachine
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

/**
 * Component bound to [ActivityScope] to be used by the JVM test code.
 * Unlike Android, on JVM this component will technically have the same
 * scoping as the [JvmAppComponent].
 *
 * Can be created using [JvmActivityComponent.Factory].
 */
@SingleIn(ActivityScope::class)
@ContributesSubcomponent(ActivityScope::class)
interface JvmActivityComponent {
  /**
   * Dependencies that are used by JVM test code.
   */
  val addingTcsUiStateMachine: AddingTrustedContactUiStateMachine
  val appUiStateMachine: AppUiStateMachine
  val feedbackUiStateMachine: FeedbackUiStateMachine
  val inheritanceManagementUiStateMachine: InheritanceManagementUiStateMachine
  val lostHardwareRecoveryUiStateMachine: LostHardwareRecoveryUiStateMachine
  val nfcTransactor: NfcTransactor
  val notificationsStateMachine: EnableNotificationsUiStateMachine
  val partnershipsPurchaseUiStateMachine: PartnershipsPurchaseUiStateMachine
  val partnershipsTransferUiStateMachine: PartnershipsTransferUiStateMachine
  val recoveringKeyboxUiStateMachine: LostAppRecoveryUiStateMachine
  val recoveryChallengeUiStateMachine: RecoveryChallengeUiStateMachine
  val rotateAuthUIStateMachine: RotateAuthKeyUIStateMachine
  val trustedContactManagementUiStateMachine: TrustedContactManagementUiStateMachine

  /**
   * Factory for creating [JvmActivityComponent] instance:
   *
   * ```kotlin
   * val activityComponent = appComponent.activityComponent()
   * ```
   */
  @ContributesSubcomponent.Factory(AppScope::class)
  interface Factory {
    fun activityComponent(): JvmActivityComponent
  }
}
