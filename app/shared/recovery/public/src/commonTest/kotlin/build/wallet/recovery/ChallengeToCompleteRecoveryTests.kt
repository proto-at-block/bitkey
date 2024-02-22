package build.wallet.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class ChallengeToCompleteRecoveryTests : FunSpec({
  test("generate challenge from keys") {
    ChallengeToCompleteRecovery(
      app = AppGlobalAuthPublicKey(Secp256k1PublicKey("1111")),
      recovery = AppRecoveryAuthPublicKey(Secp256k1PublicKey("3333")),
      hw = HwAuthPublicKey(Secp256k1PublicKey("2222"))
    ).bytes.shouldBe("CompleteDelayNotify222211113333".encodeUtf8())
  }
})
