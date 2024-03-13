package build.wallet.bitkey.hardware

import build.wallet.bitkey.auth.AuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import kotlinx.serialization.Serializable

/**
 * Authentication private key for the hardware factor.
 */
@Serializable
data class HwAuthPublicKey(override val pubKey: Secp256k1PublicKey) : AuthPublicKey
