package build.wallet.bitkey.socrec

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [EndorsedTrustedContact].
 */
@Serializable
@JvmInline
value class TrustedContactAlias(val alias: String)
