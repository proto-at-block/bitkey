package build.wallet.f8e.configuration

import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetBdkConfigurationServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val deviceInfoProvider: DeviceInfoProvider,
) : GetBdkConfigurationService {
  override suspend fun getConfiguration(
    f8eEnvironment: F8eEnvironment,
  ): Result<ElectrumServers, NetworkingError> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<ResponseBody> {
        get("/api/bdk-configuration")
      }
      .map { response ->
        ElectrumServers(
          mainnet = response.electrumServers.mainnet.toElectrumServer(),
          signet = response.electrumServers.signet.toElectrumServer(),
          testnet = response.electrumServers.testnet.toElectrumServer(),
          regtest =
            response.electrumServers.regtest?.transformRegtestServerForAndroidEmulator(
              deviceInfoProvider
            )?.toElectrumServer()
        )
      }
      .logNetworkFailure { "Failed to get BDK configuration" }
  }

  @Serializable
  private data class ResponseBody(
    @SerialName("electrum_servers")
    val electrumServers: F8eElectrumServers,
  )

  @Serializable
  private data class F8eElectrumServers(
    val mainnet: ElectrumServerDetailsDTO,
    val signet: ElectrumServerDetailsDTO,
    val testnet: ElectrumServerDetailsDTO,
    val regtest: ElectrumServerDetailsDTO?,
  )

  @Serializable
  private data class ElectrumServerDetailsDTO(
    val host: String,
    val port: UInt,
    val scheme: String,
  )

  private fun ElectrumServerDetailsDTO.toElectrumServer(): ElectrumServer =
    F8eDefined(
      electrumServerDetails =
        ElectrumServerDetails(
          host = host,
          port = port.toString(10),
          protocol = scheme
        )
    )

  private fun ElectrumServerDetailsDTO.transformRegtestServerForAndroidEmulator(
    deviceInfoProvider: DeviceInfoProvider,
  ): ElectrumServerDetailsDTO {
    if (deviceInfoProvider.getDeviceInfo().let { it.isEmulator && it.devicePlatform == Android }) {
      // Android emulator puts the host device's localhost IP at 10.0.2.2
      // https://developer.android.com/studio/run/emulator-networking
      if (host == "127.0.0.1" || host == "localhost") {
        return copy(host = "10.0.2.2")
      }
    }
    return this
  }
}
