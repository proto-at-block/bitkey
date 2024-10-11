package build.wallet.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetPartnershipTransactionF8eClient
import build.wallet.flatMapIfNotNull
import build.wallet.ktor.result.HttpError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getErrorOr
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.time.Duration

class PartnershipTransactionsRepositoryImpl(
  private val dao: PartnershipTransactionsDao,
  private val getPartnershipTransactionF8eClient: GetPartnershipTransactionF8eClient,
  private val clock: Clock,
) : PartnershipTransactionsStatusRepository {
  override val transactions: Flow<List<PartnershipTransaction>> = dao.getTransactions().map {
    it.logFailure { "Failed to get partnership transactions" }
      .getOr(emptyList())
  }

  override val previouslyUsedPartnerIds: Flow<List<PartnerId>> = dao.getPreviouslyUsedPartnerIds()
    .map {
      it.logFailure { "Failed to get partner IDs" }
        .getOr(emptyList())
    }

  override suspend fun sync() {
    TODO("W-6471")
  }

  override suspend fun clear(): Result<Unit, Error> {
    return dao.clear()
      .logFailure { "Failed clearing partnership transactions" }
  }

  override suspend fun create(
    id: PartnershipTransactionId,
    partnerInfo: PartnerInfo,
    type: PartnershipTransactionType,
  ): Result<PartnershipTransaction, Error> {
    val timestamp = clock.now()
    val transaction = PartnershipTransaction(
      id = id,
      type = type,
      status = null,
      context = null,
      partnerInfo = partnerInfo,
      cryptoAmount = null,
      txid = null,
      fiatAmount = null,
      fiatCurrency = null,
      paymentMethod = null,
      created = timestamp,
      updated = timestamp,
      sellWalletAddress = null
    )

    return dao.save(transaction).map { transaction }
  }

  override suspend fun updateRecentTransactionStatusIfExists(
    partnerId: PartnerId,
    status: PartnershipTransactionStatus,
    recency: Duration,
  ): Result<PartnershipTransaction?, Error> {
    return coroutineBinding {
      dao.getMostRecentByPartner(partnerId)
        .bind()
        ?.takeIf { it.created > clock.now().minus(recency) }
        ?.copy(status = status)
        ?.also {
          dao.save(it).bind()
        }
    }
  }

  override suspend fun syncTransaction(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    return dao.getById(transactionId)
      .flatMapIfNotNull { mostRecent ->
        fetchUpdatedTransaction(
          fullAccountId = fullAccountId,
          f8eEnvironment = f8eEnvironment,
          transaction = mostRecent
        )
      }
      // If the update fails because the transaction is not found, emit null, since the transaction does not
      // exist for the partner.
      .recoverIf(
        predicate = { it is HttpError.ClientError && it.response.status == HttpStatusCode.NotFound },
        transform = { null }
      )
  }

  private suspend fun fetchUpdatedTransaction(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    transaction: PartnershipTransaction,
  ): Result<PartnershipTransaction, Error> {
    return coroutineBinding {
      val new = getPartnershipTransactionF8eClient.getPartnershipTransaction(
        fullAccountId = fullAccountId,
        f8eEnvironment = f8eEnvironment,
        partner = transaction.partnerInfo.partnerId,
        partnershipTransactionId = transaction.id,
        transactionType = transaction.type
      ).bind()

      transaction.copy(
        status = new.status,
        context = new.context,
        cryptoAmount = new.cryptoAmount,
        txid = new.txid,
        fiatAmount = new.fiatAmount,
        fiatCurrency = new.fiatCurrency,
        paymentMethod = new.paymentMethod,
        updated = clock.now(),
        sellWalletAddress = new.sellWalletAddress
      ).also { updated ->
        dao.save(updated).bind()
      }
    }.mapError { error ->
      when {
        error is HttpError.ClientError && error.response.status == HttpStatusCode.NotFound -> {
          log(LogLevel.Debug) { "Transaction was not found, removing from local database" }
          dao.deleteTransaction(transaction.id).getErrorOr(error)
        }

        else -> error.also {
          log(LogLevel.Error, throwable = error) { "Failed to fetch updated transaction" }
        }
      }
    }
  }
}
