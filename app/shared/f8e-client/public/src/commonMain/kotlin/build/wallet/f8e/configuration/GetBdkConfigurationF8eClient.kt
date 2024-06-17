package build.wallet.f8e.configuration

import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetBdkConfigurationF8eClient {
  suspend fun getConfiguration(
    f8eEnvironment: F8eEnvironment,
  ): Result<ElectrumServers, NetworkingError>
}

data class ElectrumServers(
  val mainnet: ElectrumServer,
  val signet: ElectrumServer,
  val testnet: ElectrumServer,
  val regtest: ElectrumServer?,
)
