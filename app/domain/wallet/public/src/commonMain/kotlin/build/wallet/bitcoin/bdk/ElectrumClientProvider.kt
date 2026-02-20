package build.wallet.bitcoin.bdk

import uniffi.bdk.ElectrumClient

interface ElectrumClientProvider {
  fun <T> withClient(
    url: String,
    block: (ElectrumClient) -> T,
  ): T

  fun invalidate(url: String)
}
