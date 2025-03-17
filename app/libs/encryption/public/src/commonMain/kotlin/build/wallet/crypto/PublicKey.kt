package build.wallet.crypto

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A serialized public key value.
 */
@Serializable
@JvmInline
@Suppress("Unused")
value class PublicKey<T : KeyPurpose>(val value: String)
