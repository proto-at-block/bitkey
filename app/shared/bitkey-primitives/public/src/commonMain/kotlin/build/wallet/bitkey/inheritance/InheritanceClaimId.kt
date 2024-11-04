package build.wallet.bitkey.inheritance

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Identifier for an inheritance claim after it has been started.
 */
@JvmInline
@Serializable
value class InheritanceClaimId(val value: String)
