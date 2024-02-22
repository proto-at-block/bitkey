package build.wallet.recovery.socrec

import build.wallet.bitkey.keybox.LiteAccountMock
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SocialChallengeVerifierTests : FunSpec({

  val socialChallengeVerifier =
    SocialChallengeVerifierImpl(
      socRecChallengeRepository = SocRecChallengeRepositoryMock(),
      socRecCrypto = SocRecCryptoFake()
    )

  test("verify challenge with correct code") {
    val result =
      socialChallengeVerifier.verifyChallenge(
        account = LiteAccountMock,
        trustedContactIdentityKey = TrustedContactIdentityKeyFake,
        recoveryRelationshipId = "recovery-relationship-id",
        code = "123456"
      )

    result.get().shouldBe(Unit)
  }

  test("verify challenge with invalid code") {
    val result =
      socialChallengeVerifier.verifyChallenge(
        account = LiteAccountMock,
        trustedContactIdentityKey = TrustedContactIdentityKeyFake,
        recoveryRelationshipId = "recovery-relationship-id",
        code = "!123456"
      )

    result.getError()
      .shouldBeInstanceOf<SocialChallengeError.UnableToVerifyChallengeError>()
  }
})
