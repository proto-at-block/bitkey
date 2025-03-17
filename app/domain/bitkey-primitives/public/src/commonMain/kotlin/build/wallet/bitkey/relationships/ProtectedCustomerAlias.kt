package build.wallet.bitkey.relationships

import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [ProtectedCustomer].
 */
@Serializable
@JvmInline
@Redacted
value class ProtectedCustomerAlias(val alias: String)
