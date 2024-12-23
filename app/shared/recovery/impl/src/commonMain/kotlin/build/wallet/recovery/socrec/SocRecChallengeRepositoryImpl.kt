package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.ChallengeAuthentication
import build.wallet.bitkey.relationships.ChallengeWrapper
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.SocRecF8eClient
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.relationships.RecoveryCodeParts.Schema
import build.wallet.relationships.RelationshipsCodeBuilder
import build.wallet.relationships.RelationshipsCrypto
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.ImmutableList
import okio.ByteString

@BitkeyInject(AppScope::class)
class SocRecChallengeRepositoryImpl(
  @Impl private val socRecF8eClientImpl: SocRecF8eClient,
  private val relationshipsCrypto: RelationshipsCrypto,
  @Fake private val socRecF8eClientFake: SocRecF8eClient,
  private val relationshipsCodeBuilder: RelationshipsCodeBuilder,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
  private val socRecStartedChallengeAuthenticationDao: SocRecStartedChallengeAuthenticationDao,
) : SocRecChallengeRepository {
  override suspend fun startChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsedTrustedContacts: ImmutableList<EndorsedTrustedContact>,
    sealedDekMap: Map<String, XCiphertext>,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper, Error> =
    coroutineBinding {
      // clear auth table on each new challenge
      socRecStartedChallengeAuthenticationDao.clear()

      // for each trusted contact...
      val startSocialChallengeTcs = endorsedTrustedContacts.map { trustedContact ->
        // generate a pake code
        val pakeCode = Schema.maskPakeData(SecureRandom().nextBytes(Schema.pakeByteArraySize()))
        // and a ProtectedCustomerRecoveryPakeKey
        val protectedCustomerRecoveryPakeKey =
          relationshipsCrypto.generateProtectedCustomerRecoveryPakeKey(pakeCode)
            .bind()
        // insert them into the db
        socRecStartedChallengeAuthenticationDao.insert(
          recoveryRelationshipId = trustedContact.relationshipId,
          protectedCustomerRecoveryPakeKey = protectedCustomerRecoveryPakeKey,
          pakeCode = pakeCode
        ).mapError { Error(it) }
          .bind()
        // get the sealed dek for this trusted contact
        sealedDekMap[trustedContact.relationshipId]?.let {
          StartSocialChallengeRequestTrustedContact(
            trustedContact.relationshipId,
            protectedCustomerRecoveryPakeKey.publicKey,
            it.value
          )
        }
      }
      socRecF8eClient(isUsingSocRecFakes).startChallenge(
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
    coroutineBinding {
      val socialChallenge = socRecF8eClient(isUsingSocRecFakes).getSocialChallengeStatus(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = accountId,
        challengeId = challengeId
      ).bind()

      val tcAuths = socRecStartedChallengeAuthenticationDao.getAll()
        .mapError { Error(it) }
        .bind()
        .map {
          val pakeCode = it.pakeCode
          val fullCode =
            relationshipsCodeBuilder.buildRecoveryCode(socialChallenge.counter, pakeCode)
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
    return socRecF8eClient(account.config.isUsingSocRecFakes).verifyChallenge(
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
    return socRecF8eClient(account.config.isUsingSocRecFakes).respondToChallenge(
      account,
      socialChallengeId,
      trustedContactRecoveryPakePubkey,
      recoveryPakeConfirmation,
      resealedDek
    )
  }

  private fun socRecF8eClient(isUsingSocRecFakes: Boolean): SocRecF8eClient {
    return if (isUsingSocRecFakes) {
      socRecF8eClientFake
    } else {
      socRecF8eClientImpl
    }
  }
}
