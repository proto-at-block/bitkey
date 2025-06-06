package bitkey.verification

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
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
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.get
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

@BitkeyInject(AppScope::class)
class TxVerificationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : TxVerificationDao {
  override suspend fun setActivePolicy(
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy.Active, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      transactionVerificationQueries.replaceActivePolicy(
        thresholdCurrencyAlphaCode = threshold.amount?.currency?.textCode,
        thresholdAmountFractionalUnitValue = threshold.amount?.fractionalUnitValue?.longValue()
      ).executeAsOne()
    }.flatMap { entity ->
      binding<TxVerificationPolicy.Active, Error> {
        TxVerificationPolicy.Active(
          id = TxVerificationPolicy.PolicyId(entity.id),
          threshold = threshold
        )
      }
    }
  }

  override suspend fun createPendingPolicy(
    threshold: VerificationThreshold,
    auth: TxVerificationPolicy.DelayNotifyAuthorization,
  ): Result<TxVerificationPolicy.Pending, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      transactionVerificationQueries.createPendingPolicy(
        thresholdCurrencyAlphaCode = threshold.amount?.currency?.textCode,
        thresholdAmountFractionalUnitValue = threshold.amount?.fractionalUnitValue?.longValue(),
        delayEndTime = auth.delayEndTime,
        authId = auth.id.value,
        cancellationToken = auth.cancellationToken,
        completionToken = auth.completionToken
      ).executeAsOne()
    }.flatMap { entity ->
      binding<TxVerificationPolicy.Pending, Error> {
        TxVerificationPolicy.Pending(
          id = TxVerificationPolicy.PolicyId(entity.id),
          authorization = auth,
          threshold = threshold
        )
      }
    }
  }

  override suspend fun promotePolicy(id: TxVerificationPolicy.PolicyId): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.promotePolicy(
        id = id.value
      )
    }
  }

  override suspend fun getActivePolicy(): Flow<Result<TxVerificationPolicy.Active?, Error>> {
    return databaseProvider.database().transactionVerificationQueries
      .getActivePolicy()
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
            TxVerificationPolicy.Active(
              id = TxVerificationPolicy.PolicyId(policy.id),
              threshold = buildThreshold(
                policyCurrencyCode = policy.thresholdCurrencyAlphaCode,
                threshold = policy.thresholdAmountFractionalUnitValue,
                fiatCurrencyEntity = fiatCurrencyResult?.bind()
              ).bind()
            )
          }
        } ?: Ok(null)
      }
  }

  override suspend fun getPendingPolicies(): Flow<Result<List<TxVerificationPolicy.Pending>, DbError>> {
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
          policyResults.bind().mapNotNull { policy ->
            binding {
              TxVerificationPolicy.Pending(
                id = TxVerificationPolicy.PolicyId(policy.id),
                threshold = buildThreshold(
                  policyCurrencyCode = policy.thresholdCurrencyAlphaCode,
                  policy.thresholdAmountFractionalUnitValue,
                  fiatCurrencyEntity = fiatCurrencyResults.bind()
                    .find { it.textCode == policy.thresholdCurrencyAlphaCode }
                ).bind(),
                authorization = TxVerificationPolicy.DelayNotifyAuthorization(
                  id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId(policy.authId),
                  delayEndTime = policy.delayEndTime,
                  cancellationToken = policy.cancellationToken,
                  completionToken = policy.completionToken
                )
              )
            }.logFailure { "Pending policy is invalid" }.get()
          }
        }
      }
  }

  override suspend fun deletePolicy(id: TxVerificationPolicy.PolicyId): Result<Unit, DbError> {
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

  private fun buildThreshold(
    policyCurrencyCode: IsoCurrencyTextCode?,
    threshold: Long?,
    fiatCurrencyEntity: FiatCurrencyEntity?,
  ): Result<VerificationThreshold, InvalidPolicyError> {
    return binding {
      when {
        policyCurrencyCode == null || threshold == null -> VerificationThreshold.Disabled
        else -> VerificationThreshold.Enabled(
          amount = buildMoney(
            policyCurrencyCode = policyCurrencyCode,
            threshold = threshold,
            currency = fiatCurrencyEntity
          ).bind()
        )
      }
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
