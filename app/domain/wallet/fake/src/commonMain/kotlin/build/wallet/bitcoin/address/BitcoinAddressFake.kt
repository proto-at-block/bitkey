package build.wallet.bitcoin.address

val bitcoinAddressP2PKH = BitcoinAddress("15e15hWo6CShMgbAfo8c2Ykj4C6BLq6Not")
val bitcoinAddressP2SH = BitcoinAddress("35PBEaofpUeH8VnnNSorM1QZsadrZoQp4N")
val bitcoinAddressP2WPKH = BitcoinAddress("bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc")
val bitcoinAddressP2TR = BitcoinAddress("bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs")
val signetAddressP2SH =
  BitcoinAddress("tb1pqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesf3hn0c")

val someBitcoinAddress = bitcoinAddressP2TR

const val bitcoinAddressInvalid = "tb1qj8jvlm5r67w6dwrlehlurk7et6vtdscwstm97nc6u"
