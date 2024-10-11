package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.encrypt.XCiphertext
import build.wallet.relationships.RelationshipsCodeBuilder
import build.wallet.relationships.RelationshipsCodeVersionError
import build.wallet.relationships.RelationshipsCrypto
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class SocialChallengeVerifierImpl(
  private val socRecChallengeRepository: SocRecChallengeRepository,
  private val relationshipsCrypto: RelationshipsCrypto,
  private val relationshipsCodeBuilder: RelationshipsCodeBuilder,
) : SocialChallengeVerifier {
  override suspend fun verifyChallenge(
    account: Account,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    recoveryRelationshipId: String,
    recoveryCode: String,
  ): Result<Unit, SocialChallengeError> =
    coroutineBinding {
      val (serverPart, pakePart) = relationshipsCodeBuilder.parseRecoveryCode(recoveryCode)
        .mapError {
          when (it) {
            is RelationshipsCodeVersionError -> SocialChallengeError.ChallengeCodeVersionMismatch(cause = it)
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

      val decryptPkekOutput = relationshipsCrypto.decryptPrivateKeyEncryptionKey(
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
