package build.wallet.statemachine.core.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_EMAIL_RESEND
import build.wallet.analytics.v1.Action.ACTION_APP_EMAIL_RESEND_SKIP_FOR_NOW
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_RESEND
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_RESEND_SKIP_FOR_NOW
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Hidden
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Showing
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.ResendCodeState
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.ResendCodeState.AvailableState
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.ResendCodeState.BlockedState
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.ResendCodeState.ErrorState
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.ResendCodeState.LoadingState
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachineImpl.State.SkipBottomSheetState
import build.wallet.time.DurationFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class VerificationCodeInputStateMachineImpl(
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val eventTracker: EventTracker,
) : VerificationCodeInputStateMachine {
  @Composable
  override fun model(props: VerificationCodeInputProps): ScreenModel {
    var state: State by remember {
      // Start off blocking the customer from resending for 30 seconds
      val resendCodeAvailableTime = clock.now() + 30.seconds
      mutableStateOf(
        State(
          resendCodeState =
            BlockedState(
              resendCodeAvailableTime = resendCodeAvailableTime,
              content = blockedResendCodeContent(clock, durationFormatter, resendCodeAvailableTime)
            ),
          skipBottomSheetState = SkipBottomSheetState.Hidden
        )
      )
    }

    var enteredCode by remember { mutableStateOf("") }
    var hasResentCodeOnce by remember { mutableStateOf(false) }

    // Update the resend state every 1 second
    LaunchedEffect("update-resend-code-state") {
      while (isActive) {
        state =
          state.copy(
            resendCodeState =
              state.resendCodeState.calculateState(
                clock = clock,
                durationFormatter = durationFormatter,
                onSendCodeAgain = {
                  hasResentCodeOnce = true
                  resendCode(
                    clock, durationFormatter, eventTracker, props,
                    setState = { produceState ->
                      state = produceState(state)
                    }
                  )
                }
              )
          )
        delay(1.seconds)
      }
    }

    // Build and return the model
    return VerificationCodeInputBodyModel(
      title = props.title,
      subtitle = props.subtitle,
      value = enteredCode,
      resendCodeContent = state.resendCodeState.content,
      skipForNowContent =
        buildSkipForNowContent(
          eventTracker,
          hasResentCodeOnce,
          props,
          setState = { produceState ->
            state = produceState(state)
          }
        ),
      explainerText =
        when (props.notificationTouchpoint) {
          is EmailTouchpoint -> "If the code doesn’t arrive, please check your spam folder."
          is PhoneNumberTouchpoint -> null
        },
      onValueChange = { newEnteredText ->
        enteredCode = newEnteredText.take(props.expectedCodeLength)
        if (enteredCode.length == props.expectedCodeLength) {
          props.onCodeEntered(enteredCode)
        }
      },
      onBack = props.onBack,
      errorOverlay =
        when (val resendCodeState = state.resendCodeState) {
          is ErrorState ->
            ResendCodeErrorSheet(
              isConnectivityError = resendCodeState.isConnectivityError,
              onDismiss = {
                state =
                  state.copy(
                    resendCodeState = AvailableState(content = resendCodeState.content)
                  )
              }
            )

          else ->
            when (val skipBottomSheetState = state.skipBottomSheetState) {
              is SkipBottomSheetState.Hidden -> null
              is SkipBottomSheetState.Showing -> skipBottomSheetState.sheet
            }
        },
      id = props.screenId
    )
  }

  private data class State(
    val resendCodeState: ResendCodeState,
    val skipBottomSheetState: SkipBottomSheetState,
  ) {
    sealed interface SkipBottomSheetState {
      data object Hidden : SkipBottomSheetState

      data class Showing(val sheet: SheetModel) : SkipBottomSheetState
    }

    sealed interface ResendCodeState {
      val content: ResendCodeContent

      /** We are blocking the customer from resending the code until the given time. */
      data class BlockedState(
        val resendCodeAvailableTime: Instant,
        override val content: Text,
      ) : ResendCodeState {
        fun remainingDuration(clock: Clock) = resendCodeAvailableTime - clock.now()
      }

      /** The customer can now resend the code */
      data class AvailableState(
        override val content: Button,
      ) : ResendCodeState {
        constructor(onSendCodeAgain: () -> Unit) : this(
          content = Button(onSendCodeAgain = onSendCodeAgain, isLoading = false)
        )
      }

      /** We are in the process of resending the code */
      data class LoadingState(
        override val content: Button =
          Button(onSendCodeAgain = {}, isLoading = true),
      ) : ResendCodeState

      /** There was an error resending the code */
      data class ErrorState(
        val isConnectivityError: Boolean,
        override val content: Button,
      ) : ResendCodeState
    }
  }

  private fun resendCode(
    clock: Clock,
    durationFormatter: DurationFormatter,
    eventTracker: EventTracker,
    props: VerificationCodeInputProps,
    setState: (
      produceState: (currentState: State) -> State,
    ) -> Unit,
  ) {
    when (props.notificationTouchpoint) {
      is EmailTouchpoint -> eventTracker.track(ACTION_APP_EMAIL_RESEND)
      is PhoneNumberTouchpoint -> eventTracker.track(ACTION_APP_PHONE_NUMBER_RESEND)
    }

    // Resend the code and start a new 30 second timer
    props.onResendCode(
      ResendCodeCallbacks(
        onSuccess = {
          handleResendCodeSuccess(clock, durationFormatter, setState)
        },
        onError = {
          handleResendCodeError(
            isConnectivityError = it,
            onSendCodeAgain = {
              resendCode(
                clock,
                durationFormatter,
                eventTracker,
                props,
                setState
              )
            },
            setState = setState
          )
        }
      )
    )
    // Right after we resend, we change the state to loading
    setState { it.copy(resendCodeState = LoadingState()) }
  }

  private fun handleResendCodeSuccess(
    clock: Clock,
    durationFormatter: DurationFormatter,
    setState: (
      produceState: (currentState: State) -> State,
    ) -> Unit,
  ) {
    // Once resending succeeds, we change the state back to blocked
    val newResendCodeAvailableTime = clock.now() + 30.seconds
    setState {
      it.copy(
        resendCodeState =
          BlockedState(
            resendCodeAvailableTime = newResendCodeAvailableTime,
            content =
              blockedResendCodeContent(
                clock,
                durationFormatter,
                newResendCodeAvailableTime
              )
          )
      )
    }
  }

  private fun handleResendCodeError(
    isConnectivityError: Boolean,
    onSendCodeAgain: () -> Unit,
    setState: (
      produceState: (currentState: State) -> State,
    ) -> Unit,
  ) {
    // If resending fails, we show an error
    setState {
      it.copy(
        resendCodeState =
          ErrorState(
            isConnectivityError = isConnectivityError,
            content = AvailableState(onSendCodeAgain).content
          )
      )
    }
  }

  private fun ResendCodeState.calculateState(
    clock: Clock,
    durationFormatter: DurationFormatter,
    onSendCodeAgain: () -> Unit,
  ): ResendCodeState {
    return when (this) {
      is BlockedState -> {
        if (remainingDuration(clock) <= 0.seconds) {
          AvailableState(onSendCodeAgain)
        } else {
          this.copy(
            content = blockedResendCodeContent(clock, durationFormatter, resendCodeAvailableTime)
          )
        }
      }

      else -> this
    }
  }

  private fun Clock.remainingResendCodeDuration(resendCodeAvailableTime: Instant) =
    resendCodeAvailableTime - now()

  private fun blockedResendCodeContent(
    clock: Clock,
    durationFormatter: DurationFormatter,
    resendCodeAvailableTime: Instant,
  ): Text {
    val remainingDuration = clock.remainingResendCodeDuration(resendCodeAvailableTime)
    return Text(
      value = "Resend code in ${durationFormatter.formatWithMMSS(remainingDuration)}"
    )
  }

  private fun buildSkipForNowContent(
    eventTracker: EventTracker,
    hasResentCodeOnce: Boolean,
    props: VerificationCodeInputProps,
    setState: (
      produceState: (currentState: State) -> State,
    ) -> Unit,
  ): SkipForNowContent {
    if (!hasResentCodeOnce) {
      return Hidden
    }

    if (props.skipBottomSheetProvider == null) {
      return Hidden
    }

    // Build the sheet with the go back behavior
    val skipBottomSheet =
      props.skipBottomSheetProvider.invoke {
        // onBack
        setState {
          it.copy(skipBottomSheetState = SkipBottomSheetState.Hidden)
        }
      }

    return Showing(
      text = "Can’t receive the code?",
      onSkipForNow = {
        when (props.notificationTouchpoint) {
          is EmailTouchpoint -> eventTracker.track(ACTION_APP_EMAIL_RESEND_SKIP_FOR_NOW)
          is PhoneNumberTouchpoint ->
            eventTracker.track(
              ACTION_APP_PHONE_NUMBER_RESEND_SKIP_FOR_NOW
            )
        }

        // Update the state to show the sheet
        setState {
          it.copy(
            skipBottomSheetState =
              SkipBottomSheetState.Showing(
                sheet = skipBottomSheet
              )
          )
        }
      }
    )
  }
}
