package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import com.github.michaelbull.result.Result

/**
 * HTTP client for interacting with Augur's Bitcoin fee estimation API.
 *
 * Augur is an open-source Bitcoin fee estimation library that provides more sophisticated
 * statistical modeling compared to traditional mempool-based estimates. It uses probabilistic
 * confidence-based estimates to predict transaction confirmation times.
 *
 * ## API Endpoint
 *
 * The client connects to Augur's fee estimation service at: https://pricing.bitcoin.block.xyz/fees
 *
 * ## Fee Rate Mapping Strategy
 *
 * The client maps our transaction priorities to Augur's probabilistic estimates as follows:
 *
 * | Priority | Augur API Mapping
 * |----------|-------------------|
 * | **FASTEST** | `three_blocks @ 95% confidence`
 * | **THIRTY_MINUTES** | `three_blocks @ 80% confidence`
 * | **SIXTY_MINUTES** | `six_blocks @ 80% confidence`
 *
 * ## Response Format
 *
 * Augur returns estimates for different block targets (3, 6, 9, 12, 18, 24, 36, 48, 72, 96, 144)
 * with probability distributions for each target:
 * - 5% confidence (very conservative)
 * - 20% confidence
 * - 50% confidence (median)
 * - 80% confidence (recommended)
 * - 95% confidence (high confidence)
 *
 * @see [Augur Blog Post](https://engineering.blockstaging.xyz/blog/augur-an-open-source-bitcoin-fee-estimation-library)
 */
interface AugurFeesHttpClient {
  /**
   * Retrieves a fee rate estimate for a specific transaction priority from Augur's API.
   *
   * This method fetches the current fee estimates from Augur and maps the requested priority
   * to the appropriate confidence level and block target combination.
   *
   * @param networkType The Bitcoin network type (determines which Augur endpoint to use)
   * @param estimatedTransactionPriority The desired confirmation priority/urgency
   * @return [Result] containing the estimated [FeeRate] in sats per vByte, or an [Error]
   *         if the API call fails or required data is missing from the response
   *
   * @see EstimatedTransactionPriority for available priority levels
   * @see FeeRate for the returned fee rate structure
   */
  suspend fun getAugurFeesFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error>

  /**
   * Retrieves fee rate estimates for all supported transaction priorities from Augur's API.
   *
   * This method fetches a complete set of fee estimates covering all priority levels,
   * which is more efficient than making individual calls when multiple estimates are needed.
   *
   * @param networkType The Bitcoin network type (determines which Augur endpoint to use)
   * @return [Result] containing [FeeRatesByPriority] with estimates for all priority levels,
   *         or an [Error] if the API call fails or any required data is missing
   *
   * @see FeeRatesByPriority for the returned structure containing all fee rates
   */
  suspend fun getAugurFeesFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error>
}
