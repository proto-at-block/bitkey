package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.time.someInstant
import kotlin.time.Duration.Companion.hours

val LostHardwareServerRecoveryMock =
  ServerRecovery(
    fullAccountId = FullAccountId("account-id"),
    delayStartTime = someInstant,
    delayEndTime = someInstant + 2.hours,
    lostFactor = Hardware,
    destinationAppGlobalAuthPubKey =
      AppGlobalAuthPublicKey(
        Secp256k1PublicKey("lost-hardware-recovery-app-auth-pubKey")
      ),
    destinationAppRecoveryAuthPubKey =
      AppRecoveryAuthPublicKey(
        Secp256k1PublicKey("lost-hardware-recovery-app-recovery-auth-pubKey")
      ),
    destinationHardwareAuthPubKey =
      HwAuthPublicKey(
        Secp256k1PublicKey("lost-hardware-recovery-hardware-auth-pubKey")
      )
  )
