package build.wallet.statemachine.trustedcontact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactRole.Companion.Beneficiary
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.relationships.*
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.trustedcontact.model.*
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant.Direct
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant.Feature.Inheritance
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import okio.ByteString

@BitkeyInject(ActivityScope::class)
class TrustedContactEnrollmentUiStateMachineImpl(
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val eventTracker: EventTracker,
  private val relationshipsService: RelationshipsService,
  private val delegatedDecryptionKeyService: DelegatedDecryptionKeyService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val createAccountUiStateMachine: CreateAccountUiStateMachine,
  private val promoCodeUpsellUiStateMachine: PromoCodeUpsellUiStateMachine,
) : TrustedContactEnrollmentUiStateMachine {
  @Suppress("ComplexMethod")
  @Composable
  override fun model(props: TrustedContactEnrollmentUiProps): ScreenModel {
    var uiState: State by remember {
      if (props.inviteCode != null) {
        eventTracker.track(Action.ACTION_APP_SOCREC_ENTERED_INVITE_VIA_DEEPLINK)
        mutableStateOf(State.RetrievingInviteWithF8e)
      } else {
        mutableStateOf(State.EnteringInviteCode)
      }
    }
    var inviteCode by remember { mutableStateOf(props.inviteCode ?: "") }
    var isInheritance by remember { mutableStateOf(false) }
    var promoCode: PromotionCode? by remember { mutableStateOf(null) }
    var protectedCustomerAlias by remember { mutableStateOf(ProtectedCustomerAlias("")) }

    return when (val state = uiState) {
      is State.EnteringInviteCode -> {
        EnteringInviteCodeBodyModel(
          value = inviteCode,
          onValueChange = { inviteCode = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = inviteCode.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {
                uiState = State.RetrievingInviteWithF8e
              }
            ),
          retreat = props.retreat,
          variant = props.variant
        ).asScreen(props.screenPresentationStyle)
      }

      is State.RetrievingInviteWithF8e -> {
        LaunchedEffect("retrieve-invitation") {
          relationshipsService.retrieveInvitation(props.account, inviteCode)
            .onFailure {
              logWarn(throwable = it.cause) { "Failed to retrieve invite using code [$inviteCode]" }
              uiState = State.RetrievingInviteWithF8eFailure(error = it)
            }
            .onSuccess { invite ->
              isInheritance = invite.recoveryRelationshipRoles.contains(Beneficiary)

              val nextState = {
                uiState = if (isInheritance) {
                  State.ShowingBeneficiaryOnboardingMessage(invite)
                } else {
                  State.EnteringProtectedCustomerName(props.account, invite)
                }
              }

              relationshipsService.retrieveInvitationPromotionCode(props.account, inviteCode)
                .onSuccess { promo ->
                  promoCode = promo
                  nextState()
                }
                .onFailure {
                  nextState()
                }
            }
        }

        val variant = props.variant
        val screenId = if (variant is Direct && variant.target == Inheritance) {
          TC_BENEFICIARY_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
        } else {
          TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
        }
        LoadingBodyModel(id = screenId)
          .asScreen(props.screenPresentationStyle)
      }

      is State.RetrievingInviteWithF8eFailure ->
        RetrievingInviteWithF8eFailureBodyModel(
          error = state.error,
          onRetry = { uiState = State.RetrievingInviteWithF8e },
          onBack = { uiState = State.EnteringInviteCode }
        ).asScreen(props.screenPresentationStyle)

      is State.ShowingBeneficiaryOnboardingMessage ->
        BeneficiaryOnboardingBodyModel(
          onBack = { uiState = State.EnteringInviteCode },
          onContinue = {
            uiState = State.EnteringBenefactorName(props.account, state.invitation)
          },
          onMoreInfo = {
            uiState = State.ViewingInheritanceInfo(state.invitation)
          }
        ).asScreen(props.screenPresentationStyle)

      is State.EnteringProtectedCustomerName -> {
        var value by remember { mutableStateOf("") }

        EnteringProtectedCustomerNameBodyModel(
          value = value,
          onValueChange = { value = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = value.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick =
                StandardClick {
                  protectedCustomerAlias = ProtectedCustomerAlias(value)
                  uiState =
                    State.LoadIdentityKey(state.account, state.invitation, ProtectedCustomerAlias(value))
                }
            ),
          retreat =
            Retreat(
              style = RetreatStyle.Back,
              onRetreat = {
                uiState = if (state.invitation.recoveryRelationshipRoles.contains(Beneficiary)) {
                  State.ShowingBeneficiaryOnboardingMessage(state.invitation)
                } else {
                  State.EnteringInviteCode
                }
              }
            )
        ).asScreen(props.screenPresentationStyle)
      }

      is State.UploadDelegatedDecryptionKeypair -> {
        LaunchedEffect("upload-ddk") {
          if (state.sealedData == null) {
            uiState = State.UploadDelegatedDecryptionKeypair(
              fullAccount = state.fullAccount,
              invitation = state.invitation,
              protectedCustomerAlias = state.protectedCustomerAlias,
              delegatedDecryptionKeypair = state.delegatedDecryptionKeypair,
              encodedKey = state.delegatedDecryptionKeypair.privateKey.bytes
            )
          } else {
            uploadSealedData(
              state = state,
              sealedData = state.sealedData,
              onSuccess = { uiState = it },
              onFailure = { uiState = it }
            )
          }
        }

        if (state.sealedData == null && state.encodedKey != null) {
          return nfcSessionUIStateMachine.model(
            NfcSessionUIStateMachineProps(
              session = { session, commands ->
                commands.sealData(session = session, unsealedData = state.encodedKey)
              },
              onCancel = {
                uiState = State.EnteringProtectedCustomerName(state.fullAccount, state.invitation)
              },
              onSuccess = { sealedData ->
                uploadSealedData(
                  state = state,
                  sealedData = sealedData,
                  onSuccess = { uiState = it },
                  onFailure = { uiState = it }
                )
              },
              isHardwareFake = state.fullAccount.config.isHardwareFake,
              screenPresentationStyle = Modal,
              eventTrackerContext = NfcEventTrackerScreenIdContext.SEAL_DELEGATED_DECRYPTION_KEY
            )
          )
        }

        val screenId = if (state.invitation.recoveryRelationshipRoles.singleOrNull() == Beneficiary) {
          TC_BENEFICIARY_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY
        } else {
          TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY
        }
        LoadingBodyModel(id = screenId)
          .asScreen(props.screenPresentationStyle)
      }

      is State.DelegatedDecryptionKeypairFailure ->
        UploadDelegatedDecryptionKeyFailureBodyModel(
          isInheritance = isInheritance,
          onBack = { uiState = State.EnteringProtectedCustomerName(state.fullAccount, state.invitation) },
          onRetry = {
            uiState = State.UploadDelegatedDecryptionKeypair(
              fullAccount = state.fullAccount,
              invitation = state.invitation,
              protectedCustomerAlias = state.protectedCustomerAlias,
              delegatedDecryptionKeypair = state.delegatedDecryptionKeypair,
              sealedData = state.sealedData
            )
          }
        ).asScreen(props.screenPresentationStyle)

      is State.LoadIdentityKey -> {
        LaunchedEffect("load-keys") {
          relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
            .onSuccess { keypair ->
              uiState = if (state.account is FullAccount) {
                State.UploadDelegatedDecryptionKeypair(
                  fullAccount = state.account,
                  invitation = state.invitation,
                  protectedCustomerAlias = state.protectedCustomerAlias,
                  delegatedDecryptionKeypair = keypair
                )
              } else {
                State.AcceptingInviteWithF8e(state.account, state.invitation, state.protectedCustomerAlias, keypair.publicKey)
              }
            }
            .onFailure {
              uiState = State.LoadIdentityKeyFailure(state.account, state.invitation, state.protectedCustomerAlias, it)
            }
        }

        val screenId = if (state.invitation.recoveryRelationshipRoles.singleOrNull() == Beneficiary) {
          TC_BENEFICIARY_ENROLLMENT_LOAD_KEY
        } else {
          TC_ENROLLMENT_LOAD_KEY
        }
        LoadingBodyModel(id = screenId)
          .asScreen(props.screenPresentationStyle)
      }

      is State.LoadIdentityKeyFailure ->
        LoadKeyFailureBodyModel(
          isInheritance = isInheritance,
          onBack = { uiState = State.EnteringProtectedCustomerName(state.account, state.invitation) },
          onRetry = {
            uiState = State.LoadIdentityKey(state.account, state.invitation, state.protectedCustomerAlias)
          }
        ).asScreen(props.screenPresentationStyle)

      is State.AcceptingInviteWithF8e -> {
        LaunchedEffect("accept-invitation") {
          relationshipsService.acceptInvitation(
            account = state.account,
            invitation = state.invitation,
            protectedCustomerAlias = state.protectedCustomerAlias,
            delegatedDecryptionKey = state.delegatedDecryptionKey,
            inviteCode = inviteCode
          ).onFailure {
            uiState =
              State.AcceptingInviteWithF8eFailure(
                account = state.account,
                error = it,
                invitation = state.invitation,
                protectedCustomerAlias = state.protectedCustomerAlias,
                delegatedDecryptionKey = state.delegatedDecryptionKey
              )
          }
            .onSuccess {
              uiState = State.AcceptingInviteWithF8eSuccess(
                recoveryRelationshipRoles = state.invitation.recoveryRelationshipRoles,
                account = state.account,
                protectedCustomer = it
              )
            }
        }

        val screenId = if (state.invitation.recoveryRelationshipRoles.singleOrNull() == Beneficiary) {
          TC_BENEFICIARY_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
        } else {
          TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
        }
        LoadingBodyModel(id = screenId)
          .asScreen(props.screenPresentationStyle)
      }

      is State.AcceptingInviteWithF8eFailure ->
        AcceptingInviteWithF8eFailureBodyModel(
          error = state.error,
          onRetry = {
            uiState = State.AcceptingInviteWithF8e(state.account, state.invitation, state.protectedCustomerAlias, state.delegatedDecryptionKey)
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
          isInheritance = isInheritance,
          onBack = { uiState = State.EnteringProtectedCustomerName(state.account, state.invitation) }
        ).asScreen(props.screenPresentationStyle)

      is State.AcceptingInviteWithF8eSuccess ->
        AcceptingInviteWithF8eSuccessBodyModel(
          recoveryRelationshipRoles = state.recoveryRelationshipRoles,
          protectedCustomer = state.protectedCustomer,
          onDone = {
            props.onDone(state.account)
          }
        ).asScreen(props.screenPresentationStyle)

      is State.ViewingInheritanceInfo -> InAppBrowserModel(
        open = {
          inAppBrowserNavigator.open(
            url = "https://bitkey.world/hc/what-is-beneficiary",
            onClose = { uiState = State.ShowingBeneficiaryOnboardingMessage(state.invitation) }
          )
        }
      ).asScreen(props.screenPresentationStyle)

      is State.AskingIfHasHardware -> AskingIfHasHardwareBodyModel(
        onBack = {
          uiState = State.ShowingBeneficiaryOnboardingMessage(state.invitation)
        },
        onYes = {
          eventTracker.track(Action.ACTION_APP_SOCREC_BENEFICIARY_HAS_HARDWARE)
          uiState = state.copy(isNoChecked = false, isYesChecked = true)
        },
        onNo = {
          eventTracker.track(Action.ACTION_APP_SOCREC_BENEFICIARY_NO_HARDWARE)
          uiState = state.copy(isNoChecked = true, isYesChecked = false)
        },
        onContinue = {
          if (state.isYesChecked) {
            uiState = State.UpgradingLiteAccount(state.invitation, state.account, state.protectedCustomerAlias)
          } else {
            promoCode?.let {
              uiState = State.ShowingPromoCodeUpsell(state.account, it)
            } ?: props.onDone(state.account)
          }
        },
        isYesChecked = state.isYesChecked,
        isNoChecked = state.isNoChecked
      ).asScreen(props.screenPresentationStyle)

      is State.ShowingPromoCodeUpsell -> {
        promoCodeUpsellUiStateMachine.model(
          PromoCodeUpsellUiProps(
            promoCode = state.promoCode,
            onExit = {
              props.onDone(state.account)
            }
          )
        )
      }

      is State.UpgradingLiteAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.LiteToFullAccountUpgrade(state.account),
          rollback = {
            uiState = State.AskingIfHasHardware(state.invitation, state.account, protectedCustomerAlias = state.protectedCustomerAlias)
          },
          onOnboardingComplete = { account ->
            uiState = State.LoadIdentityKey(account, state.invitation, state.protectedCustomerAlias)
          }
        )
      )

      is State.EnteringBenefactorName -> {
        var value by remember { mutableStateOf("") }

        EnteringBenefactorNameBodyModel(
          value = value,
          onValueChange = { value = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = value.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick =
                StandardClick {
                  uiState = when (props.account) {
                    is LiteAccount -> {
                      State.AskingIfHasHardware(
                        invitation = state.invitation,
                        account = props.account,
                        protectedCustomerAlias = ProtectedCustomerAlias(value)
                      )
                    }
                    else -> {
                      State.LoadIdentityKey(state.account, state.invitation, ProtectedCustomerAlias(value))
                    }
                  }
                }
            ),
          retreat =
            Retreat(
              style = RetreatStyle.Back,
              onRetreat = {
                uiState = if (state.invitation.recoveryRelationshipRoles.contains(Beneficiary)) {
                  State.ShowingBeneficiaryOnboardingMessage(state.invitation)
                } else {
                  State.EnteringInviteCode
                }
              }
            )
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }

  private suspend fun uploadSealedData(
    state: State.UploadDelegatedDecryptionKeypair,
    sealedData: SealedData,
    onSuccess: (State) -> Unit,
    onFailure: (State) -> Unit,
  ) = delegatedDecryptionKeyService.uploadSealedDelegatedDecryptionKeyData(
    fullAccountId = state.fullAccount.accountId,
    f8eEnvironment = state.fullAccount.config.f8eEnvironment,
    sealedData = sealedData
  )
    .onSuccess {
      onSuccess(
        State.AcceptingInviteWithF8e(
          account = state.fullAccount,
          invitation = state.invitation,
          protectedCustomerAlias = state.protectedCustomerAlias,
          delegatedDecryptionKey = state.delegatedDecryptionKeypair.publicKey
        )
      )
    }
    .onFailure {
      onFailure(
        State.DelegatedDecryptionKeypairFailure(
          fullAccount = state.fullAccount,
          invitation = state.invitation,
          protectedCustomerAlias = state.protectedCustomerAlias,
          delegatedDecryptionKeypair = state.delegatedDecryptionKeypair,
          sealedData = sealedData
        )
      )
    }
}

private sealed interface State {
  /**
   * The user is entering an invite code.
   * Optional step, omitted if the app is launched from a deeplink with an invite code embedded
   */
  data object EnteringInviteCode : State

  /** Server call to f8e to retrieve the invite data from the code */
  data object RetrievingInviteWithF8e : State

  /** Server call to f8e to retrieve the invite data from the code failed */
  data class RetrievingInviteWithF8eFailure(
    val error: RetrieveInvitationCodeError,
  ) : State

  /** We are showing the user the beneficiary onboarding message */
  data class ShowingBeneficiaryOnboardingMessage(
    val invitation: IncomingInvitation,
  ) : State

  /** The user is entering the name of the customer they will be protecting */
  data class EnteringProtectedCustomerName(
    val account: Account,
    val invitation: IncomingInvitation,
  ) : State

  /** Call to seal the [DelegatedDecryptionKey] */
  data class UploadDelegatedDecryptionKeypair(
    val fullAccount: FullAccount,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val delegatedDecryptionKeypair: AppKey<DelegatedDecryptionKey>,
    val sealedData: SealedData? = null,
    val encodedKey: ByteString? = null,
  ) : State

  /** Call to seal the [DelegatedDecryptionKey] */
  data class DelegatedDecryptionKeypairFailure(
    val fullAccount: FullAccount,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val delegatedDecryptionKeypair: AppKey<DelegatedDecryptionKey>,
    val sealedData: SealedData,
  ) : State

  /** Call to load the [DelegatedDecryptionKey] */
  data class LoadIdentityKey(
    val account: Account,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
  ) : State

  /** Failed to load the [DelegatedDecryptionKey] */
  data class LoadIdentityKeyFailure(
    val account: Account,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val relationshipsKeyError: RelationshipsKeyError,
  ) : State

  /** Server call to f8e to accept the invite */
  data class AcceptingInviteWithF8e(
    val account: Account,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ) : State

  /** Server call to f8e to retrieve the invite data from the code failed */
  data class AcceptingInviteWithF8eFailure(
    val account: Account,
    val error: AcceptInvitationCodeError,
    val invitation: IncomingInvitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ) : State

  /** Screen shown when enrolling as a Trusted Contact succeeded, after accepting the invite. */
  data class AcceptingInviteWithF8eSuccess(
    val recoveryRelationshipRoles: Set<TrustedContactRole>,
    val account: Account,
    val protectedCustomer: ProtectedCustomer,
  ) : State

  data class ViewingInheritanceInfo(
    val invitation: IncomingInvitation,
  ) : State

  /**
   * The customer is being asked if they have the hardware to complete the enrollment as a beneficiary
   */
  data class AskingIfHasHardware(
    val invitation: IncomingInvitation,
    val account: LiteAccount,
    val isYesChecked: Boolean = false,
    val isNoChecked: Boolean = false,
    val protectedCustomerAlias: ProtectedCustomerAlias,
  ) : State

  /**
   * The customer is being shown a promo code upsell
   */
  data class ShowingPromoCodeUpsell(
    val account: Account,
    val promoCode: PromotionCode,
  ) : State

  /**
   * The customer has hardware and is being asked to upgrade to a full account
   */
  data class UpgradingLiteAccount(
    val invitation: IncomingInvitation,
    val account: LiteAccount,
    val protectedCustomerAlias: ProtectedCustomerAlias,
  ) : State

  /**
   * The customer is entering the name of the benefactor
   */
  data class EnteringBenefactorName(
    val account: Account,
    val invitation: IncomingInvitation,
  ) : State
}
