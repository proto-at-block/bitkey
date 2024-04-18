package build.wallet.partnerships

import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class PartnershipTransactionsRepositoryImpl(
  private val dao: PartnershipTransactionsDao,
  private val uuidGenerator: UuidGenerator,
  private val clock: Clock,
) : PartnershipTransactionsStatusRepository {
  override val transactions: Flow<List<PartnershipTransaction>> = dao.getTransactions().map {
    it.logFailure { "Failed to get partnership transactions" }
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
    partnerInfo: PartnerInfo,
    type: PartnershipTransactionType,
  ): Result<PartnershipTransaction, Error> {
    val id = uuidGenerator.random().let(::PartnershipTransactionId)
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
      updated = timestamp
    )

    return dao.save(transaction).map { transaction }
  }
}
