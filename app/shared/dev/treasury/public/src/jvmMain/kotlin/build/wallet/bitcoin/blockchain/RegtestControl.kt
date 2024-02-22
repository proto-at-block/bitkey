package build.wallet.bitcoin.blockchain

import build.wallet.bitcoin.address.BitcoinAddress
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A test blockchain that runs on regtest.
 *
 * @param client A bitcoin RPC client configured to talk to a regtest bitcoin node
 * @param miningWallet Wallet address that will receive all mined bitcoins
 */
class RegtestControl(
  private val client: BitcoinJSONRPCClient,
  private val electrumHttpApiUrl: String,
) : BlockchainControl {
  /**
   * Generate the requested number of blocks and wait for the blocks to be indexed by the electrum
   * server. The mined bitcoin is sent to [miningWallet] and available after 100 blocks.
   *
   * @param mineToAddress The address to send mined bitcoin to.
   * @param numBlock Number of blocks to generatek
   * @return Block hash of the newest block
   */
  override suspend fun mineBlocks(
    numBlock: Int,
    mineToAddress: BitcoinAddress,
  ) {
    require(numBlock > 0)
    val blocks =
      withContext(Dispatchers.IO) {
        client.generateToAddress(numBlock, mineToAddress.address)
      }
    waitForIndexing(blocks.last())
    println("Mined $numBlock blocks to ${mineToAddress.address}")
  }

  private suspend fun waitForIndexing(blockHash: String) {
    val url = "$electrumHttpApiUrl/block/$blockHash"
    withContext(Dispatchers.Default) {
      withTimeout(10.seconds) {
        while (true) {
          val resp = HttpClient().get(url)
          when (resp.status) {
            HttpStatusCode.OK -> return@withTimeout
            HttpStatusCode.NotFound -> delay(500.milliseconds)
            else ->
              error(
                "error calling Electrum API $url: code=${resp.status}: ${resp.bodyAsText()}"
              )
          }
        }
      }
    }
  }

  companion object {
    fun create(
      bitcoindDomain: String,
      bitcoindUser: String,
      bitcoindPassword: String,
      electrumHttpApiUrl: String,
    ): RegtestControl {
      val client = BitcoinJSONRPCClient("http://$bitcoindUser:$bitcoindPassword@$bitcoindDomain")
      return RegtestControl(client, electrumHttpApiUrl)
    }
  }
}
