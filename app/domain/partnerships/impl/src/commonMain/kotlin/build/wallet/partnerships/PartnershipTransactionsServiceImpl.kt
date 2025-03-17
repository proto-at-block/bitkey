package build.wallet.partnerships

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.partnerships.GetPartnershipTransactionF8eClient
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.flatMapIfNotNull
import build.wallet.ktor.result.HttpError.ClientError
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnershipTransactionStatus.PENDING
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState.FOREGROUND
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class PartnershipTransactionsServiceImpl(
  private val expectedTransactionsFlag: ExpectedTransactionsPhase2FeatureFlag,
  private val accountService: AccountService,
  private val dao: PartnershipTransactionsDao,
  private val getPartnershipTransactionF8eClient: GetPartnershipTransactionF8eClient,
  private val clock: Clock,
  private val appSessionManager: AppSessionManager,
) : PartnershipTransactionsService, PartnershipTransactionsSyncWorker {
  private val syncLock = Mutex()
  private val transactionsCache = MutableStateFlow<List<PartnershipTransaction>?>(null)

  override suspend fun executeWork() {
    coroutineScope {
      launch {
        dao.getTransactions()
          .distinctUntilChanged()
          .collectLatest { result ->
            result
              .logFailure { "Failed to get partnership transactions" }
              .onSuccess { transactions ->
                // Only emit transactions on success
                transactionsCache.value = transactions
              }
          }
      }

      // Periodically sync pending transactions (if any).
      launch {
        val appSessionState = appSessionManager.appSessionState
        appSessionState.collectLatest { sessionState ->
          val appInForeground = sessionState == FOREGROUND
          if (appInForeground) {
            expectedTransactionsFlag.flagValue().map { it.value }
              .collectLatest { expectedTransactionsEnabled ->
                if (expectedTransactionsEnabled) {
                  launchTicker(5.seconds) {
                    syncPendingTransactions()
                  }
                }
              }
          }
        }
      }
    }
  }

  override val transactions: Flow<List<PartnershipTransaction>> = transactionsCache.filterNotNull()

  /**
   * Partnership transactions that have [PENDING] status.
   */
  private val pendingTransactions: Flow<List<PartnershipTransaction>> =
    transactionsCache
      .mapNotNull { it?.filter { it.status == PENDING || it.status == null } }
      .distinctUntilChanged()

  override val previouslyUsedPartnerIds: Flow<List<PartnerId>> = dao.getPreviouslyUsedPartnerIds()
    .map {
      it.logFailure { "Failed to get partner IDs" }
        .getOr(emptyList())
    }

  override suspend fun syncPendingTransactions(): Result<Unit, Error> =
    coroutineBinding {
      syncLock.withLock {
        val transactions = pendingTransactions.first()
        if (transactions.isNotEmpty()) {
          val account = accountService.getAccount<FullAccount>().bind()
          transactions.forEach {
            syncTransactionWithoutLock(account, it.id).bind()
          }
        }
      }
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
      sellWalletAddress = null,
      partnerTransactionUrl = null
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
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> =
    coroutineBinding {
      syncLock.withLock {
        val account = accountService.getAccount<FullAccount>().bind()
        syncTransactionWithoutLock(account, transactionId).bind()
      }
    }

  override suspend fun getTransactionById(
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> =
    coroutineBinding {
      dao.getById(transactionId).bind()
    }

  private suspend fun syncTransactionWithoutLock(
    account: Account,
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    return dao.getById(transactionId)
      .flatMapIfNotNull { mostRecent ->
        coroutineBinding {
          val new = getPartnershipTransactionF8eClient
            .getPartnershipTransaction(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              partner = mostRecent.partnerInfo.partnerId,
              partnershipTransactionId = mostRecent.id,
              transactionType = mostRecent.type
            )
            .bind()

          mostRecent.copy(
            status = new.status,
            context = new.context,
            cryptoAmount = new.cryptoAmount,
            txid = new.txid,
            fiatAmount = new.fiatAmount,
            fiatCurrency = new.fiatCurrency,
            paymentMethod = new.paymentMethod,
            updated = clock.now(),
            sellWalletAddress = new.sellWalletAddress,
            partnerTransactionUrl = new.partnerTransactionUrl,
            partnerInfo = new.partnerInfo
          ).also { updated ->
            dao.save(updated).bind()
          }
        }.mapError { error ->
          when {
            error is ClientError && error.response.status == HttpStatusCode.NotFound -> {
              // only remove the transaction if it is older than 15 minutes
              // this is done to prevent a sync which removes a transaction before it is created
              // on the partner side
              if (clock.now() - 15.minutes >= mostRecent.created) {
                logDebug { "Transaction was not found, removing from local database" }
                dao.deleteTransaction(mostRecent.id).getErrorOr(error)
              } else {
                error
              }
            }

            else -> error.also {
              logError(throwable = error) { "Failed to fetch updated transaction" }
            }
          }
        }
      }
      // If the update fails because the transaction is not found, emit null, since the transaction does not
      // exist for the partner.
      .recoverIf(
        predicate = { it is ClientError && it.response.status == HttpStatusCode.NotFound },
        transform = { null }
      )
  }
}
