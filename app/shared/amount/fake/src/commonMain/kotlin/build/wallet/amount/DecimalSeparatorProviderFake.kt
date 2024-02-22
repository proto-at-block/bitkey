package build.wallet.amount

class DecimalSeparatorProviderFake : DecimalSeparatorProvider {
  var decimalSeparator = '.'

  override fun decimalSeparator(): Char {
    return decimalSeparator
  }
}
