package build.wallet.recovery.sweep

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.logging.logFailure
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.queueprocessor.process
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf

@BitkeyInject(AppScope::class)
class SweepGeneratorImpl(
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val keysetWalletProvider: KeysetWalletProvider,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val registerWatchAddressProcessor: RegisterWatchAddressProcessor,
) : SweepGenerator {
  override suspend fun generateSweep(
    keybox: Keybox,
  ): Result<List<SweepPsbt>, SweepGeneratorError> =
    coroutineBinding {
      // Fetch the full list of known spending keysets for this account from f8e
      val serverKeysets =
        listKeysetsF8eClient.listKeysets(
          keybox.config.f8eEnvironment,
          keybox.fullAccountId
        )
          .mapError { FailedToListKeysets }
          .logFailure { "Error fetching keysets for an account when generating sweep." }
          .bind()
          .keysets

      // The active hw key's dpub contains the master key fingerprint for all
      // keys generated by the hw.
      val hardwareMasterKeyFingerprint =
        keybox.activeSpendingKeyset.hardwareKey.key.origin.fingerprint

      // Find the list of keysets we can sign for using either App or Hardware
      val signableKeysets =
        serverKeysets
          .filter { it.f8eSpendingKeyset.keysetId != keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId }
          .mapNotNull {
            val isAppSignable =
              isAppSignable(it)
                .mapError(::AppPrivateKeyMissing)
                .bind()
            when {
              isAppSignable -> SignableKeyset(it, App)
              isHardwareSignable(hardwareMasterKeyFingerprint, it) ->
                SignableKeyset(
                  it,
                  Hardware
                )

              else -> null
            }
          }

      val feeRate =
        bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
          networkType = keybox.config.bitcoinNetworkType,
          estimatedTransactionPriority = EstimatedTransactionPriority.sweepPriority()
        )

      buildList<SweepPsbt> {
        signableKeysets.forEach { keyset ->
          // Generate the sweep psbt(s), failing fast on the first non-recoverable error
          buildPsbt(keyset, keybox.activeSpendingKeyset, feeRate, keybox).bind()
            ?.let { psbt -> add(psbt) }
        }
      }
    }.logFailure { "Error generating sweep psbts" }

  private suspend fun isAppSignable(keyset: SpendingKeyset): Result<Boolean, Throwable> {
    return appPrivateKeyDao.getAppSpendingPrivateKey(keyset.appKey).map { it != null }
  }

  private fun isHardwareSignable(
    hardwareMasterKeyFingerPrint: String,
    keyset: SpendingKeyset,
  ): Boolean {
    return hardwareMasterKeyFingerPrint == keyset.hardwareKey.key.origin.fingerprint
  }

  private suspend fun buildPsbt(
    signableKeyset: SignableKeyset,
    destinationKeyset: SpendingKeyset,
    feeRate: FeeRate,
    keybox: Keybox,
  ): Result<SweepPsbt?, SweepGeneratorError> =
    coroutineBinding {
      val destinationWallet =
        keysetWalletProvider
          .getWatchingWallet(destinationKeyset)
          .mapError(::ErrorCreatingWallet)
          .bind()

      // Get a new address for every keyset for anonymity
      val destinationAddress =
        destinationWallet
          .getNewAddress()
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()

      // don't bind on process the address, if this fails we still want the sweep to continue
      registerWatchAddressProcessor.process(
        RegisterWatchAddressContext(
          address = destinationAddress,
          f8eSpendingKeyset = destinationKeyset.f8eSpendingKeyset,
          accountId = keybox.fullAccountId.serverId,
          f8eEnvironment = keybox.config.f8eEnvironment
        )
      ).logFailure { "Error registering address with f8e" }

      destinationWallet
        .sync()
        .mapError(::ErrorCreatingWallet)
        .bind()

      val wallet =
        keysetWalletProvider
          .getWatchingWallet(signableKeyset.keyset)
          .mapError(::ErrorCreatingWallet)
          .bind()

      wallet
        .sync()
        .mapError {
          ErrorSyncingSpendingWallet(it)
        }
        .bind()

      wallet
        .createPsbt(
          recipientAddress = destinationAddress,
          amount = BitcoinTransactionSendAmount.SendAll,
          feePolicy = FeePolicy.Rate(feeRate)
        )
        .map { SweepPsbt(it, signableKeyset.signingFactor, signableKeyset.keyset) }
        // Return null if the wallet doesn't have enough funds to sweep.
        .recoverIf(
          predicate = { it is BdkError.InsufficientFunds },
          transform = { null }
        )
        .mapError { BdkFailedToCreatePsbt(it, signableKeyset.keyset) }
        .bind()
    }

  private data class SignableKeyset(
    val keyset: SpendingKeyset,
    val signingFactor: PhysicalFactor,
  )
}
