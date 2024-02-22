package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import com.github.michaelbull.result.Result

/**
 * Manages the active Social Recovery Challenges for a given account.
 */
interface SocRecChallengeRepository {
  /**
   * Starts a new Social Recovery Challenge for the [account].
   */
  suspend fun startChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
  ): Result<SocialChallenge, Error>

  /**
   * Get the current active social challenge for this device, if one exists.
   */
  suspend fun getCurrentChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge?, Error>

  /**
   * Get a a specific social challenge by a specified ID.
   */
  suspend fun getChallengeById(
    challengeId: String,
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge, Error>

  /**
   * Verify the active social challenge by providing the code sent by the protect customer.
   * The response contains the keys necessary for encrypting the shared secret.
   */
  suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: String,
  ): Result<ChallengeVerificationResponse, Error>

  /**
   * Respond to the social challenge by providing the shared secret for the active challenge
   */
  suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    sharedSecretCiphertext: XCiphertext,
  ): Result<Unit, Error>
}

/**
 * Wrap the repository in an account to get the performable actions for challenges.
 */
fun SocRecChallengeRepository.toActions(
  accountId: FullAccountId,
  f8eEnvironment: F8eEnvironment,
  isUsingSocRecFakes: Boolean,
) = SocRecChallengeActions(
  repository = this,
  accountId = accountId,
  f8eEnvironment = f8eEnvironment,
  isUsingSocRecFakes = isUsingSocRecFakes
)
