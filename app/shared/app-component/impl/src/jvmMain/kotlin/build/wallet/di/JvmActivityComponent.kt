package build.wallet.di

import build.wallet.availability.AgeRangeVerificationServiceImpl
import build.wallet.f8e.inheritance.ShortenInheritanceClaimF8eClient
import build.wallet.inheritance.InheritanceClaimsRepository
import build.wallet.nfc.NfcTransactor
import build.wallet.statemachine.inheritance.InheritanceManagementUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementPresenter
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
  val nfcTransactor: NfcTransactor
  val partnershipsPurchaseAmountUiStateMachine: PartnershipsPurchaseAmountUiStateMachine
  val partnershipsPurchaseQuotesUiStateMachine: PartnershipsPurchaseQuotesUiStateMachine
  val recoveryChallengeUiStateMachine: RecoveryChallengeUiStateMachine
  val rotateAuthUIStateMachine: RotateAuthKeyUIStateMachine
  val trustedContactManagementScreenPresenter: TrustedContactManagementPresenter
  val claimsRepository: InheritanceClaimsRepository
  val ageRangeVerificationServiceImpl: AgeRangeVerificationServiceImpl
  val shortenClaimF8eClient: ShortenInheritanceClaimF8eClient

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
