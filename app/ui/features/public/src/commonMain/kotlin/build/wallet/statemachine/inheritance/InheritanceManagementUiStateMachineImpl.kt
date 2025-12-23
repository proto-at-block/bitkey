package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.*
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.ToastState.*
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachineProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiStateMachine
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiProps
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiStateMachine
import build.wallet.ui.model.Click
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toast.ToastModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

@BitkeyInject(ActivityScope::class)
class InheritanceManagementUiStateMachineImpl(
  private val inviteBeneficiaryUiStateMachine: InviteBeneficiaryUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val cancelingClaimUiStateMachine: CancelingClaimUiStateMachine,
  private val removingRelationshipUiStateMachine: RemovingRelationshipUiStateMachine,
  private val inheritanceService: InheritanceService,
  private val startClaimUiStateMachine: StartClaimUiStateMachine,
  private val completeClaimUiStateMachine: CompleteInheritanceClaimUiStateMachine,
  private val reinviteTrustedContactUiStateMachine: ReinviteTrustedContactUiStateMachine,
  private val declineInheritanceClaimUiStateMachine: DeclineInheritanceClaimUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val inheritanceCardUiStateMachine: InheritanceCardUiStateMachine,
  private val sendUiStateMachine: SendUiStateMachine,
) : InheritanceManagementUiStateMachine {
  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun model(props: InheritanceManagementUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.ManagingInheritance()) }
    var selectedTab by remember { mutableStateOf(props.selectedTab) }

    val benefactorStates =
      inheritanceService.benefactorClaimState.collectAsState(emptyImmutableList())
    val beneficiaryStates =
      inheritanceService.beneficiaryClaimState.collectAsState(emptyImmutableList())

    val inheritanceCards = inheritanceCardUiStateMachine.model(
      props = InheritanceCardUiProps(
        isDismissible = false,
        includeDismissed = true,
        claimFilter = { claim ->
          when (selectedTab) {
            ManagingInheritanceTab.Beneficiaries -> claim is BenefactorClaim
            ManagingInheritanceTab.Inheritance -> claim is BeneficiaryClaim
          }
        },
        completeClaim = { claim ->
          uiState = UiState.CompletingClaim(claim.relationshipId)
        },
        denyClaim = { claim ->
          uiState = UiState.DecliningClaim(
            recoveryEntity = beneficiaryStates.value.first {
              it.claims.filter { it.claimId == claim.claimId }.isNotEmpty()
            }.relationship
          )
        },
        moveFundsCallToAction = {
          inAppBrowserNavigator.open("https://bitkey.world/hc/retain-control-of-funds") {}
        }
      )
    )

    return when (val currentState = uiState) {
      // TODO W-9135 W-9383 add inheritance management UI to design spec
      is UiState.StartingClaim -> startClaimUiStateMachine.model(
        StartClaimUiStateMachineProps(
          account = props.account,
          relationshipId = currentState.relationshipId,
          onExit = {
            uiState = UiState.ManagingInheritance()
          }
        )
      )
      is UiState.CompletingClaim -> completeClaimUiStateMachine.model(
        CompleteInheritanceClaimUiStateMachineProps(
          account = props.account,
          relationshipId = currentState.relationshipId,
          onExit = {
            uiState = UiState.ManagingInheritance()
          }
        )
      )

      UiState.LearnMore -> InAppBrowserModel(
        open = {
          inAppBrowserNavigator.open(
            url = "https://bitkey.world/hc/what-is-inheritance",
            onClose = {
              uiState = UiState.ManagingInheritance()
            }
          )
        }
      ).asModalFullScreen()

      is UiState.ManageRelationship,
      is UiState.CancelingClaim,
      is UiState.RemovingRelationship,
      is UiState.SharingInvite,
      is UiState.ManagingInheritance,
      is UiState.BeneficiaryApprovedClaimWarning,
      UiState.BenefactorApprovedClaimWarning,
      -> {
        ManagingInheritanceBodyModel(
          selectedTab = selectedTab,
          onBack = props.onBack,
          onLearnMore = {
            uiState = UiState.LearnMore
          },
          onInviteClick = StandardClick {
            uiState = UiState.InvitingBeneficiary
          },
          onTabRowClick = { tab ->
            selectedTab = tab
          },
          onAcceptInvitation = { uiState = UiState.AcceptingInvitation },
          hasPendingBeneficiaries = beneficiaryStates.value.any { it.isInvite },
          claimCallouts = inheritanceCards,
          beneficiaries = BeneficiaryListModel(
            beneficiaries = beneficiaryStates.value,
            onManageClick = { state ->
              uiState = UiState.ManageRelationship(
                contactState = state
              )
            }
          ),
          benefactors = BenefactorListModel(
            benefactors = benefactorStates.value,
            onManageClick = {
              uiState = UiState.ManageRelationship(it)
            }
          )
        ).let {
          when (currentState) {
            is UiState.ManageRelationship -> {
              ScreenModel(
                body = it,
                bottomSheetModel = SheetModel(
                  body = ManageInheritanceContactBodyModel(
                    onClose = { uiState = UiState.ManagingInheritance() },
                    onRemove = {
                      uiState = if (currentState.contactState.hasApprovedClaim) {
                        if (currentState.contactState is ContactClaimState.Beneficiary) {
                          UiState.BenefactorApprovedClaimWarning
                        } else {
                          UiState.BeneficiaryApprovedClaimWarning(currentState.contactState.relationship.id)
                        }
                      } else {
                        UiState.RemovingRelationship(currentState.contactState.relationship)
                      }
                    },
                    onShare = { invite ->
                      uiState = UiState.SharingInvite(invite)
                    },
                    recoveryEntity = currentState.contactState.relationship,
                    claimControls = when {
                      currentState.contactState is ContactClaimState.Benefactor && currentState.contactState.hasApprovedClaim -> ManageInheritanceContactBodyModel.ClaimControls.Complete(
                        onClick = {
                          uiState =
                            UiState.CompletingClaim(currentState.contactState.relationship.id)
                        }
                      )
                      currentState.contactState.hasCancelableClaim -> ManageInheritanceContactBodyModel.ClaimControls.Cancel(
                        onClick = {
                          uiState = when (currentState.contactState) {
                            is ContactClaimState.Benefactor -> UiState.CancelingClaim(relationshipId = currentState.contactState.relationship.id)
                            else -> UiState.DecliningClaim(recoveryEntity = currentState.contactState.relationship)
                          }
                        }
                      )
                      currentState.contactState is ContactClaimState.Benefactor && !currentState.contactState.hasCompletedClaim -> ManageInheritanceContactBodyModel.ClaimControls.Start(
                        onClick = {
                          uiState = UiState.StartingClaim(currentState.contactState.relationship.id)
                        }
                      )
                      else -> ManageInheritanceContactBodyModel.ClaimControls.None
                    }
                  ),
                  onClosed = { uiState = UiState.ManagingInheritance() }
                )
              )
            }

            is UiState.CancelingClaim ->
              cancelingClaimUiStateMachine.model(
                CancelingClaimUiProps(
                  account = props.account,
                  relationshipId = currentState.relationshipId,
                  body = it,
                  onSuccess = { uiState = UiState.ManagingInheritance(toastState = ClaimCanceled) },
                  onExit = { uiState = UiState.ManagingInheritance() }
                )
              )

            is UiState.RemovingRelationship -> {
              removingRelationshipUiStateMachine.model(
                RemovingRelationshipUiProps(
                  account = props.account,
                  body = it,
                  recoveryEntity = currentState.recoveryEntity,
                  onSuccess = {
                    uiState = UiState.ManagingInheritance(
                      toastState = when (currentState.recoveryEntity) {
                        is ProtectedCustomer -> BenefactorRemoved
                        is Invitation -> CanceledInvite
                        is TrustedContact -> BeneficiaryRemoved
                      }
                    )
                  },
                  onExit = { uiState = UiState.ManagingInheritance() }
                )
              )
            }

            is UiState.BeneficiaryApprovedClaimWarning -> {
              ScreenModel(
                body = it,
                bottomSheetModel = SheetModel(
                  body = BeneficiaryApprovedClaimWarningBodyModel(
                    onTransferFunds = {
                      uiState = UiState.CompletingClaim(currentState.relationshipId)
                    },
                    onClose = { uiState = UiState.ManagingInheritance() }
                  ),
                  onClosed = {
                    uiState = UiState.ManagingInheritance()
                  }
                )
              )
            }

            UiState.BenefactorApprovedClaimWarning -> {
              ScreenModel(
                body = it,
                bottomSheetModel = SheetModel(
                  body = BenefactorApprovedClaimWarningBodyModel(
                    onTransferFunds = {
                      uiState = UiState.SendingFundsOutOfWallet
                    },
                    onClose = { uiState = UiState.ManagingInheritance() }
                  ),
                  onClosed = {
                    uiState = UiState.ManagingInheritance()
                  }
                )
              )
            }

            is UiState.SharingInvite -> {
              reinviteTrustedContactUiStateMachine.model(
                ReinviteTrustedContactUiProps(
                  account = props.account,
                  isBeneficiary = true,
                  trustedContactAlias = currentState.invite.trustedContactAlias.alias,
                  relationshipId = currentState.invite.id.value,
                  onExit = { uiState = UiState.ManagingInheritance() },
                  onSuccess = {
                    uiState = UiState.ManagingInheritance(toastState = ResentInvite)
                  }
                )
              )
            }

            else -> it.asRootScreen(
              toastModel = currentState.toastModel()
            )
          }
        }
      }

      is UiState.DecliningClaim -> {
        LaunchedEffect("decline claim") {
          if (currentState.claimId == null) {
            val claimId = inheritanceService.claims.first()
              .firstOrNull { it.relationshipId == currentState.recoveryEntity.id }
              ?.claimId?.value

            uiState = if (claimId != null) {
              UiState.DecliningClaim(
                claimId = claimId,
                recoveryEntity = currentState.recoveryEntity
              )
            } else {
              // Unable to find the claim, so return to manage screen
              UiState.ManagingInheritance()
            }
          }
        }

        if (currentState.claimId != null) {
          declineInheritanceClaimUiStateMachine.model(
            DeclineInheritanceClaimUiProps(
              fullAccount = props.account,
              claimId = currentState.claimId,
              onBack = { uiState = UiState.ManagingInheritance() },
              onClaimDeclined = {
                uiState = UiState.ManagingInheritance(toastState = ClaimDeclined)
              },
              onBeneficiaryRemoved = {
                uiState = UiState.ManagingInheritance(toastState = BeneficiaryRemoved)
              }
            )
          )
        } else {
          LoadingBodyModel(id = InheritanceEventTrackerScreenId.StartDenyClaim).asModalScreen()
        }
      }

      UiState.InvitingBeneficiary -> inviteBeneficiaryUiStateMachine.model(
        InviteBeneficiaryUiProps(
          account = props.account,
          onExit = {
            uiState = UiState.ManagingInheritance()
          },
          onInvited = {
            uiState = UiState.ManagingInheritance(toastState = SentInvite)
          }
        )
      )

      UiState.AcceptingInvitation -> trustedContactEnrollmentUiStateMachine.model(
        TrustedContactEnrollmentUiProps(
          retreat = Retreat(
            style = RetreatStyle.Close,
            onRetreat = { uiState = UiState.ManagingInheritance() }
          ),
          account = props.account,
          inviteCode = null,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onDone = {
            uiState = UiState.ManagingInheritance(toastState = BenefactorActivated)
          },
          variant = TrustedContactFeatureVariant.Direct(
            target = TrustedContactFeatureVariant.Feature.Inheritance
          )
        )
      )
      UiState.SendingFundsOutOfWallet -> sendUiStateMachine.model(
        SendUiProps(
          validInvoiceInClipboard = null,
          onExit = {
            uiState = UiState.ManagingInheritance()
          },
          onDone = {
            uiState = UiState.ManagingInheritance()
          },
          onGoToUtxoConsolidation = {
            props.onGoToUtxoConsolidation()
          }
        )
      )
    }
  }
}

