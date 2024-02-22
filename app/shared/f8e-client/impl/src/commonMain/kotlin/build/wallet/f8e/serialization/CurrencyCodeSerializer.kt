package build.wallet.f8e.serialization

import build.wallet.money.currency.Currency

internal fun Currency.toJsonString() = textCode.code
