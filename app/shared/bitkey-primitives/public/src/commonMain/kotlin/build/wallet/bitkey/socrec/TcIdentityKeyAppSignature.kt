package build.wallet.bitkey.socrec

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * [DelegatedDecryptionKey] signed with [AppGlobalAuthPublicKey]'s private key.
 */
@JvmInline
@Serializable
value class TcIdentityKeyAppSignature(val value: String)
