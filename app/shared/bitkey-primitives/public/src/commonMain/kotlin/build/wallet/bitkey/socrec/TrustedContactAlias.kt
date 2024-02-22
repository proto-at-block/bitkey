package build.wallet.bitkey.socrec

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [TrustedContact].
 */
@Serializable
@JvmInline
value class TrustedContactAlias(val alias: String)
