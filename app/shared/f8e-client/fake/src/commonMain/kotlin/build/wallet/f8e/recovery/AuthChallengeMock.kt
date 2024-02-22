package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge

val AuthChallengeMock =
  AuthChallenge(
    fullAccountId = FullAccountId("auth-challenge-mock-account-id"),
    challenge = "auth-challenge-challenge",
    session = "auth-challenge-session"
  )
