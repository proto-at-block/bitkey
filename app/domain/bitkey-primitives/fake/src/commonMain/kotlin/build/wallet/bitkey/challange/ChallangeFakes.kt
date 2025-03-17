package build.wallet.bitkey.challange

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock

val DelayNotifyRecoveryChallengeFake = DelayNotifyChallenge.fromParts(
  type = DelayNotifyChallenge.Type.RECOVERY,
  app = AppGlobalAuthPublicKeyMock,
  recovery = AppRecoveryAuthPublicKeyMock,
  hw = HwAuthPublicKeyMock
)

val DelayNotifyInheritanceChallengeFake = DelayNotifyChallenge.fromParts(
  type = DelayNotifyChallenge.Type.INHERITANCE,
  app = AppGlobalAuthPublicKeyMock,
  recovery = AppRecoveryAuthPublicKeyMock,
  hw = HwAuthPublicKeyMock
)
