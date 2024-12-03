package build.wallet.partnerships

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Type-safe wrapper for a partner's string identifier.
 */
@Serializable
@JvmInline
value class PartnerId(val value: String)
