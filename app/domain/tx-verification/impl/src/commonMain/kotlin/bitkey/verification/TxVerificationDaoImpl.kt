package bitkey.verification

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal class TxVerificationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val clock: Clock,
) : TxVerificationDao {
  override suspend fun setPolicy(policy: TxVerificationPolicy): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.setPolicy(
        id = policy.id.value,
        effective = null,
        thresholdCurrencyAlphaCode = policy.threshold.amount?.currency?.textCode,
        thresholdAmountFractionalUnitValue = policy.threshold.amount?.fractionalUnitValue?.longValue(),
        delayEndTime = policy.authorization?.delayEndTime,
        cancellationToken = policy.authorization?.cancellationToken,
        completionToken = policy.authorization?.completionToken
      )
    }
  }

  override suspend fun markPolicyEffective(id: TxVerificationPolicy.Id): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.markPolicyEffective(
        id = id.value,
        effective = clock.now()
      )
    }
  }

  override suspend fun getEffectivePolicy(): Flow<Result<TxVerificationPolicy?, Error>> {
    return databaseProvider.database().transactionVerificationQueries
      .getEffectivePolicy()
      .asFlowOfOneOrNull()
      .flatMapLatest { policyResult ->
        policyResult.get()?.thresholdCurrencyAlphaCode?.let { code ->
          databaseProvider.database()
            .fiatCurrencyQueries
            .getFiatCurrencyByTextCode(code)
            .asFlowOfOneOrNull()
            .map { policyResult to it }
        } ?: flowOf(policyResult to null)
      }
      .mapLatest { (policyResult, fiatCurrencyResult) ->
        policyResult.get()?.let { policy ->
          coroutineBinding {
            buildPolicy(
              id = policy.id,
              policyCurrencyCode = policy.thresholdCurrencyAlphaCode,
              threshold = policy.thresholdAmountFractionalUnitValue,
              delayEndTime = policy.delayEndTime,
              cancellationToken = policy.cancellationToken,
              completionToken = policy.completionToken,
              fiatCurrencyEntity = fiatCurrencyResult?.bind()
            ).bind()
          }
        } ?: Ok(null)
      }
  }

  override suspend fun getPendingPolicies(): Flow<Result<List<TxVerificationPolicy>, DbError>> {
    return databaseProvider.database().transactionVerificationQueries
      .getPendingPolicies()
      .asFlowOfList()
      .combine(
        databaseProvider.database()
          .fiatCurrencyQueries
          .allFiatCurrencies()
          .asFlowOfList()
      ) { policyResults, fiatCurrencyResults ->
        binding {
          policyResults.bind().map { policy ->
            buildPolicy(
              id = policy.id,
              policyCurrencyCode = policy.thresholdCurrencyAlphaCode,
              threshold = policy.thresholdAmountFractionalUnitValue,
              delayEndTime = policy.delayEndTime,
              cancellationToken = policy.cancellationToken,
              completionToken = policy.completionToken,
              fiatCurrencyEntity = fiatCurrencyResults.bind()
                .find { it.textCode == policy.thresholdCurrencyAlphaCode }
            ).logFailure { "Pending policy is invalid" }.get()
          }.filterNotNull()
        }
      }
  }

  override suspend fun deletePolicy(id: TxVerificationPolicy.Id): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.deletePolicy(
        id = id.value
      )
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.clear()
    }
  }

  /**
   * Build the verification policy domain model from query results.
   */
  private fun buildPolicy(
    id: String,
    policyCurrencyCode: IsoCurrencyTextCode?,
    threshold: Long?,
    delayEndTime: Instant?,
    cancellationToken: String?,
    completionToken: String?,
    fiatCurrencyEntity: FiatCurrencyEntity?,
  ): Result<TxVerificationPolicy, InvalidPolicyError> {
    return binding {
      TxVerificationPolicy(
        id = TxVerificationPolicy.Id(id),
        threshold = when {
          policyCurrencyCode == null || threshold == null -> VerificationThreshold.Disabled
          else -> VerificationThreshold.Enabled(
            amount = buildMoney(
              policyCurrencyCode = policyCurrencyCode,
              threshold = threshold,
              currency = fiatCurrencyEntity
            ).bind()
          )
        },
        authorization = when {
          delayEndTime == null || cancellationToken == null || completionToken == null -> null
          else -> TxVerificationPolicy.DelayNotifyAuthorization(
            delayEndTime = delayEndTime,
            cancellationToken = cancellationToken,
            completionToken = completionToken
          )
        }
      )
    }
  }

  /**
   * Build the money object used in the verification policy.
   */
  private fun buildMoney(
    policyCurrencyCode: IsoCurrencyTextCode,
    threshold: Long,
    currency: FiatCurrencyEntity?,
  ): Result<Money, InvalidPolicyError> {
    return when {
      policyCurrencyCode == BTC.textCode -> Ok(BitcoinMoney(threshold.toBigInteger()))
      currency != null -> Ok(
        FiatMoney(
          currency = FiatCurrency(
            textCode = currency.textCode,
            unitSymbol = currency.displayUnitSymbol,
            fractionalDigits = currency.fractionalDigits.toInt(),
            displayConfiguration =
              FiatCurrency.DisplayConfiguration(
                name = currency.displayName,
                displayCountryCode = currency.displayCountryCode
              )
          ),
          fractionalUnitAmount = threshold.toBigInteger()
        )
      )
      else -> Err(InvalidPolicyError("Currency entity for $policyCurrencyCode not found"))
    }
  }
}
