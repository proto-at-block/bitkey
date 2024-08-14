package build.wallet.bitkey.relationships

import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [EndorsedTrustedContact].
 */
@Serializable
@JvmInline
@Redacted
value class TrustedContactAlias(val alias: String)
