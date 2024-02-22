package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBalance

val BdkBalanceMock =
  BdkBalance(
    immature = 0u,
    trustedPending = 0u,
    untrustedPending = 0u,
    confirmed = 0u,
    spendable = 0u,
    total = 0u
  )
