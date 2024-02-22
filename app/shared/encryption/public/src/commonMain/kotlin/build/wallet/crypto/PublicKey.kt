package build.wallet.crypto

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A serialized public key value.
 */
@Serializable
@JvmInline
value class PublicKey(val value: String)
