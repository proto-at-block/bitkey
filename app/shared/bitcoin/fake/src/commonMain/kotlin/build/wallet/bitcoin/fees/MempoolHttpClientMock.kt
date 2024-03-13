package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import io.ktor.client.HttpClient

class MempoolHttpClientMock(
  val httpClient: HttpClient = HttpClient(),
) : MempoolHttpClient {
  override fun client(networkType: BitcoinNetworkType): HttpClient = httpClient
}
