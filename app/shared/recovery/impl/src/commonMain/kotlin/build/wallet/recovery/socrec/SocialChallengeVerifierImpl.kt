package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class SocialChallengeVerifierImpl(
  private val socRecChallengeRepository: SocRecChallengeRepository,
  private val socRecCrypto: SocRecCrypto,
) : SocialChallengeVerifier {
  override suspend fun verifyChallenge(
    account: Account,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    recoveryRelationshipId: String,
    code: String,
  ): Result<Unit, SocialChallengeError> =
    binding {
      val challengeResponse =
        socRecChallengeRepository.verifyChallenge(
          account = account,
          recoveryRelationshipId = recoveryRelationshipId,
          code = code
        ).mapError { SocialChallengeError.UnableToVerifyChallengeError(cause = it) }
          .bind()

      val protectedCustomerIdentityKey =
        ProtectedCustomerIdentityKey(
          AppKey.fromPublicKey(challengeResponse.customerIdentityPublicKey)
        )

      val protectedCustomerEphemeralKey =
        ProtectedCustomerEphemeralKey(
          AppKey.fromPublicKey(challengeResponse.customerEphemeralPublicKey)
        )

      val secretShareCipherText =
        socRecCrypto.deriveAndEncryptSharedSecret(
          protectedCustomerIdentityKey = protectedCustomerIdentityKey,
          protectedCustomerEphemeralKey = protectedCustomerEphemeralKey,
          trustedContactIdentityKey = trustedContactIdentityKey
        ).mapError { SocialChallengeError.UnableToEncryptSharedSecretError(cause = it) }
          .bind()

      socRecChallengeRepository.respondToChallenge(
        account = account,
        socialChallengeId = challengeResponse.socialChallengeId,
        sharedSecretCiphertext = secretShareCipherText
      ).mapError { SocialChallengeError.UnableToRespondToChallengeError(cause = it) }
        .bind()
    }
}
