package build.wallet.money.currency.code

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Strong type for the ISO 4217 text code of a currency
 */
@Serializable
@JvmInline
value class IsoCurrencyTextCode(val code: String)
