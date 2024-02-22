package build.wallet.nfc

import build.wallet.bitkey.auth.AuthKeypair
import build.wallet.bitkey.auth.AuthPrivateKey
import build.wallet.bitkey.auth.AuthPublicKey

data class FakeHwAuthKeypair(
  override val publicKey: AuthPublicKey,
  override val privateKey: AuthPrivateKey,
) : AuthKeypair
