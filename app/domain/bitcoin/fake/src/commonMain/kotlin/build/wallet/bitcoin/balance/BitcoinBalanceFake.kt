package build.wallet.bitcoin.balance

import build.wallet.money.BitcoinMoney

fun BitcoinBalanceFake(
  immature: BitcoinMoney = BitcoinMoney.zero(),
  trustedPending: BitcoinMoney = BitcoinMoney.zero(),
  untrustedPending: BitcoinMoney = BitcoinMoney.zero(),
  confirmed: BitcoinMoney = BitcoinMoney.zero(),
) = BitcoinBalance(
  immature = immature,
  trustedPending = trustedPending,
  untrustedPending = untrustedPending,
  confirmed = confirmed,
  spendable = trustedPending + confirmed,
  total = immature + trustedPending + untrustedPending + confirmed
)

val BitcoinBalanceFake =
  BitcoinBalanceFake(
    immature = BitcoinMoney.zero(),
    trustedPending = BitcoinMoney.zero(),
    untrustedPending = BitcoinMoney.zero(),
    confirmed = BitcoinMoney.sats(100_000)
  )
