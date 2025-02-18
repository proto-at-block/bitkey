package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceClaimsRepository
import build.wallet.inheritance.InheritanceService
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class DeclineInheritanceClaimUiStateMachineImpl(
  private val inheritanceService: InheritanceService,
  private val inheritanceClaimsRepository: InheritanceClaimsRepository,
  private val removingRelationshipUiStateMachine: RemovingRelationshipUiStateMachine,
  private val browserNavigator: InAppBrowserNavigator,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : DeclineInheritanceClaimUiStateMachine {
  @Composable
  override fun model(props: DeclineInheritanceClaimUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.Loading) }

    return when (val state = uiState) {
      UiState.Loading -> {
        LaunchedEffect("fetch claims") {
          inheritanceClaimsRepository.fetchClaims()
            .onSuccess {
              val claim = it.benefactorClaims.firstOrNull {
                it.claimId.value == props.claimId
              } as? BenefactorClaim.PendingClaim
              val trustedContact = inheritanceService.inheritanceRelationships.map {
                  relationships ->
                relationships.endorsedTrustedContacts.find { contact ->
                  contact.id == claim?.relationshipId
                }
              }.first()
              if (claim != null && trustedContact != null) {
                uiState = UiState.DeclineClaim(trustedContact, claim)
              } else {
                uiState = UiState.ClaimNotFound(
                  if (claim == null) {
                    LoadingError.IllegalClaimState(IllegalStateException("Cancelable claim not found"))
                  } else {
                    LoadingError.ContactNotFound(IllegalStateException("Contact not found"))
                  }
                )
              }
            }
            .onFailure {
              uiState = UiState.ClaimLoadingFailed(it)
            }
        }

        LoadingBodyModel(id = InheritanceEventTrackerScreenId.DenyClaim).asModalScreen()
      }

      is UiState.DeclineClaim -> {
        DeclineClaimBodyModel(
          title = "Decline inheritance claim",
          subtitle = "${state.trustedContact.trustedContactAlias.alias} has started an inheritance claim. Decline this claim by ${endDateForClaim(state.claim)} to retain control of your funds.",
          dismiss = {
            props.onBack()
          },
          declineClaim = {
            uiState = UiState.DecliningClaim(state.trustedContact)
          },
          learnMore = {
            browserNavigator.open("https://bitkey.world/hc/cancel-inheritance-claim", onClose = {})
          }
        ).asModalScreen()
      }

      is UiState.DecliningClaim -> {
        LaunchedEffect("decline claim") {
          inheritanceService.cancelClaims(state.trustedContact.id)
            .onSuccess {
              uiState = UiState.DeclinedClaim(state.trustedContact)
            }
            .onFailure {
              uiState = UiState.DeclineClaimFailed(it)
            }
        }

        LoadingBodyModel(id = InheritanceEventTrackerScreenId.DenyClaim).asModalScreen()
      }

      is UiState.DeclineClaimFailed -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Failed To Deny Claim",
            subline = "Please check your internet connection and try again.",
            primaryButton = ButtonDataModel(
              text = "Okay",
              onClick = {
                uiState = UiState.Loading
              }
            ),
            errorData = ErrorData(
              segment = InheritanceAppSegment.BenefactorClaim.Deny,
              cause = state.error,
              actionDescription = "Denying inheritance claim"
            ),
            eventTrackerScreenId = InheritanceEventTrackerScreenId.DenyClaim
          )
        )
      }

      is UiState.ClaimNotFound -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Unable to load cancellation data",
            subline = when (state.error) {
              is LoadingError.IllegalClaimState -> "Unable to find a cancelable claim. It may be complete, or already canceled."
              is LoadingError.ContactNotFound -> "Unable to find the contact associated with this claim."
            },
            primaryButton = ButtonDataModel(
              text = "Okay",
              onClick = {
                props.onBack()
              }
            ),
            errorData = ErrorData(
              segment = InheritanceAppSegment.BenefactorClaim.Deny,
              cause = state.error.throwable,
              actionDescription = "Loading inheritance claim for denial"
            ),
            eventTrackerScreenId = InheritanceEventTrackerScreenId.DenyClaim
          )
        )
      }

      is UiState.ClaimLoadingFailed -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Failed To Load Claim",
            subline = "Please check your internet connection and try again.",
            primaryButton = ButtonDataModel(
              text = "Okay",
              onClick = {
                props.onBack()
              }
            ),
            errorData = ErrorData(
              segment = InheritanceAppSegment.BenefactorClaim.Deny,
              cause = state.error,
              actionDescription = "Loading inheritance claim for denial"
            ),
            eventTrackerScreenId = InheritanceEventTrackerScreenId.DenyClaim
          )
        )
      }

      is UiState.DeclinedClaim -> {
        DeclinedClaimBodyModel(
          dismiss = props.onClaimDeclined,
          removeBeneficiary = {
            uiState = UiState.RemoveTrustedContact(state.trustedContact)
          }
        ).asModalScreen()
      }

      is UiState.RemoveTrustedContact -> {
        removingRelationshipUiStateMachine.model(
          props = RemovingRelationshipUiProps(
            body = DeclinedClaimBodyModel(
              dismiss = props.onClaimDeclined,
              removeBeneficiary = {
                uiState = UiState.RemoveTrustedContact(state.trustedContact)
              }
            ),
            account = props.fullAccount,
            recoveryEntity = state.trustedContact,
            onSuccess = props.onBeneficiaryRemoved,
            onExit = {
              uiState = UiState.DeclinedClaim(state.trustedContact)
            }
          )
        )
      }
    }
  }

  private fun endDateForClaim(claim: BenefactorClaim.PendingClaim): String =
    dateTimeFormatter.shortDateWithYear(
      claim.delayEndTime.toLocalDateTime(
        timeZoneProvider.current()
      )
    )

  private sealed interface LoadingError {
    val throwable: Throwable

    data class IllegalClaimState(override val throwable: Throwable) : LoadingError

    data class ContactNotFound(override val throwable: Throwable) : LoadingError
  }

  private sealed interface UiState {
    data object Loading : UiState

    data class DeclineClaim(
      val trustedContact: EndorsedTrustedContact,
      val claim: BenefactorClaim.PendingClaim,
    ) : UiState

    data class DecliningClaim(val trustedContact: EndorsedTrustedContact) : UiState

    data class ClaimLoadingFailed(val error: Throwable) : UiState

    data class ClaimNotFound(val error: LoadingError) : UiState

    data class DeclinedClaim(val trustedContact: EndorsedTrustedContact) : UiState

    data class DeclineClaimFailed(val error: Throwable) : UiState

    data class RemoveTrustedContact(val trustedContact: EndorsedTrustedContact) : UiState
  }
}
