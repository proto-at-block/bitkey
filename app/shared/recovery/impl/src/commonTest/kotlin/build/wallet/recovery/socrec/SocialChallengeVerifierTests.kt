package build.wallet.recovery.socrec

import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.relationships.DelegatedDecryptionKeyFake
import build.wallet.relationships.RelationshipsCodeBuilderFake
import build.wallet.relationships.RelationshipsCryptoFake
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SocialChallengeVerifierTests : FunSpec({

  val relationshipsCrypto = RelationshipsCryptoFake()
  val socialChallengeVerifier =
    SocialChallengeVerifierImpl(
      socRecChallengeRepository = SocRecChallengeRepositoryMock(),
      relationshipsCrypto = relationshipsCrypto,
      relationshipsCodeBuilder = RelationshipsCodeBuilderFake()
    )

  test("verify challenge with correct code") {
    val result =
      socialChallengeVerifier.verifyChallenge(
        account = LiteAccountMock,
        delegatedDecryptionKey = DelegatedDecryptionKeyFake,
        recoveryRelationshipId = "recovery-relationship-id",
        recoveryCode = "ABCDEF,123456"
      )

    result.get().shouldBe(Unit)
  }

  test("verify challenge with invalid code") {
    val result =
      socialChallengeVerifier.verifyChallenge(
        account = LiteAccountMock,
        delegatedDecryptionKey = DelegatedDecryptionKeyFake,
        recoveryRelationshipId = "recovery-relationship-id",
        recoveryCode = "!123456,__"
      )

    result.getError()
      .shouldBeInstanceOf<SocialChallengeError.UnableToVerifyChallengeError>()
  }
})
