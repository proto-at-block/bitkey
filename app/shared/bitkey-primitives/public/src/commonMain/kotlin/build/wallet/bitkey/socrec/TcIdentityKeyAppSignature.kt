package build.wallet.bitkey.socrec

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * [DelegatedDecryptionKey] signed with [AppGlobalAuthPublicKey]'s private key.
 */
@JvmInline
@Serializable
value class TcIdentityKeyAppSignature(val value: String)
