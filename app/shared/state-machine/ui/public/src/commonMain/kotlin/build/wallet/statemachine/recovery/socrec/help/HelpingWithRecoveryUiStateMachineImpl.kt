package build.wallet.statemachine.recovery.socrec.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.recovery.socrec.SocialChallengeVerifier
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.ConfirmingIdentityFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.EnterRecoveryCodeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.SecurityNoticeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.VerifyingContactMethodFormBodyModel
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class HelpingWithRecoveryUiStateMachineImpl(
  private val socialChallengeVerifier: SocialChallengeVerifier,
  private val socRecKeysRepository: SocRecKeysRepository,
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
          onPhoneCallClick = { uiState = UiState.ConfirmingIdentity },
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
          value = state.code,
          onBack = { uiState = UiState.VerifyingContactMethod },
          onContinueClick = { uiState = UiState.VerifyingRecoveryCode(code = state.code) },
          onInputChange = { code -> uiState = UiState.EnteringRecoveryCode(code) }
        ).asModalScreen()

      is UiState.VerifyingRecoveryCode ->
        VerifyingRecoveryCodeModel(
          account = props.account,
          relationshipId = props.protectedCustomer.recoveryRelationshipId,
          goToSuccess = { uiState = UiState.SuccessfullyVerified },
          goToFailure = { uiState = UiState.FailedToVerify },
          code = state.code
        ).asModalScreen()

      UiState.FailedToVerify ->
        ErrorFormBodyModel(
          onBack = props.onExit,
          title = "Failed to verify your recovery code",
          subline = "Please try to re-enter your recovery code.",
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
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE
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
    code: String,
    goToSuccess: () -> Unit,
    goToFailure: () -> Unit,
  ): BodyModel {
    LaunchedEffect("verifying-recovery-code") {
      socRecKeysRepository
        .getKeyWithPrivateMaterialOrCreate(::TrustedContactIdentityKey)
        .flatMap { trustedContactIdentityKey ->
          socialChallengeVerifier.verifyChallenge(
            account = account,
            trustedContactIdentityKey = trustedContactIdentityKey,
            recoveryRelationshipId = relationshipId,
            code = code
          )
        }
        .onSuccess { goToSuccess() }
        .onFailure { goToFailure() }
    }

    return LoadingBodyModel(
      style = LoadingBodyModel.Style.Implicit,
      id = null
    )
  }

  @Composable
  private fun SuccessfulVerifiedRecoveryCodeModel(exit: () -> Unit): BodyModel {
    LaunchedEffect("verifying-recovery-code") {
      delay(2.seconds)
      exit()
    }

    return SuccessBodyModel(
      id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS,
      title = "Verified",
      style = SuccessBodyModel.Style.Implicit
    )
  }
}

private sealed interface UiState {
  data object VerifyingContactMethod : UiState

  data object ConfirmingIdentity : UiState

  data object ViewingSecurityNotice : UiState

  data class EnteringRecoveryCode(val code: String = "") : UiState

  data class VerifyingRecoveryCode(val code: String) : UiState

  data object SuccessfullyVerified : UiState

  data object FailedToVerify : UiState
}
