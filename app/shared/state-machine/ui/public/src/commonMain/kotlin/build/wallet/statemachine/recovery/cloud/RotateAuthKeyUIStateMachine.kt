package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId
import build.wallet.auth.AuthKeyRotationManager
import build.wallet.auth.AuthKeyRotationRequestState
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.KeyboxDao
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

interface RotateAuthKeyUIStateMachine :
  StateMachine<RotateAuthKeyUIStateMachineProps, ScreenModel>

data class RotateAuthKeyUIStateMachineProps(
  val keybox: Keybox,
  val origin: RotateAuthKeyUIOrigin,
)

/**
 * The rotation can happen from two places:
 * 1. when recovering from the cloud,
 * 2. or just going in through the settings.
 *
 * When recovering from the cloud,
 * we want to give users the option to skip this step,
 * with a dedicated button next to the one that rotates keys.
 *
 * However, when users are going through settings,
 * they expect to have a back button in the toolbar,
 * so we only show the one button to rotate keys on the bottom.
 *
 * There's also difference in copy between the two.
 *
 * IMPORTANT TODO(BKR-918): Currently there's also an inconsistency in how this screen works.
 * When recovering from the cloud, we don't have an active keybox to rotate keys in.
 * So we rotate and then return the new keybox to the cloud recovery flow (the parent).
 * This can lead to users losing access as explained in BKR-918.
 */
sealed interface RotateAuthKeyUIOrigin {
  data class CloudRestore(val onComplete: (keyboxToActive: Keybox) -> Unit) : RotateAuthKeyUIOrigin

  data class Settings(val onBack: () -> Unit) : RotateAuthKeyUIOrigin
}

class RotateAuthKeyUIStateMachineImpl(
  val keyboxDao: KeyboxDao,
  val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  val authKeyRotationManager: AuthKeyRotationManager,
) : RotateAuthKeyUIStateMachine {
  @Composable
  override fun model(props: RotateAuthKeyUIStateMachineProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.WaitingOnChoiceState)
    }

    return when (val uiState = state) {
      State.WaitingOnChoiceState -> {
        when (props.origin) {
          is RotateAuthKeyUIOrigin.CloudRestore -> DeactivateDevicesAfterRestoreChoiceScreenModel(
            onNotRightNow = { state = State.ReturningFinalKeybox(props.keybox) },
            onRemoveAllOtherDevices = { state = State.ObtainingHwProofOfPossession }
          )
          is RotateAuthKeyUIOrigin.Settings -> DeactivateDevicesFromSettingsChoiceScreenModel(
            onBack = props.origin.onBack,
            onRemoveAllOtherDevices = { state = State.ObtainingHwProofOfPossession }
          )
        }
      }

      State.ObtainingHwProofOfPossession -> waitingOnProofOfPossession(props) { state = it }
      is State.RotatingAuthKeys -> {
        when (val rotationState = rotateAuthKeys(props, uiState)) {
          // TODO(BRK-887): Handle failure screen correctly
          is AuthKeyRotationRequestState.FailedRotation -> FailureScreen(
            origin = props.origin,
            onSelected = {
              // TODO(BKR-818): This can lead to users losing access when recovering from the cloud.
              rotationState.clearAttempt()
              state = State.ReturningFinalKeybox(props.keybox)
            }
          )
          is AuthKeyRotationRequestState.FinishedRotation -> ConfirmationScreen(
            origin = props.origin,
            onSelected = {
              // TODO(BKR-818): This can lead to users losing access when recovering from the cloud.
              rotationState.clearAttempt()
              state = State.ReturningFinalKeybox(rotationState.rotatedKeybox)
            }
          )
          AuthKeyRotationRequestState.Rotating -> LoadingScreen(
            message = "Removing all devices",
            id = when (props.origin) {
              is RotateAuthKeyUIOrigin.CloudRestore -> AuthEventTrackerScreenId.ROTATING_AUTH_AFTER_CLOUD_RESTORE
              is RotateAuthKeyUIOrigin.Settings -> AuthEventTrackerScreenId.ROTATING_AUTH_FROM_SETTINGS
            }
          )
        }
      }
      is State.ReturningFinalKeybox -> {
        LaunchedEffect("rotating-keybox") {
          when (val origin = props.origin) {
            is RotateAuthKeyUIOrigin.CloudRestore -> origin.onComplete(uiState.keybox)
            is RotateAuthKeyUIOrigin.Settings -> origin.onBack()
          }
        }

        LoadingScreen(
          message = "",
          id = when (props.origin) {
            is RotateAuthKeyUIOrigin.CloudRestore -> AuthEventTrackerScreenId.SETTING_ACTIVE_KEYBOX_AFTER_CLOUD_RESTORE
            is RotateAuthKeyUIOrigin.Settings -> AuthEventTrackerScreenId.SETTING_ACTIVE_KEYBOX_FROM_SETTINGS
          }
        )
      }
    }
  }

  @Composable
  private fun rotateAuthKeys(
    props: RotateAuthKeyUIStateMachineProps,
    uiState: State.RotatingAuthKeys,
  ): AuthKeyRotationRequestState {
    /**
     * We could just do `props.origin is Settings`,
     * but since this is important, we should make sure if a new origin is added,
     * we don't forget to think about whether we should rotate or not.
     */
    val shouldRotateActiveKeybox = when (props.origin) {
      // When restoring, we don't have an active keybox to rotate keys in.
      is RotateAuthKeyUIOrigin.CloudRestore -> false
      // And we don't want to add more logic to settings that'd run the key rotating.
      is RotateAuthKeyUIOrigin.Settings -> true
    }
    return authKeyRotationManager.startOrResumeAuthKeyRotation(
      hwFactorProofOfPossession = uiState.hwFactorProofOfPossession,
      keyboxToRotate = props.keybox,
      rotateActiveKeybox = shouldRotateActiveKeybox,
      hwAuthPublicKey = uiState.hwAuthPublicKey,
      hwSignedAccountId = uiState.signedAccountId
    )
  }

  @Composable
  private fun waitingOnProofOfPossession(
    props: RotateAuthKeyUIStateMachineProps,
    setState: (State) -> Unit,
  ) = proofOfPossessionNfcStateMachine.model(
    props = ProofOfPossessionNfcProps(
      request = Request.HwKeyProofAndAccountSignature(
        accountId = props.keybox.fullAccountId,
        onSuccess = { signedAccountId, hwAuthPublicKey, hwFactorProofOfPossession ->
          setState(
            State.RotatingAuthKeys(
              hwAuthPublicKey = hwAuthPublicKey,
              hwFactorProofOfPossession = hwFactorProofOfPossession,
              signedAccountId = signedAccountId
            )
          )
        }
      ),
      fullAccountId = props.keybox.fullAccountId,
      keyboxConfig = props.keybox.config,
      screenPresentationStyle = ScreenPresentationStyle.FullScreen,
      appAuthKey = props.keybox.activeKeyBundle.authKey,
      onBack = { setState(State.WaitingOnChoiceState) }
    )
  )

  private sealed interface State {
    // We're waiting on the customer to choose an option.
    data object WaitingOnChoiceState : State

    // We're returning the final keybox to our parent, who will set it as active
    data class ReturningFinalKeybox(
      val keybox: Keybox,
    ) : State

    // We've passed control to the proof of possession state machine and are awaiting it's completion
    data object ObtainingHwProofOfPossession : State

    // We're performing the work to rotate the auth keys for the customer
    data class RotatingAuthKeys(
      val hwAuthPublicKey: HwAuthPublicKey,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val signedAccountId: String,
    ) : State
  }
}

