package build.wallet.statemachine.recovery.socrec.help

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocialChallengeError
import build.wallet.recovery.socrec.SocialChallengeVerifier
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.help.model.ConfirmingIdentityFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.EnterRecoveryCodeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.SecurityNoticeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.VerifyingContactMethodFormBodyModel
import build.wallet.time.Delayer
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

class HelpingWithRecoveryUiStateMachineImpl(
  private val delayer: Delayer,
  private val socialChallengeVerifier: SocialChallengeVerifier,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
) : HelpingWithRecoveryUiStateMachine {
  @Composable
  override fun model(props: HelpingWithRecoveryUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.VerifyingContactMethod) }

    return when (val state = uiState) {
      is UiState.VerifyingContactMethod ->
        VerifyingContactMethodFormBodyModel(
          onBack = props.onExit,
          onTextMessageClick = { uiState = UiState.ViewingSecurityNotice },
          onEmailClick = { uiState = UiState.ViewingSecurityNotice },
          onPhoneCallClick = { uiState = UiState.ViewingSecurityNotice },
          onVideoChatClick = { uiState = UiState.ConfirmingIdentity },
          onInPersonClick = { uiState = UiState.ConfirmingIdentity }
        ).asModalScreen()

      UiState.ConfirmingIdentity ->
        ConfirmingIdentityFormBodyModel(
          protectedCustomer = props.protectedCustomer,
          onBack = { uiState = UiState.VerifyingContactMethod },
          onVerifiedClick = { uiState = UiState.EnteringRecoveryCode() }
        ).asModalScreen()

      UiState.ViewingSecurityNotice ->
        SecurityNoticeFormBodyModel(
          onBack = { uiState = UiState.VerifyingContactMethod }
        ).asModalScreen()

      is UiState.EnteringRecoveryCode ->
        EnterRecoveryCodeFormBodyModel(
          value = state.recoveryCode,
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = state.recoveryCode.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick =
                StandardClick {
                  uiState = UiState.VerifyingRecoveryCode(recoveryCode = state.recoveryCode)
                }
            ),
          onBack = { uiState = UiState.VerifyingContactMethod },
          onInputChange = { recoveryCode -> uiState = UiState.EnteringRecoveryCode(recoveryCode) }
        ).asModalScreen()

      is UiState.VerifyingRecoveryCode ->
        VerifyingRecoveryCodeModel(
          account = props.account,
          relationshipId = props.protectedCustomer.relationshipId,
          goToSuccess = { uiState = UiState.SuccessfullyVerified },
          goToFailure = { uiState = UiState.FailedToVerify(it) },
          recoveryCode = state.recoveryCode
        ).asModalScreen()

      is UiState.FailedToVerify ->
        ErrorFormBodyModel(
          onBack = props.onExit,
          title = when (state.error) {
            is SocialChallengeError.ChallengeCodeVersionMismatch -> "Bitkey app out of date"
            else -> "Failed to verify your recovery code"
          },
          subline = when (state.error) {
            is SocialChallengeError.ChallengeCodeVersionMismatch -> "The invite could not be accepted - please make sure both you and the Trusted Contact you invited have updated to the most recent Bitkey app version, and then try again."
            else -> "Please try to re-enter your recovery code."
          },
          primaryButton =
            ButtonDataModel(
              text = "Try again",
              onClick = { uiState = UiState.EnteringRecoveryCode() }
            ),
          secondaryButton =
            ButtonDataModel(
              text = "Cancel",
              onClick = props.onExit
            ),
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE,
          errorData =
            ErrorData(
              segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
              actionDescription = "Verifying recovery code failed",
              cause = state.error
            )
        ).asModalScreen()

      UiState.SuccessfullyVerified ->
        SuccessfulVerifiedRecoveryCodeModel(
          exit = props.onExit
        ).asModalScreen()
    }
  }

  @Composable
  private fun VerifyingRecoveryCodeModel(
    account: Account,
    relationshipId: String,
    recoveryCode: String,
    goToSuccess: () -> Unit,
    goToFailure: (Error) -> Unit,
  ): BodyModel {
    LaunchedEffect("verifying-recovery-code") {
      relationshipsKeysRepository
        .getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
        .flatMap { trustedContactIdentityKey ->
          socialChallengeVerifier.verifyChallenge(
            account = account,
            delegatedDecryptionKey = trustedContactIdentityKey,
            recoveryRelationshipId = relationshipId,
            recoveryCode = recoveryCode
          )
        }
        .onSuccess { goToSuccess() }
        .logFailure { "Failed to verify social recovery code" }
        .onFailure { goToFailure(it) }
    }

    return LoadingSuccessBodyModel(
      id = null,
      state = LoadingSuccessBodyModel.State.Loading
    )
  }

  @Composable
  private fun SuccessfulVerifiedRecoveryCodeModel(exit: () -> Unit): BodyModel {
    LaunchedEffect("verifying-recovery-code") {
      delayer.delay(3.seconds)
      exit()
    }

    return LoadingSuccessBodyModel(
      id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS,
      message = "Verified",
      state = LoadingSuccessBodyModel.State.Success
    )
  }
}

private sealed interface UiState {
  data object VerifyingContactMethod : UiState

  data object ConfirmingIdentity : UiState

  data object ViewingSecurityNotice : UiState

  data class EnteringRecoveryCode(val recoveryCode: String = "") : UiState

  data class VerifyingRecoveryCode(val recoveryCode: String) : UiState

  data object SuccessfullyVerified : UiState

  data class FailedToVerify(val error: Error) : UiState
}
