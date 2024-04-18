package build.wallet.statemachine.recovery.socrec.challenge

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.EndorsedTrustedContact
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.recovery.socrec.SocRecChallengeActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State for the Social recovery challenge operations providing the user
 * access to start a social challenge and list their trusted contacts to
 * reach out to for completing the challenge.
 */
interface RecoveryChallengeUiStateMachine : StateMachine<RecoveryChallengeUiProps, ScreenModel>

/**
 * Parameters required to start the social recovery challenge flow.
 */
data class RecoveryChallengeUiProps(
  val accountId: FullAccountId,
  val f8eEnvironment: F8eEnvironment,
  val actions: SocRecChallengeActions,
  val relationshipIdToSocRecPkekMap: Map<String, XCiphertext>,
  val sealedPrivateKeyMaterial: XCiphertext,
  val endorsedTrustedContacts: ImmutableList<EndorsedTrustedContact>,
  val onExit: () -> Unit,
  val onKeyRecovered: (FullAccountKeys) -> Unit,
)
