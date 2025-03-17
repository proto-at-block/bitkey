package build.wallet.limit

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.database.sqldelight.SpendingLimitEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.get
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class SpendingLimitDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : SpendingLimitDao {
  override fun activeSpendingLimit(): Flow<SpendingLimit?> {
    return flow {
      databaseProvider.database()
        .spendingLimitQueries
        .activeLimit()
        .asFlowOfOneOrNull()
        .distinctUntilChanged()
        .mapLatest { result ->
          result
            .logFailure { "Failed to read active spending limit from database" }
            .flatMap { it?.toSpendingLimit() ?: Ok(null) }
            .get()
        }
        .collect(::emit)
    }
  }

  override suspend fun mostRecentSpendingLimit(): Result<SpendingLimit?, DbError> {
    return databaseProvider.database().spendingLimitQueries.lastLimit().awaitAsOneOrNullResult()
      .flatMap { lastLimitEntity -> lastLimitEntity?.toSpendingLimit() ?: Ok(null) }
      .logFailure { "Failed to fetch most recent spending limit from database:" }
  }

  override suspend fun saveAndSetSpendingLimit(limit: SpendingLimit): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        val existingLimit = spendingLimitQueries.lastLimit().executeAsOneOrNull()

        when (existingLimit) {
          null ->
            spendingLimitQueries.insertLimit(
              active = limit.active,
              limitAmountFractionalUnitValue = limit.amount.fractionalUnitValue.longValue(),
              limitAmountCurrencyAlphaCode = limit.amount.currency.textCode,
              limitTimeZoneZoneId = limit.timezone
            )
          else ->
            spendingLimitQueries.updateLimit(
              active = limit.active,
              limitAmountFractionalUnitValue = limit.amount.fractionalUnitValue.longValue(),
              limitAmountCurrencyAlphaCode = limit.amount.currency.textCode,
              limitTimeZoneZoneId = limit.timezone
            )
        }
      }
      .logFailure { "Failed to save and set spending limit" }
  }

  override suspend fun disableSpendingLimit(): Result<Unit, DbError> {
    return databaseProvider.database().spendingLimitQueries.awaitTransaction {
      disableLimit()
    }.logFailure { "Failed to disable spending limit" }
  }

  override suspend fun removeAllLimits(): Result<Unit, DbError> {
    return databaseProvider.database().spendingLimitQueries.awaitTransaction { removeAllLimits() }
      .logFailure { "Failed to remove all limits" }
  }

  private suspend fun SpendingLimitEntity.toSpendingLimit(): Result<SpendingLimit?, DbError> =
    coroutineBinding {
      val fiatCurrencyEntity =
        databaseProvider.database()
          .fiatCurrencyQueries
          .getFiatCurrencyByTextCode(limitAmountCurrencyAlphaCode)
          .awaitAsOneOrNullResult().bind() ?: return@coroutineBinding null

      SpendingLimit(
        active = active,
        amount = FiatMoney(
          currency = fiatCurrencyEntity.toFiatCurrency(),
          fractionalUnitAmount = limitAmountFractionalUnitValue.toBigInteger()
        ),
        timezone = limitTimeZoneZoneId
      )
    }
}

private fun FiatCurrencyEntity.toFiatCurrency() =
  FiatCurrency(
    textCode = textCode,
    unitSymbol = displayUnitSymbol,
    fractionalDigits = fractionalDigits.toInt(),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = displayName,
        displayCountryCode = displayCountryCode
      )
  )
