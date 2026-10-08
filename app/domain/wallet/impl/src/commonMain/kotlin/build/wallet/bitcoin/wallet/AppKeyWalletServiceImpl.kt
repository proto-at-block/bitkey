package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.coroutines.withTimeoutResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class AppKeyWalletServiceImpl(
  private val appKeyWalletProvider: AppKeyWalletProvider,
  private val bitcoinAddressService: BitcoinAddressService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
) : AppKeyWalletService {
  private val operationLock = Mutex()
  private var activeOperation: AppKeyWalletSweep? = null

  override suspend fun generateAddressInfo(keyset: SpendingKeyset) = coroutineBinding {
    wallet(keyset).bind().getNewAddressInfo()
      .mapError(AppKeyWalletServiceError::WalletCreationFailed)
      .bind()
  }

  override suspend fun fullScan(keyset: SpendingKeyset) =
    coroutineBinding {
      val wallet = wallet(keyset).bind()
      fullScan(wallet).bind()
      val balance = wallet.balance().first()
      AppKeyWalletBalance(balance.total.sats(), balance.spendable.sats())
    }

  override suspend fun prepareSweep(keyset: SpendingKeyset) =
    coroutineBinding {
      val sourceWallet = wallet(keyset).bind()
      fullScan(sourceWallet).bind()
      val balance = sourceWallet.balance().first().spendable
      if (balance == BitcoinMoney.zero()) Err(AppKeyWalletServiceError.NoSpendableFunds()).bind()
      val destination = bitcoinAddressService.generateAddress()
        .mapError(AppKeyWalletServiceError::DestinationFailed).bind()
      validateDestination(destination, keyset.networkType).bind()
      val feeRate = bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
        keyset.networkType, EstimatedTransactionPriority.sweepPriority()
      )
      val psbt = sourceWallet.createPsbt(
        destination, BitcoinTransactionSendAmount.SendAll, FeePolicy.Rate(feeRate)
      ).mapError(AppKeyWalletServiceError::PrepareFailed).bind()
      validatePsbt(psbt, balance, destination).bind()
      val id = AppKeyWalletSweepId(psbt.id)
      operationLock.withLock {
        activeOperation = AppKeyWalletSweep.Prepared(
          id = id,
          keysetId = keyset.localId,
          networkType = keyset.networkType,
          destination = destination,
          sourceBalance = balance,
          psbt = psbt
        )
      }
      AppKeyWalletSweepPreview(id, balance, BitcoinMoney.sats(psbt.amountSats), psbt.fee.amount)
    }

  override suspend fun signSweep(
    keyset: SpendingKeyset,
    previewId: AppKeyWalletSweepId,
  ) = coroutineBinding {
    val operation = preparedOperation(previewId, keyset).bind()
    validateDestination(operation.destination, keyset.networkType).bind()
    validatePsbt(operation.psbt, operation.sourceBalance, operation.destination).bind()
    val sourceWallet = wallet(keyset).mapError(AppKeyWalletServiceError::SignFailed).bind()
    fullScan(sourceWallet).mapError(AppKeyWalletServiceError::SignFailed).bind()
    val currentBalance = sourceWallet.balance().first().spendable
    if (currentBalance != operation.sourceBalance) Err(AppKeyWalletServiceError.StalePreview()).bind()
    val signed = sourceWallet.signPsbt(operation.psbt)
      .mapError(AppKeyWalletServiceError::SignFailed).bind()
    operationLock.withLock {
      if (activeOperation?.id == previewId) {
        activeOperation = AppKeyWalletSweep.Signed(
          id = operation.id,
          keysetId = operation.keysetId,
          networkType = operation.networkType,
          destination = operation.destination,
          sourceBalance = operation.sourceBalance,
          psbt = signed
        )
      }
    }
  }

  override suspend fun broadcastSweep(
    keyset: SpendingKeyset,
    previewId: AppKeyWalletSweepId,
  ) = coroutineBinding {
    val operation = signedOperation(previewId, keyset).bind()
    validateDestination(operation.destination, keyset.networkType).bind()
    validatePsbt(operation.psbt, operation.sourceBalance, operation.destination).bind()
    val detail = bitcoinWalletService.broadcast(operation.psbt, EstimatedTransactionPriority.sweepPriority())
      .mapError(AppKeyWalletServiceError::BroadcastFailed).bind()
    operationLock.withLock {
      if (activeOperation?.id == previewId) {
        activeOperation = null
      }
    }
    AppKeyWalletSweepResult(detail.transactionId)
  }

  private suspend fun preparedOperation(
    id: AppKeyWalletSweepId,
    keyset: SpendingKeyset,
  ): Result<AppKeyWalletSweep.Prepared, AppKeyWalletServiceError> =
    operationLock.withLock {
      when (val operation = activeOperation) {
        is AppKeyWalletSweep.Prepared ->
          operation
            .takeIf { it.matches(id, keyset) }
            ?.let(::Ok)
            ?: Err(AppKeyWalletServiceError.StalePreview())
        else -> Err(AppKeyWalletServiceError.StalePreview())
      }
    }

  private suspend fun signedOperation(
    id: AppKeyWalletSweepId,
    keyset: SpendingKeyset,
  ): Result<AppKeyWalletSweep.Signed, AppKeyWalletServiceError> =
    operationLock.withLock {
      when (val operation = activeOperation) {
        is AppKeyWalletSweep.Signed ->
          operation
            .takeIf { it.matches(id, keyset) }
            ?.let(::Ok)
            ?: Err(AppKeyWalletServiceError.StalePreview())
        else -> Err(AppKeyWalletServiceError.StalePreview())
      }
    }

  private suspend fun validateDestination(
    destination: BitcoinAddress,
    networkType: BitcoinNetworkType,
  ): Result<Unit, AppKeyWalletServiceError> =
    coroutineBinding {
      val activeWallet = bitcoinWalletService.spendingWallet().value
        ?: Err(AppKeyWalletServiceError.ActiveWalletUnavailable()).bind()
      val isDestinationOwned = activeWallet.isMine(destination)
        .mapError(AppKeyWalletServiceError::DestinationValidationFailed)
        .bind()
      if (activeWallet.networkType != networkType || !isDestinationOwned) {
        Err(AppKeyWalletServiceError.InvalidDestination()).bind()
      }
    }

  private suspend fun validatePsbt(
    psbt: Psbt,
    sourceBalance: BitcoinMoney,
    destination: BitcoinAddress,
  ): Result<Unit, AppKeyWalletServiceError> {
    val activeWallet = bitcoinWalletService.spendingWallet().value
      ?: return Err(AppKeyWalletServiceError.ActiveWalletUnavailable())
    val outputIsDestination = psbt.outputs.singleOrNull()?.let { output ->
      activeWallet.isMine(output.scriptPubkey)
        .mapError(AppKeyWalletServiceError::DestinationValidationFailed)
        .getOrElse { return Err(it) }
    } ?: false
    val destinationIsOwned = activeWallet.isMine(destination)
      .mapError(AppKeyWalletServiceError::DestinationValidationFailed)
      .getOrElse { return Err(it) }
    val spendsSourceBalance = BitcoinMoney.sats(psbt.amountSats) + psbt.fee.amount == sourceBalance
    val psbtMatchesExpected = outputIsDestination && spendsSourceBalance && destinationIsOwned

    return if (!psbtMatchesExpected) {
      Err(AppKeyWalletServiceError.StalePreview())
    } else {
      Ok(Unit)
    }
  }

  private suspend fun wallet(
    keyset: SpendingKeyset,
  ): Result<AppKeyWallet, AppKeyWalletServiceError> =
    appKeyWalletProvider.getAppKeyWallet(keyset).mapError { error ->
      when (error) {
        is AppKeyWalletError.PrivateKeyUnavailable -> AppKeyWalletServiceError.PrivateKeyUnavailable()
        else -> AppKeyWalletServiceError.WalletCreationFailed(error)
      }
    }

  private suspend fun fullScan(wallet: AppKeyWallet): Result<Unit, AppKeyWalletServiceError> =
    withTimeoutResult(60.seconds) {
      wallet.fullScan().getOrElse { throw it }
    }.mapError(AppKeyWalletServiceError::FullScanFailed)

  private fun BitcoinMoney.sats(): ULong = fractionalUnitValue.ulongValue(exactRequired = true)

  /**
   * The in-memory state of the single App Key Wallet sweep currently being reviewed.
   *
   * Keeping prepared and signed transactions as separate types prevents broadcasting an unsigned
   * PSBT and prevents a second signing attempt after the operation has advanced to [Signed].
   */
  private sealed interface AppKeyWalletSweep {
    val id: AppKeyWalletSweepId
    val keysetId: String
    val networkType: BitcoinNetworkType
    val destination: BitcoinAddress
    val sourceBalance: BitcoinMoney
    val psbt: Psbt

    data class Prepared(
      override val id: AppKeyWalletSweepId,
      override val keysetId: String,
      override val networkType: BitcoinNetworkType,
      override val destination: BitcoinAddress,
      override val sourceBalance: BitcoinMoney,
      override val psbt: Psbt,
    ) : AppKeyWalletSweep

    data class Signed(
      override val id: AppKeyWalletSweepId,
      override val keysetId: String,
      override val networkType: BitcoinNetworkType,
      override val destination: BitcoinAddress,
      override val sourceBalance: BitcoinMoney,
      override val psbt: Psbt,
    ) : AppKeyWalletSweep
  }

  private fun AppKeyWalletSweep.matches(
    id: AppKeyWalletSweepId,
    keyset: SpendingKeyset,
  ) = this.id == id && keysetId == keyset.localId && networkType == keyset.networkType
}
