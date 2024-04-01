package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ChallengeAuthentication
import build.wallet.bitkey.socrec.ChallengeWrapper
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.SocialRecoveryService
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.recovery.socrec.RecoveryCodeParts.Schema
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.collections.immutable.ImmutableList
import okio.ByteString

class SocRecChallengeRepositoryImpl(
  private val socRec: SocialRecoveryService,
  private val socRecCrypto: SocRecCrypto,
  private val socRecFake: SocialRecoveryService,
  private val socRecCodeBuilder: SocialRecoveryCodeBuilder,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
  private val socRecStartedChallengeAuthenticationDao: SocRecStartedChallengeAuthenticationDao,
) : SocRecChallengeRepository {
  override suspend fun startChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    trustedContacts: ImmutableList<TrustedContact>,
    sealedDekMap: Map<String, XCiphertext>,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper, Error> =
    binding {
      // clear auth table on each new challenge
      socRecStartedChallengeAuthenticationDao.clear()

      // for each trusted contact...
      val startSocialChallengeTcs = trustedContacts.map { trustedContact ->
        // generate a pake code
        val pakeCode = Schema.maskPakeData(SecureRandom().nextBytes(Schema.pakeByteArraySize()))
        // and a ProtectedCustomerRecoveryPakeKey
        val protectedCustomerRecoveryPakeKey =
          socRecCrypto.generateProtectedCustomerRecoveryPakeKey(pakeCode)
            .bind()
        // insert them into the db
        socRecStartedChallengeAuthenticationDao.insert(
          recoveryRelationshipId = trustedContact.recoveryRelationshipId,
          protectedCustomerRecoveryPakeKey = protectedCustomerRecoveryPakeKey,
          pakeCode = pakeCode
        ).mapError { Error(it) }
          .bind()
        // get the sealed dek for this trusted contact
        sealedDekMap[trustedContact.recoveryRelationshipId]?.let {
          StartSocialChallengeRequestTrustedContact(
            trustedContact.recoveryRelationshipId,
            protectedCustomerRecoveryPakeKey.publicKey,
            it.value
          )
        }
      }
      getSocialRecoveryService(isUsingSocRecFakes).startChallenge(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = accountId,
        trustedContacts = startSocialChallengeTcs.filterNotNull()
      ).map {
        getChallengeById(
          it.challengeId,
          accountId,
          f8eEnvironment,
          isUsingSocRecFakes
        ).bind()
      }.bind()
        .also {
          socRecStartedChallengeDao.set(it.challenge.challengeId).bind()
        }
    }

  override suspend fun getCurrentChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper?, Error> {
    return socRecStartedChallengeDao.get()
      .flatMap { challengeId ->
        if (challengeId != null) {
          getChallengeById(
            challengeId = challengeId,
            accountId = accountId,
            f8eEnvironment = f8eEnvironment,
            isUsingSocRecFakes = isUsingSocRecFakes
          )
        } else {
          Ok(null)
        }
      }
  }

  override suspend fun getChallengeById(
    challengeId: String,
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper, Error> =
    binding {
      val socialChallenge = getSocialRecoveryService(isUsingSocRecFakes).getSocialChallengeStatus(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = accountId,
        challengeId = challengeId
      ).bind()

      val tcAuths = socRecStartedChallengeAuthenticationDao.getAll()
        .mapError { Error(it) }
        .bind()
        .map {
          val pakeCode = it.pakeCode
          val fullCode = socRecCodeBuilder.buildRecoveryCode(socialChallenge.counter, pakeCode)
            .mapError { genErr -> Error(genErr) }
            .bind()
          ChallengeAuthentication(
            it.relationshipId,
            fullCode,
            pakeCode,
            it.protectedCustomerRecoveryPakeKey
          )
        }

      ChallengeWrapper(socialChallenge, tcAuths)
    }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: Int,
  ): Result<ChallengeVerificationResponse, Error> {
    return getSocialRecoveryService(account.config.isUsingSocRecFakes).verifyChallenge(
      account = account,
      recoveryRelationshipId = recoveryRelationshipId,
      counter = code
    )
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, Error> {
    return getSocialRecoveryService(account.config.isUsingSocRecFakes).respondToChallenge(
      account,
      socialChallengeId,
      trustedContactRecoveryPakePubkey,
      recoveryPakeConfirmation,
      resealedDek
    )
  }

  private fun getSocialRecoveryService(isUsingSocRecFakes: Boolean): SocialRecoveryService {
    return if (isUsingSocRecFakes) {
      socRecFake
    } else {
      socRec
    }
  }
}
