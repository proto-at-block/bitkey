package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.SocialChallenge

val SocialChallengeFake =
  SocialChallenge(
    challengeId = "fake-challenge-id",
    code = "123456",
    responses = emptyList()
  )
