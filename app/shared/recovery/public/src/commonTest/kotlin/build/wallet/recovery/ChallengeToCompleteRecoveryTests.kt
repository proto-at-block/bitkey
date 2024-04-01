package build.wallet.recovery

import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class ChallengeToCompleteRecoveryTests : FunSpec({
  test("generate challenge from keys") {
    ChallengeToCompleteRecovery(
      app = PublicKey("1111"),
      recovery = PublicKey("3333"),
      hw = HwAuthPublicKey(Secp256k1PublicKey("2222"))
    ).bytes.shouldBe("CompleteDelayNotify222211113333".encodeUtf8())
  }
})
