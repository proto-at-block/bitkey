package bitkey.verification

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class TxVerificationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : TxVerificationDao {
  override suspend fun setActivePolicy(
    txVerificationPolicy: TxVerificationPolicy.Active,
  ): Result<TxVerificationPolicy.Active, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      transactionVerificationQueries.setPolicy(
        thresholdCurrencyAlphaCode = txVerificationPolicy.threshold.amount.currency.textCode,
        thresholdAmountFractionalUnitValue = txVerificationPolicy.threshold.amount.fractionalUnitValue.longValue()
      )
    }.flatMap { entity ->
      binding<TxVerificationPolicy.Active, Error> {
        txVerificationPolicy
      }
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

  override suspend fun deletePolicy(): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      transactionVerificationQueries.deletePolicy()
    }
  }

  private fun buildThreshold(
    policyCurrencyCode: IsoCurrencyTextCode?,
    threshold: Long?,
    fiatCurrencyEntity: FiatCurrencyEntity?,
  ): Result<VerificationThreshold, InvalidPolicyError> {
    return binding {
      VerificationThreshold(
        amount = buildMoney(
          policyCurrencyCode = policyCurrencyCode ?: BTC.textCode,
          threshold = threshold ?: 0L,
          currency = fiatCurrencyEntity
        ).bind()
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
