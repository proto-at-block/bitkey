package build.wallet.recovery

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.time.someInstant
import kotlin.time.Duration.Companion.hours

val StillRecoveringInitiatedRecoveryMock =
  StillRecovering.ServerDependentRecovery.InitiatedRecovery(
    fullAccountId = FullAccountId("account-id"),
    appSpendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-spending-key")),
    appGlobalAuthKey = AppGlobalAuthPublicKey(Secp256k1PublicKey("app-auth-key")),
    appRecoveryAuthKey = AppRecoveryAuthPublicKeyMock,
    hardwareSpendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hardware-spending-key")),
    hardwareAuthKey = HwAuthPublicKey(Secp256k1PublicKey("hardware-auth-key")),
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    factorToRecover = App,
    serverRecovery =
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
            Secp256k1PublicKey("lost-hardware-recovery-app-auth-pubKey")
          ),
        destinationHardwareAuthPubKey =
          HwAuthPublicKey(
            Secp256k1PublicKey("lost-hardware-recovery-hardware-auth-pubKey")
          )
      )
  )