private fun ConfirmationScreen(
  origin: RotateAuthKeyUIOrigin,
  onSelected: () -> Unit,
) = FormBodyModel(
  id = when (origin) {
    is RotateAuthKeyUIOrigin.CloudRestore -> AuthEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH_AFTER_CLOUD_RESTORE
    is RotateAuthKeyUIOrigin.Settings -> AuthEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH_FROM_SETTINGS
  },
  onBack = null,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Removed all devices",
      subline = "We’ve successfully removed all other devices that were using your Bitkey wallet."
    ),
  primaryButton =
    ButtonModel(
      "Done",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick(onSelected)
    )
).asRootScreen()

private fun FailureScreen(
  origin: RotateAuthKeyUIOrigin,
  onSelected: () -> Unit,
) = FormBodyModel(
  id = when (origin) {
    is RotateAuthKeyUIOrigin.CloudRestore -> AuthEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_AFTER_CLOUD_BACKUP
    is RotateAuthKeyUIOrigin.Settings -> AuthEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_FROM_SETTINGS
  },
  onBack = null,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Something went wrong",
      subline = "We weren't able to remove all devices associated with your Bitkey wallet. " +
        "Please try again or you can cancel and remove devices at a later time within your Bitkey application settings."
    ),
  primaryButton =
    ButtonModel(
      "Try again",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick(onSelected)
    ),
  secondaryButton =
    ButtonModel(
      "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick(onSelected)
    )
).asRootScreen()

private fun LoadingScreen(
  message: String,
  id: AuthEventTrackerScreenId,
) = LoadingBodyModel(
  message = message,
  id = id
).asRootScreen()

private fun DeactivateDevicesAfterRestoreChoiceScreenModel(
  onNotRightNow: () -> Unit,
  onRemoveAllOtherDevices: () -> Unit,
) = FormBodyModel(
  id = AuthEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH_AFTER_CLOUD_RESTORE,
  onBack = null,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      headline = "Remove all other devices",
      subline = "If you've restored a wallet, you might still be signed into Bitkey on another device. " +
        "You can remove Bitkey from other devices now, or choose to do this later from settings."
    ),
  secondaryButton =
    ButtonModel(
      "Remove all other devices",
      treatment = ButtonModel.Treatment.Black,
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick(onRemoveAllOtherDevices)
    ),
  primaryButton =
    ButtonModel(
      "Not right now",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick(onNotRightNow)
    )
).asRootScreen()

private fun DeactivateDevicesFromSettingsChoiceScreenModel(
  onBack: () -> Unit,
  onRemoveAllOtherDevices: () -> Unit,
) = FormBodyModel(
  id = AuthEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH_FROM_SETTINGS,
  onBack = null,
  toolbar = ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = onBack)
  ),
  header =
    FormHeaderModel(
      headline = "Remove all other devices",
      subline = "If you’ve restored a wallet, your Bitkey might still be connected to another mobile device. " +
        "You can remove Bitkey from other mobile devices while remaining to use this one."
    ),
  primaryButton = ButtonModel(
    "Remove all other devices",
    treatment = ButtonModel.Treatment.Black,
    size = ButtonModel.Size.Footer,
    onClick = Click.standardClick(onRemoveAllOtherDevices)
  )
).asRootScreen()
