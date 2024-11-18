package build.wallet.frost

import build.wallet.rust.core.PublicKey as FfiPublicKey

data class PublicKeyImpl(val ffiPublicKey: FfiPublicKey) : PublicKey
