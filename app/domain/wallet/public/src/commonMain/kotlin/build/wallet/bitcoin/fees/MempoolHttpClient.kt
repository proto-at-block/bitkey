package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import com.github.michaelbull.result.Result

/**
 * HTTP client for fetching Bitcoin fee rate estimates from mempool.space API.
 *
 * This client provides access to real-time Bitcoin transaction fee estimates by querying the
 * mempool.space API's `/api/v1/fees/recommended` endpoint. The API analyzes the current
 * Bitcoin mempool (the pool of unconfirmed transactions) to provide fee rate recommendations
 * for different confirmation time targets.
 *
 * ## Confirmation Time Targets
 *
 * The API provides fee rate estimates for different confirmation time targets:
 * - **Fastest**: For transactions that should be confirmed in the next block (highest fee rate)
 * - **30 minutes**: For transactions that should be confirmed within ~30 minutes (medium fee rate)
 * - **60 minutes**: For transactions that should be confirmed within ~60 minutes (lower fee rate)
 *
 * ## Network Support
 *
 * Supports multiple Bitcoin networks:
 * - **Bitcoin Mainnet**: Uses https://bitkey.mempool.space/
 * - **Testnet**: Uses https://bitkey.mempool.space/testnet/
 * - **Signet/Regtest**: Uses https://bitkey.mempool.space/signet/ (for lower rates during testing)
 */
interface MempoolHttpClient {
  /**
   * Fetches a single fee rate estimate for a specific transaction priority.
   *
   * This method queries the mempool.space API to get the current recommended fee rate
   * for transactions with the specified priority level.
   */
  suspend fun getMempoolFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error>

  /**
   * Fetches all available fee rate estimates for different transaction priorities.
   *
   * This method queries the mempool.space API to get a comprehensive breakdown of
   * fee rate estimates for all available priority levels. This is useful when you
   * need to present multiple fee options to users or when you want to cache all
   * fee rates with a single API call.
   *
   * The returned data includes fee rates for:
   * - Fastest confirmation (next block)
   * - 30-minute confirmation target
   * - 60-minute confirmation target
   */
  suspend fun getMempoolFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error>
}
