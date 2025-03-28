package build.wallet.nfc

import build.wallet.bitkey.auth.AuthKeypair
import build.wallet.bitkey.auth.AuthPrivateKey
import build.wallet.bitkey.hardware.HwAuthPublicKey

data class FakeHwAuthKeypair(
  override val publicKey: HwAuthPublicKey,
  override val privateKey: AuthPrivateKey,
) : AuthKeypair
