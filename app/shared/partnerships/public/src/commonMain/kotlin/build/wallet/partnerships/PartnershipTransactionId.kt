package build.wallet.partnerships

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Identifier generated locally and used to identify an external partnership transaction.
 */
@Serializable
@JvmInline
value class PartnershipTransactionId(val value: String)
