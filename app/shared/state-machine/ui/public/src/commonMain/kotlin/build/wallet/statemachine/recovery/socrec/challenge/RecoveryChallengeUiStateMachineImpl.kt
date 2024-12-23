package build.wallet.statemachine.recovery.socrec.challenge

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.relationships.ChallengeAuthentication
import build.wallet.bitkey.relationships.ChallengeWrapper
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.XCiphertext
import build.wallet.logging.logFailure
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.permissions.Permission
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus.*
import build.wallet.relationships.DecryptPrivateKeyEncryptionKeyOutput
import build.wallet.relationships.RelationshipsCrypto
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.statemachine.core.*
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationRationale
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.START_SOCIAL_RECOVERY_MESSAGE
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(ActivityScope::class)
class RecoveryChallengeUiStateMachineImpl(
  private val crypto: RelationshipsCrypto,
  private val enableNotificationsUiStateMachine: EnableNotificationsUiStateMachine,
  private val deviceTokenManager: DeviceTokenManager,
  private val challengeCodeFormatter: ChallengeCodeFormatter,
  private val permissionChecker: PermissionChecker,
) : RecoveryChallengeUiStateMachine {
  @Composable
  override fun model(props: RecoveryChallengeUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.StartingChallengeState) }

    return when (val current = state) {
      State.StartingChallengeState -> {
        LaunchedEffect("start-challenge") {
          startOrResumeChallenge(props, setState = { state = it })
        }
        LoadingBodyModel(
          message = START_SOCIAL_RECOVERY_MESSAGE,
          id = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING,
          onBack = props.onExit
        ).asRootScreen()
      }
      is State.StartSocialChallengeFailed ->
        ErrorFormBodyModel(
          title = "Failed to start social recovery.",
          primaryButton =
            ButtonDataModel(
              text = "Back",
              onClick = props.onExit
            ),
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
            actionDescription = "Creating a new social recovery challenge",
            cause = current.error
          ),
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_FAILED
        ).asRootScreen()
      is State.EnablePushNotifications ->
        enableNotificationsUiStateMachine.model(
          EnableNotificationsUiProps(
            retreat =
              Retreat(
                style = RetreatStyle.Back,
                onRetreat = props.onExit
              ),
            onComplete = {
              state =
                State.TrustedContactList(
                  challenge = current.challenge
                )
            },
            rationale = NotificationRationale.Recovery,
            eventTrackerContext =
              PushNotificationEventTrackerScreenIdContext.SOCIAL_RECOVERY_CHALLENGE
          )
        ).asRootScreen()
      is State.TrustedContactList -> {
        LaunchedEffect("update-challenge") {
          deviceTokenManager.addDeviceTokenIfPresentForAccount(
            fullAccountId = props.accountId,
            f8eEnvironment = props.f8eEnvironment,
            authTokenScope = AuthTokenScope.Recovery
          ).result.logFailure {
            "Failed to add device token for account during Social Recovery"
          }

          while (isActive) {
            delay(5.seconds)
            props.actions.getChallengeById(current.challenge.challenge.challengeId)
              .onSuccess { updated ->
                state = current.copy(challenge = updated)
              }
          }
        }

        RecoveryChallengeContactListBodyModel(
          onExit = props.onExit,
          endorsedTrustedContacts = props.endorsedTrustedContacts,
          onVerifyClick = {
            state =
              State.ShareChallengeCode(
                selectedContact = it,
                challenge = current.challenge
              )
          },
          verifiedBy = current.challenge.challenge.responses.map {
            it.recoveryRelationshipId
          }.toImmutableList(),
          onContinue = {
            val response = current.challenge.challenge.responses.first()
            val respondingContact =
              props.endorsedTrustedContacts.first { contact ->
                contact.relationshipId == response.recoveryRelationshipId
              }
            state =
              State.RestoringAppKey(
                sealedPrivateKeyMaterial = props.sealedPrivateKeyMaterial,
                response = response,
                contact = respondingContact,
                challengeAuth =
                  current.challenge.tcAuths.first {
                    it.relationshipId == response.recoveryRelationshipId
                  }
              )
          },
          onCancelRecovery = props.onExit
        ).asRootScreen()
      }
      is State.ShareChallengeCode -> {
        RecoveryChallengeCodeBodyModel(
          recoveryChallengeCode =
            challengeCodeFormatter.format(
              current.challenge.tcAuths
                .first { it.relationshipId == current.selectedContact.relationshipId }
                .fullCode
            ),
          onBack = {
            state =
              State.TrustedContactList(
                challenge = current.challenge
              )
          },
          onDone = {
            state =
              State.TrustedContactList(
                challenge = current.challenge
              )
          }
        ).asRootScreen()
      }

      is State.RestoringAppKey -> {
        LaunchedEffect("restore-app-key") {
          crypto.decryptPrivateKeyMaterial(
            password = current.challengeAuth.pakeCode,
            protectedCustomerRecoveryPakeKey = current.challengeAuth.protectedCustomerRecoveryPakeKey,
            decryptPrivateKeyEncryptionKeyOutput = DecryptPrivateKeyEncryptionKeyOutput(
              trustedContactRecoveryPakeKey = current.response.trustedContactRecoveryPakePubkey,
              keyConfirmation = current.response.recoveryPakeConfirmation,
              sealedPrivateKeyEncryptionKey = current.response.resealedDek
            ),
            sealedPrivateKeyMaterial = current.sealedPrivateKeyMaterial
          ).logFailure {
            "Error decrypting SocRec payload during recovery"
          }.flatMap {
            Json.decodeFromStringResult<FullAccountKeys>(it.utf8())
          }.logFailure {
            "Error decoding SocRec payload during recovery"
          }.onSuccess { pkMat ->
            props.onKeyRecovered(pkMat)
          }.onFailure { error ->
            state = State.RecoveryFailed(error = error)
          }
        }
        LoadingBodyModel(
          message = "Completing Recovery...",
          id = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY
        ).asRootScreen()
      }
      is State.RecoveryFailed -> {
        ErrorFormBodyModel(
          title = "We couldn’t complete recovery.",
          primaryButton =
            ButtonDataModel(
              text = "Back",
              onClick = {
                state = State.StartingChallengeState
              }
            ),
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
            actionDescription = "Restoring app key after successful challenge response",
            cause = current.error
          ),
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RECOVERY_FAILED
        ).asRootScreen()
      }
    }
  }

  private suspend fun startOrResumeChallenge(
    props: RecoveryChallengeUiProps,
    setState: (State) -> Unit,
  ) {
    coroutineBinding {
      val currentChallenge = props.actions.getCurrentChallenge().bind()
      val challenge =
        currentChallenge ?: props.actions.startChallenge(
          props.endorsedTrustedContacts,
          props.relationshipIdToSocRecPkekMap
        ).bind()

      val newState = when (permissionChecker.getPermissionStatus(Permission.PushNotifications)) {
        Authorized -> State.TrustedContactList(
          challenge = challenge
        )
        Denied, NotDetermined ->
          State.EnablePushNotifications(
            challenge = challenge
          )
      }
      setState(newState)
    }.onFailure { error ->
      setState(State.StartSocialChallengeFailed(error = error))
    }
  }

  private sealed interface State {
    data object StartingChallengeState : State

    data class EnablePushNotifications(
      val challenge: ChallengeWrapper,
    ) : State

    data class StartSocialChallengeFailed(val error: Error) : State

    data class TrustedContactList(
      val challenge: ChallengeWrapper,
    ) : State

    data class ShareChallengeCode(
      val selectedContact: EndorsedTrustedContact,
      val challenge: ChallengeWrapper,
    ) : State

    data class RestoringAppKey(
      val sealedPrivateKeyMaterial: XCiphertext,
      val response: SocialChallengeResponse,
      val contact: EndorsedTrustedContact,
      val challengeAuth: ChallengeAuthentication,
    ) : State

    data class RecoveryFailed(
      val error: Error,
    ) : State
  }
}
