package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import io.ktor.client.HttpClient

interface MempoolHttpClient {
  fun client(networkType: BitcoinNetworkType): HttpClient
}
