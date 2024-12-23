package build.wallet.bitcoin.blockchain

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.logging.logTesting
import build.wallet.realDelay
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.withRealTimeout
import com.github.michaelbull.result.getOrElse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
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
    logTesting { "RegtestControl: Mined $numBlock blocks to ${mineToAddress.address}" }
  }

  override suspend fun mineBlock(
    txid: String,
    mineToAddress: BitcoinAddress,
  ) {
    waitForTxInMempool(txid)
    logTesting { "RegtestControl: Found tx $txid in Mempool. Mining block to ${mineToAddress.address}" }
    val blocks = withContext(Dispatchers.IO) {
      client.generateToAddress(1, mineToAddress.address)
    }
    waitForIndexing(blocks.last())
    logTesting { "RegtestControl: Mined blocks to ${mineToAddress.address}" }
  }

  private suspend fun waitForTxInMempool(txid: String) {
    val url = "$electrumHttpApiUrl/mempool/txids"
    withContext(Dispatchers.Default) {
      withRealTimeout(5.seconds) {
        while (isActive) {
          val resp = HttpClient().get(url)
          // Response is just an array of txid strings.
          val txids = Json.decodeFromStringResult<List<String>>(resp.bodyAsText()).getOrElse { emptyList() }

          if (txids.contains(txid)) {
            return@withRealTimeout
          } else {
            realDelay(500.milliseconds)
          }
        }
      }
    }
  }

  private suspend fun waitForIndexing(blockHash: String) {
    val url = "$electrumHttpApiUrl/block/$blockHash"
    withContext(Dispatchers.Default) {
      withRealTimeout(10.seconds) {
        while (isActive) {
          val resp = HttpClient().get(url)
          when (resp.status) {
            HttpStatusCode.OK -> return@withRealTimeout
            HttpStatusCode.NotFound -> realDelay(500.milliseconds)
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