@Stable
fun BenefactorListModel(
  benefactors: ImmutableList<ContactClaimState.Benefactor>,
  onManageClick: (ContactClaimState.Benefactor) -> Unit,
): ListGroupModel {
  return ListGroupModel(
    items = benefactors.pcListItemModel(
      onManageClick = onManageClick
    ),
    style = ListGroupStyle.DIVIDER
  )
}

@Stable
fun BeneficiaryListModel(
  beneficiaries: ImmutableList<ContactClaimState.Beneficiary>,
  onManageClick: (ContactClaimState.Beneficiary) -> Unit,
): ListGroupModel {
  return ListGroupModel(
    items = beneficiaries.tcListItemModel(onManageClick),
    style = ListGroupStyle.DIVIDER
  )
}

private fun List<ContactClaimState.Beneficiary>.tcListItemModel(
  onManageClick: (ContactClaimState.Beneficiary) -> Unit,
) = map { state ->
  ListItemModel(
    title = state.relationship.trustedContactAlias.alias,
    leadingAccessory = ListItemAccessory.ContactAvatarAccessory(
      name = state.relationship.trustedContactAlias.alias,
      isLoading = state.isInvite || state.hasActiveClaim
    ),
    secondaryText = when {
      state.isInvite -> "Pending"
      state.hasApprovedClaim || state.hasCompletedClaim -> "Claim approved"
      state.hasActiveClaim -> "Claim pending"
      else -> "Active"
    },
    sideTextTint = ListItemSideTextTint.SECONDARY,
    trailingAccessory = ManageContactButton(
      onClick = StandardClick {
        onManageClick(state)
      }
    )
  )
}.toImmutableList()

