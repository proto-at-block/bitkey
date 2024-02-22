package build.wallet.statemachine.recovery.socrec.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.encrypt.XCiphertext
import build.wallet.logging.logFailure
import build.wallet.notifications.DeviceTokenManager
import build.wallet.recovery.socrec.DecryptPrivateKeyMaterialParams
import build.wallet.recovery.socrec.SocRecCrypto
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.recovery.cloud.START_SOCIAL_RECOVERY_MESSAGE
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class RecoveryChallengeUiStateMachineImpl(
  private val crypto: SocRecCrypto,
  private val keysRepository: SocRecKeysRepository,
  private val enableNotificationsUiStateMachine: EnableNotificationsUiStateMachine,
  private val deviceTokenManager: DeviceTokenManager,
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
                  protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                  protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
                  challenge = current.challenge
                )
            },
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
            props.actions.getChallengeById(current.challenge.challengeId)
              .onSuccess { updated ->
                state = current.copy(challenge = updated)
              }
          }
        }

        RecoveryChallengeContactListBodyModel(
          onExit = props.onExit,
          trustedContacts = props.trustedContacts,
          onVerifyClick = {
            state =
              State.ShareChallengeCode(
                selectedContact = it,
                protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
                challenge = current.challenge
              )
          },
          verifiedBy = current.challenge.responses.map { it.recoveryRelationshipId }.toImmutableList(),
          onContinue = {
            val response = current.challenge.responses.first()
            val respondingContact =
              props.trustedContacts.first { contact ->
                contact.recoveryRelationshipId == response.recoveryRelationshipId
              }
            state =
              State.RestoringAppKey(
                protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
                protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                relationshipIdToSealedPrivateKeyEncryptionKeyMap = props.relationshipIdToSocRecPkekMap,
                sealedPrivateKeyMaterial = props.sealedPrivateKeyMaterial,
                response = response,
                contact = respondingContact
              )
          }
        ).asRootScreen()
      }
      is State.ShareChallengeCode -> {
        // A code of length six is expected, but leave this unenforced so server has the
        // freedom to redefine code lengths without requiring an app update.
        val formattedCode =
          current.challenge.code.let {
            if (it.length == 6) {
              "${it.take(3)}-${it.takeLast(3)}"
            } else {
              it
            }
          }
        RecoveryChallengeCodeBodyModel(
          recoveryChallengeCode = formattedCode,
          onBack = {
            state =
              State.TrustedContactList(
                protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
                challenge = current.challenge
              )
          },
          onDone = {
            state =
              State.TrustedContactList(
                protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
                challenge = current.challenge
              )
          }
        ).asRootScreen()
      }

      is State.RestoringAppKey -> {
        LaunchedEffect("restore-app-key") {
          crypto.decryptPrivateKeyMaterial(
            protectedCustomerIdentityKey = current.protectedCustomerIdentityKey,
            trustedContactIdentityKey = current.contact.identityKey,
            sealedPrivateKeyMaterial = current.sealedPrivateKeyMaterial,
            secureChannelData =
              DecryptPrivateKeyMaterialParams.V1(
                sharedSecretCipherText = current.response.sharedSecretCiphertext,
                protectedCustomerEphemeralKey = current.protectedCustomerEphemeralKey,
                sealedPrivateKeyEncryptionKey = current.relationshipIdToSealedPrivateKeyEncryptionKeyMap[current.contact.recoveryRelationshipId]!!
              )
          ).flatMap {
            Json.decodeFromStringResult<FullAccountKeys>(it.utf8())
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
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RECOVERY_FAILED
        ).asRootScreen()
      }
    }
  }

  private suspend fun startOrResumeChallenge(
    props: RecoveryChallengeUiProps,
    setState: (State) -> Unit,
  ) {
    binding {
      val identityKey = props.protectedCustomerIdentityKey
      val ephemeralKey =
        keysRepository.getKeyWithPrivateMaterialOrCreate(
          ::ProtectedCustomerEphemeralKey
        ).bind()
      val currentChallenge = props.actions.getCurrentChallenge().bind()
      val challenge =
        currentChallenge ?: props.actions.startChallenge(
          ephemeralKey,
          identityKey
        ).bind()

      setState(
        State.EnablePushNotifications(
          protectedCustomerEphemeralKey = ephemeralKey,
          protectedCustomerIdentityKey = identityKey,
          challenge = challenge
        )
      )
    }.onFailure { error ->
      setState(State.StartSocialChallengeFailed(error = error))
    }
  }

  private sealed interface State {
    data object StartingChallengeState : State

    data class EnablePushNotifications(
      val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
      val protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
      val challenge: SocialChallenge,
    ) : State

    data class StartSocialChallengeFailed(val error: Error) : State

    data class TrustedContactList(
      val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
      val protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
      val challenge: SocialChallenge,
    ) : State

    data class ShareChallengeCode(
      val selectedContact: TrustedContact,
      val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
      val protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
      val challenge: SocialChallenge,
    ) : State

    data class RestoringAppKey(
      val protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
      val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
      val relationshipIdToSealedPrivateKeyEncryptionKeyMap: Map<String, XCiphertext>,
      val sealedPrivateKeyMaterial: XCiphertext,
      val response: SocialChallengeResponse,
      val contact: TrustedContact,
    ) : State

    data class RecoveryFailed(
      val error: Error,
    ) : State
  }
}
