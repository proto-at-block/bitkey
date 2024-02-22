@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.limit

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.database.sqldelight.SpendingLimitEntity
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

class SpendingLimitDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : SpendingLimitDao {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun activeSpendingLimit(): Flow<SpendingLimit?> {
    return databaseProvider.database().spendingLimitQueries.activeLimit()
      .asFlowOfOneOrNull()
      .distinctUntilChanged()
      .transformLatest { result ->
        result
          .logFailure { "Failed to read active spending limit from database" }
          .onSuccess { activeLimit ->
            when (activeLimit) {
              InitialLoading -> Unit
              is LoadedValue -> emit(activeLimit.value?.toSpendingLimit())
            }
          }
          .onFailure {
            emit(null)
          }
      }
  }

  override suspend fun getActiveSpendingLimit(): Result<SpendingLimit?, DbError> {
    return mostRecentSpendingLimit().fold(
      success = { spendingLimit ->
        when (spendingLimit != null && spendingLimit.active) {
          true -> Ok(spendingLimit)
          false -> Ok(null)
        }
      },
      failure = { Err(it) }
    )
  }

  override suspend fun mostRecentSpendingLimit(): Result<SpendingLimit?, DbError> {
    return databaseProvider.database().spendingLimitQueries.lastLimit().awaitAsOneOrNullResult()
      .map { lastLimitEntity -> lastLimitEntity?.toSpendingLimit() }
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

  private suspend fun SpendingLimitEntity.toSpendingLimit(): SpendingLimit? {
    val fiatCurrencyEntity =
      databaseProvider.database()
        .fiatCurrencyQueries
        .getFiatCurrencyByTextCode(limitAmountCurrencyAlphaCode)
        .awaitAsOneOrNull() ?: return null
    return SpendingLimit(
      active = active,
      amount =
        FiatMoney(
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
