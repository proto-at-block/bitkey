import Shared

// MARK: -

public extension Money {

    static func > (lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) > 0
    }

    static func == (lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) == 0
    }

    static func < (lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) < 0
    }

}

// MARK: -

public extension FiatCurrency {
    static let usd = CurrencyDefinitionsKt.USD
    static let gbp = CurrencyDefinitionsKt.GBP
    static let eur = CurrencyDefinitionsKt.EUR
}

public extension CryptoCurrency {
    static let btc = CurrencyDefinitionsKt.BTC
}