private fun ImmutableList<ContactClaimState.Benefactor>.pcListItemModel(
  onManageClick: (ContactClaimState.Benefactor) -> Unit,
) = map { state ->
  ListItemModel(
    title = state.relationship.alias.alias,
    leadingAccessory = ListItemAccessory.ContactAvatarAccessory(
      name = state.relationship.alias.alias,
      isLoading = state.hasActiveClaim
    ),
    secondaryText = when {
      state.hasCompletedClaim || state.hasApprovedClaim -> "Claim approved"
      state.hasActiveClaim -> "Claim pending"
      else -> "Active"
    },
    trailingAccessory = ManageContactButton(
      onClick = StandardClick {
        onManageClick(state)
      }
    )
  )
}.toImmutableList()

@Stable
private fun ManageContactButton(onClick: Click): ListItemAccessory {
  return ListItemAccessory.ButtonAccessory(
    model = ButtonModel(
      text = "Manage",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Short,
      onClick = onClick
    )
  )
}

private fun UiState.toastModel(): ToastModel? {
  return (this as? UiState.ManagingInheritance)?.toastState?.let {
    ToastModel(
      id = it.name, // Use a stable id to ensure the toast is not re-shown if the screen recomposes.
      leadingIcon = IconModel(
        icon = Icon.SmallIconCheckFilled,
        iconTint = IconTint.Primary,
        iconSize = IconSize.Accessory
      ),
      title = it.text,
      iconStrokeColor = ToastModel.IconStrokeColor.Black
    )
  }
}

