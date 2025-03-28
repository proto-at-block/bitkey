package build.wallet.bitcoin.export

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.export.ExportTransactionRow.ExportTransactionType.*
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletProvider
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class ExportTransactionsServiceImpl(
  private val accountService: AccountService,
  private val watchingWalletProvider: WatchingWalletProvider,
  private val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val exportTransactionsAsCsvSerializer: ExportTransactionsAsCsvSerializer,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
) : ExportTransactionsService {
  override suspend fun export(): Result<ExportedTransactions, Throwable> =
    coroutineBinding {
      val descriptors = getAllDescriptors().bind()

      val confirmedTransactionRows = fetchConfirmedTransactionRows(descriptors).bind()
      val flattenedTransactionRows = flattenSweepTransactions(confirmedTransactionRows)

      ExportedTransactions(aggregateToCsv(flattenedTransactionRows))
    }

  private fun flattenSweepTransactions(
    transactionRows: List<ExportTransactionRow>,
  ): List<ExportTransactionRow> {
    val groupedTransactionRows = transactionRows.groupBy { it.txid }

    return groupedTransactionRows.values.flatMap { txGroup ->
      val transactionTypes = txGroup.map { it.transactionType }.toSet()

      if (transactionTypes.contains(Incoming) && transactionTypes.contains(Outgoing)) {
        // Check if amounts are the same across the group
        val amounts = txGroup.map { it.amount }.toSet()
        if (amounts.size == 1) {
          // Amounts and fees are the same, flatten to a single "Self Send" transaction
          listOf(txGroup.first().copy(transactionType = Sweep))
        } else {
          // Amounts or fees differ, do not flatten
          txGroup
        }
      } else {
        // No duplicates, return the group as is
        txGroup
      }
    }
  }

  private suspend fun fetchConfirmedTransactionRows(descriptors: List<WatchingWalletDescriptor>) =
    coroutineBinding {
      descriptors.flatMap { descriptor ->
        val wallet = watchingWalletProvider.getWallet(descriptor).bind()
        wallet.sync().bind()
        wallet.transactions()
          .first()
          .filter { it.confirmationStatus is Confirmed }
      }.sortedByDescending { it.confirmationTime() }
        .map { it.toExportTransactionRow() }
    }

  private suspend fun aggregateToCsv(transactionRows: List<ExportTransactionRow>): ByteString =
    exportTransactionsAsCsvSerializer.toCsvString(rows = transactionRows).encodeUtf8()

  private suspend fun getAllDescriptors(): Result<List<WatchingWalletDescriptor>, Error> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      val serverInactiveKeysets = listKeysetsF8eClient
        .listKeysets(
          account.keybox.config.f8eEnvironment,
          account.accountId
        )
        .mapError { Error("Failed to fetch keysets") }
        .logFailure { "Error fetching keysets for an account when exporting transaction history." }
        .bind()
        .filter {
          it.f8eSpendingKeyset.keysetId != account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
        }

      (serverInactiveKeysets + account.keybox.activeSpendingKeyset)
        .map { keyset ->
          WatchingWalletDescriptor(
            identifier = "WatchingWallet ${keyset.localId}",
            // This is fine because we never allow accounts to jump networks anyways.
            networkType = account.config.bitcoinNetworkType,
            receivingDescriptor = bitcoinMultiSigDescriptorBuilder
              .watchingReceivingDescriptor(
                appPublicKey = keyset.appKey.key,
                hardwareKey = keyset.hardwareKey.key,
                serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
              ),
            changeDescriptor = bitcoinMultiSigDescriptorBuilder
              .watchingChangeDescriptor(
                appPublicKey = keyset.appKey.key,
                hardwareKey = keyset.hardwareKey.key,
                serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
              )
          )
        }
    }
}

private fun BitcoinTransaction.toExportTransactionRow(): ExportTransactionRow {
  return ExportTransactionRow(
    txid = BitcoinTransactionId(value = id),
    confirmationTime = (confirmationStatus as Confirmed).blockTime.timestamp,
    amount = subtotal,
    fees = fee,
    transactionType = transactionType.toExportTransactionType()
  )
}
