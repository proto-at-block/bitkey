package build.wallet.f8e.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.ChallengeAuthentication
import build.wallet.bitkey.socrec.ChallengeWrapper
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocialChallenge
import okio.ByteString.Companion.encodeUtf8

val SocialChallengeFake =
  ChallengeWrapper(
    SocialChallenge(
      challengeId = "fake-challenge-id",
      counter = 123456,
      responses = emptyList()
    ),
    listOf(
      ChallengeAuthentication(
        "fake-relationship-id",
        "123456",
        PakeCode("12345678901".encodeUtf8()),
        ProtectedCustomerRecoveryPakeKey(
          AppKey.Companion.fromPublicKey("fake-customer-recovery-key")
        )
      )
    )
  )
