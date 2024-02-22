package build.wallet.bitkey.app

import build.wallet.bitkey.auth.AuthKeypair

sealed interface AppAuthKeypair : AuthKeypair {
  override val publicKey: AppAuthPublicKey
  override val privateKey: AppAuthPrivateKey
}
