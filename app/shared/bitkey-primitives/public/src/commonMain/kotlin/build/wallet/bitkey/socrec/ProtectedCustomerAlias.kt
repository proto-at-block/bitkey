package build.wallet.bitkey.socrec

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [ProtectedCustomer].
 */
@Serializable
@JvmInline
value class ProtectedCustomerAlias(val alias: String)
