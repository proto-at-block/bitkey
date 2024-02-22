import Shared

// MARK: -

extension Money {

    public static func >(lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) > 0
    }

    public static func ==(lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) == 0
    }

    public static func <(lhs: Money, rhs: Money) -> Bool {
        return lhs.compareTo(other_: rhs) < 0
    }

}

// MARK: -

extension FiatCurrency {
    public static let usd = CurrencyDefinitionsKt.USD
    public static let gbp = CurrencyDefinitionsKt.GBP
    public static let eur = CurrencyDefinitionsKt.EUR
}

extension CryptoCurrency {
    public static let btc = CurrencyDefinitionsKt.BTC
}
