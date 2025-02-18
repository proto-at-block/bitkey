package build.wallet.bitkey.promotions

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Type-safe wrapper for a promotion code.
 */
@Serializable
@JvmInline
value class PromotionCode(val value: String)
