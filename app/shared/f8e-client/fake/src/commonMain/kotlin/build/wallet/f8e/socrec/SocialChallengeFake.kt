package build.wallet.f8e.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.ChallengeAuthentication
import build.wallet.bitkey.relationships.ChallengeWrapper
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
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
        AppKey(
          PublicKey("fake-customer-recovery-key"),
          PrivateKey("fake-customer-recovery-private-key".encodeUtf8())
        )
      )
    )
  )
