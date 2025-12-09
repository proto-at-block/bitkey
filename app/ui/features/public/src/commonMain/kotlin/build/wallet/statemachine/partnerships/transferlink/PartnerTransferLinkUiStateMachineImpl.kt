package build.wallet.statemachine.partnerships.transferlink

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.PartnershipsEventTrackerScreenId.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.partnerships.*
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnershipsSegment
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkUiStateMachineImpl.State.*
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class PartnerTransferLinkUiStateMachineImpl(
  private val partnershipTransferLinkService: PartnershipTransferLinkService,
  private val deepLinkHandler: DeepLinkHandler,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : PartnerTransferLinkUiStateMachine {
  @Composable
  override fun model(props: PartnerTransferLinkProps): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingPartnerInfoState) }

    return when (val currentState = state) {
      is LoadingPartnerInfoState -> {
        LaunchedEffect(props.request) {
          val partnerInfo =
            partnershipTransferLinkService.getPartnerInfoForPartner(props.request.partner)

          partnerInfo.mapBoth(
            success = { state = ConfirmingLinkState(partnerInfo = it) },
            failure = { state = PartnerNotFoundErrorState(it) }
          )
        }

        props.hostScreen.copy(
          bottomSheetModel = SheetModel(
            body = TransferLinkLoadingPartnersBodyModel(
              id = PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO
            ),
            size = MIN40,
            onClosed = props.onExit
          )
        )
      }
      is ConfirmingLinkState -> {
        props.hostScreen.copy(
          bottomSheetModel = SheetModel(
            body = PartnerTransferLinkConfirmationFormBodyModel(
              partnerInfo = currentState.partnerInfo,
              onConfirm = { state = ProcessingState(partnerInfo = currentState.partnerInfo) },
              onCancel = props.onExit
            ),
            size = MIN40,
            onClosed = props.onExit
          )
        )
      }

      is ProcessingState -> {
        LaunchedEffect(props.request) {
          partnershipTransferLinkService.processTransferLink(
            partnerInfo = currentState.partnerInfo,
            tokenizedSecret = props.request.eventId
          )
            .onSuccess { redirectInfo ->
              when (val redirectMethod = redirectInfo.redirectMethod) {
                is PartnerRedirectionMethod.Deeplink -> {
                  val openDeepLinkResult = deepLinkHandler.openDeeplink(
                    url = redirectMethod.urlString,
                    appRestrictions = redirectMethod.appRestrictions
                  )
                  when (openDeepLinkResult) {
                    is OpenDeeplinkResult.Opened -> {
                      when (openDeepLinkResult.appRestrictionResult) {
                        is AppRestrictionResult.Failed -> {
                          logWarn { "Failed to redirect to ${props.request.partner} app due to app restriction." }
                          state = RedirectErrorState
                        }
                        AppRestrictionResult.None, AppRestrictionResult.Success -> {
                          props.onComplete()
                        }
                      }
                    }
                    is OpenDeeplinkResult.Failed -> {
                      logWarn { "Failed to redirect to ${props.request.partner} app." }
                      state = RedirectErrorState
                    }
                  }
                }
                is PartnerRedirectionMethod.Web -> {
                  // Transition to web browser state instead of opening directly
                  state = ShowingInAppBrowserState(redirectMethod.urlString)
                }
              }
            }
            .onFailure { error ->
              state = TransferLinkError(partnerInfo = currentState.partnerInfo, error = error)
            }
        }

        LoadingBodyModel(
          id = PARTNER_TRANSFER_LINK_PROCESSING,
          title = "Linking in progress..."
        ).asModalFullScreen()
      }

      is ShowingInAppBrowserState -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = currentState.url,
              onClose = props.onComplete
            )
          }
        ).asModalScreen()
      }

      is TransferLinkError -> {
        when (currentState.error) {
          is ProcessTransferLinkError.Retryable -> RetryableErrorModal(
            id = PARTNER_TRANSFER_LINK_RETRYABLE_ERROR,
            error = currentState.error.cause,
            onRetry = { state = ProcessingState(currentState.partnerInfo) },
            onCancel = props.onExit
          ).asModalFullScreen()
          is ProcessTransferLinkError.NotRetryable -> UnretryableErrorModal(
            partnerName = currentState.partnerInfo.name,
            error = currentState.error.cause,
            onCancel = props.onExit
          ).asModalFullScreen()
        }
      }

      is RedirectErrorState -> ErrorFormBodyModel(
        eventTrackerScreenId = PARTNER_TRANSFER_REDIRECT_ERROR,
        onBack = props.onExit,
        title = "We couldn’t redirect you back to ${props.request.partner}.",
        primaryButton = ButtonDataModel(
          text = "Got it",
          onClick = props.onExit
        )
      ).asModalFullScreen()

      is PartnerNotFoundErrorState -> RetryableErrorModal(
        id = PARTNER_TRANSFER_PARTNER_NOT_FOUND_ERROR,
        error = currentState.error,
        onRetry = { state = LoadingPartnerInfoState },
        onCancel = props.onExit
      ).asModalFullScreen()
    }
  }

  fun RetryableErrorModal(
    id: EventTrackerScreenId,
    error: Error,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): FormBodyModel {
    return ErrorFormBodyModel(
      eventTrackerScreenId = id,
      onBack = onCancel,
      title = "We hit a snag...",
      subline = "Check your connection and try again.",
      errorData = ErrorData(
        segment = PartnershipsSegment,
        actionDescription = "Creating partner transfer link",
        cause = error
      ),
      primaryButton = ButtonDataModel(
        text = "Try again",
        onClick = onRetry
      ),
      secondaryButton = ButtonDataModel(
        text = "Cancel",
        onClick = onCancel
      )
    )
  }

  fun UnretryableErrorModal(
    partnerName: String,
    error: Throwable,
    onCancel: () -> Unit,
  ): FormBodyModel {
    return ErrorFormBodyModel(
      eventTrackerScreenId = PARTNER_TRANSFER_LINK_UNRETRYABLE_ERROR,
      onBack = onCancel,
      title = "We couldn’t link your Bitkey to $partnerName",
      subline = "Return to $partnerName and try again.",
      errorData = ErrorData(
        segment = PartnershipsSegment,
        actionDescription = "Creating partner transfer link",
        cause = error
      ),
      primaryButton = ButtonDataModel(
        text = "Got it",
        onClick = onCancel
      )
    )
  }

  data class TransferLinkLoadingPartnersBodyModel(
    override val id: EventTrackerScreenId,
  ) : FormBodyModel(
      id = id,
      onBack = {},
      toolbar = null,
      header = null,
      mainContentList = immutableListOf(Loader),
      primaryButton = null,
      renderContext = Sheet
    )

  /**
   * Represents the different states in the partner transfer link flow.
   */
  private sealed interface State {
    /**
     * Initial state where we're loading the partner information
     */
    data object LoadingPartnerInfoState : State

    /**
     * An error state shown when the partner for the deep link cannot be found
     */
    data class PartnerNotFoundErrorState(val error: GetPartnerInfoError) : State

    /**
     * State where the user is presented with a confirmation sheet
     * to approve creating the transfer link with the partner platform.
     */
    data class ConfirmingLinkState(val partnerInfo: PartnerInfo) : State

    /**
     * State where the transfer link request is being processed with the backend.
     * Shows a loading screen while waiting for the server response.
     */
    data class ProcessingState(val partnerInfo: PartnerInfo) : State

    /**
     * State where the in-app browser is opened to display a web-based partner flow.
     * Used when the partner redirection method is web-based rather than a deeplink.
     *
     * @property url The web URL to open in the in-app browser
     */
    data class ShowingInAppBrowserState(val url: String) : State

    /**
     * TransferLinkError state displayed when transfer link creation fails.
     * Provides retry and cancellation options to the user.
     */
    data class TransferLinkError(
      val partnerInfo: PartnerInfo,
      val error: ProcessTransferLinkError,
    ) : State

    /**
     * Error state when something went wrong with redirecting back to the partner
     */
    data object RedirectErrorState : State
  }
}
