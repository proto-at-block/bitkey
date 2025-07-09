package build.wallet.f8e.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import okio.ByteString

/**
 * Verify the social challenge of a protected customer as a trusted contact
 */
interface VerifySocialChallengeF8eClient {
  /**
   * Verify the active social challenge by providing the code sent by the protect customer.
   * The response contains the keys necessary for encrypting the shared secret.
   *
   * @param account - the account id of the trusted contact
   * @param recoveryRelationshipId - the recovery relationship id of the protected customer <> trusted contact
   * @param counter - the server part of the recovery code sent to the trusted contact by the protected customer
   */
  suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    counter: Int,
  ): Result<ChallengeVerificationResponse, NetworkingError>

  /**
   * Respond to the social challenge as a trusted contact.
   *
   * @param account - the account id of the trusted contact
   * @param socialChallengeId - the id of the current active social challenge, provided in [ChallengeVerificationResponse]
   * @param trustedContactRecoveryPakePubkey - provided by using decryptPrivateKeyEncryptionKey on [ChallengeVerificationResponse]
   * @param recoveryPakeConfirmation - provided by using decryptPrivateKeyEncryptionKey on [ChallengeVerificationResponse]
   * @param resealedDek - provided by using decryptPrivateKeyEncryptionKey on [ChallengeVerificationResponse]
   *
   */
  suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, NetworkingError>
}
