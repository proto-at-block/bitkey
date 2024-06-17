package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class SocialChallengeVerifierImpl(
  private val socRecChallengeRepository: SocRecChallengeRepository,
  private val socRecCrypto: SocRecCrypto,
  private val socialRecoveryCodeBuilder: SocialRecoveryCodeBuilder,
) : SocialChallengeVerifier {
  override suspend fun verifyChallenge(
    account: Account,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    recoveryRelationshipId: String,
    recoveryCode: String,
  ): Result<Unit, SocialChallengeError> =
    coroutineBinding {
      val (serverPart, pakePart) = socialRecoveryCodeBuilder.parseRecoveryCode(recoveryCode)
        .mapError {
          when (it) {
            is SocialRecoveryCodeVersionError -> SocialChallengeError.ChallengeCodeVersionMismatch(cause = it)
            else -> SocialChallengeError.UnableToVerifyChallengeError(cause = it)
          }
        }.bind()
      val challengeResponse =
        socRecChallengeRepository.verifyChallenge(
          account = account,
          recoveryRelationshipId = recoveryRelationshipId,
          code = serverPart
        ).mapError {
          SocialChallengeError.UnableToVerifyChallengeError(cause = it)
        }.bind()

      val decryptPkekOutput = socRecCrypto.decryptPrivateKeyEncryptionKey(
        password = pakePart,
        protectedCustomerRecoveryPakeKey = challengeResponse.protectedCustomerRecoveryPakePubkey,
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey = XCiphertext(challengeResponse.sealedDek)
      ).mapError {
        SocialChallengeError.UnableToVerifyChallengeError(cause = it)
      }.bind()

      socRecChallengeRepository.respondToChallenge(
        account,
        challengeResponse.socialChallengeId,
        decryptPkekOutput.trustedContactRecoveryPakeKey,
        decryptPkekOutput.keyConfirmation,
        decryptPkekOutput.sealedPrivateKeyEncryptionKey
      ).mapError {
        SocialChallengeError.UnableToRespondToChallengeError(cause = it)
      }.bind()
    }
}
