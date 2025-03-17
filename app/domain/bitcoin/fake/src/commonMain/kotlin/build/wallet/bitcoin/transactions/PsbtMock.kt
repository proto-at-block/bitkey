package build.wallet.bitcoin.transactions

import build.wallet.money.BitcoinMoney

val PsbtMock =
  Psbt(
    id = "psbt-id",
    base64 = "some-base-64",
    fee = BitcoinMoney.sats(10_000),
    baseSize = 10000,
    numOfInputs = 1,
    amountSats = 10000UL
  )