/**
 * Represents a toast that displays temporarily after the user takes completes some action
 */
private enum class ToastState(val text: String) {
  SentInvite("Beneficiary invited"),
  ResentInvite("Invitation sent again"),
  CanceledInvite("Invitation canceled"),
  BenefactorActivated("Benefactor active"),
  BenefactorRemoved("Benefactor removed"),
  BeneficiaryRemoved("Beneficiary removed"),
  ClaimCanceled("Inheritance claim canceled"),
  ClaimDeclined("Inheritance claim declined"),
}

private sealed interface UiState {
  data class ManagingInheritance(
    val toastState: ToastState? = null,
  ) : UiState

  data object InvitingBeneficiary : UiState

  data object AcceptingInvitation : UiState

  data object LearnMore : UiState

  data class CancelingClaim(
    val relationshipId: RelationshipId,
  ) : UiState

  data class DecliningClaim(
    val claimId: String? = null,
    val recoveryEntity: RecoveryEntity,
  ) : UiState

  data class RemovingRelationship(
    val recoveryEntity: RecoveryEntity,
  ) : UiState

  data class ManageRelationship(
    val contactState: ContactClaimState,
  ) : UiState

  data class StartingClaim(
    val relationshipId: RelationshipId,
  ) : UiState

  data class CompletingClaim(
    val relationshipId: RelationshipId,
  ) : UiState

  data class SharingInvite(
    val invite: Invitation,
  ) : UiState

  /**
   * A warning shown to the beneficiary that has an approved claim and attempts to remove the benefactor.
   * The warning prevents the action and optionally allows the claim to be completed
   *
   * @relationshipId the id of the relationship for the relevant claim
   */
  data class BeneficiaryApprovedClaimWarning(
    val relationshipId: RelationshipId,
  ) : UiState

  /**
   * A warning shown to the benefactor that has an approved claim and attempts to remove the beneficiary.
   * The warning prevents the action and optionally allows the benefactor to transfer funds from their wallet
   */
  data object BenefactorApprovedClaimWarning : UiState

  data object SendingFundsOutOfWallet : UiState
}
