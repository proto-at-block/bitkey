@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.limit

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.mobilepay.MobilePayBalanceFailure
import build.wallet.f8e.mobilepay.MobilePayBalanceService
import build.wallet.ktor.result.HttpError
import build.wallet.mapLoadedValue
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

class MobilePayStatusProviderImpl(
  private val spendingLimitDao: SpendingLimitDao,
  private val mobilePayBalanceService: MobilePayBalanceService,
  private val uuid: Uuid,
) : MobilePayStatusProvider {
  // We initialize with null so that we can filter the initialization event when merging.
  private val onDemandRefreshStatusFlow = MutableStateFlow<String?>(null)

  override suspend fun refreshStatus() {
    onDemandRefreshStatusFlow.emit(uuid.random())
  }

  override fun status(
    keybox: Keybox,
    wallet: SpendingWallet,
  ): Flow<MobilePayStatus> {
    // The Mobile Pay balance calculation is dependent on both the limit that the customer sets, and
    // the transactions the customer creates. Therefore, we need to listen to transaction list sync
    // events to know when to calculate the customer's available balance.
    return merge(
      // First flow, emits when active spending limit is updated.
      spendingLimitDao.activeSpendingLimit(),
      // Second flow, emits when a sync event occurs and maps it to active spending limit.
      wallet.balance()
        .mapLoadedValue()
        .map { spendingLimitDao.activeSpendingLimit().firstOrNull() },
      // Third flow, emits when users of `MobilePayStatusProvider` refresh on-demand
      onDemandRefreshStatusFlow
        .filterNotNull() // Filter the initialization event
        .map { spendingLimitDao.activeSpendingLimit().firstOrNull() }
    ).transformLatest { localActiveSpendingLimit ->
      // Always first attempt a network request. If F8e returns an error, that means that the
      // customer has never set a spending limit.
      getMobilePayBalanceFromF8e(keybox)
        // Happy Path
        .onSuccess { f8eMobilePayBalance ->
          when (checkActiveStateAndLimitValue(localActiveSpendingLimit, f8eMobilePayBalance)) {
            // If App & F8e disagree on active state, update App to reflect what is returned from F8e
            F8eAndLocalMobilePayBalanceState.Mismatch -> {
              spendingLimitDao.saveAndSetSpendingLimit(f8eMobilePayBalance.limit)

              // We let the activeSpendingLimit flow re-evaluate this block if we updated the
              // spendingLimitDao.activeSpendingLimit value upstream.
              return@transformLatest
            }
            F8eAndLocalMobilePayBalanceState.Match -> {
              if (f8eMobilePayBalance.limit.active) {
                emit(
                  MobilePayStatus.MobilePayEnabled(f8eMobilePayBalance.limit, f8eMobilePayBalance)
                )
              } else {
                emit(MobilePayStatus.MobilePayDisabled(f8eMobilePayBalance.limit))
              }
            }
          }
        }
        // When we arrive here, it can mean a few things:
        // 1. Network failure.
        // 2. The user has *never* set a spending limit with F8e.
        // 3. F8e is returning a spending limit that the App does not support.
        .onFailure { error ->
          val isErrorNetworkError = (error as? MobilePayBalanceFailure.F8eError)?.error is HttpError.NetworkError

          // If it is a network failure, but we have a local active spending limit available, we
          // show it without the progress bar.
          if (isErrorNetworkError && localActiveSpendingLimit != null) {
            emit(MobilePayStatus.MobilePayEnabled(localActiveSpendingLimit, null))
            return@transformLatest
          }

          // If it is not a network error, which means F8e *never* received any spending limit
          // information, make sure we delete any traces of limits locally.
          if (!isErrorNetworkError && localActiveSpendingLimit != null) {
            spendingLimitDao.removeAllLimits()
          }

          // Under all other circumstances, assume Mobile Pay to be disabled.
          val mostRecentSpendingLimit = spendingLimitDao.mostRecentSpendingLimit().get()
          emit(MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit))
        }
    }
  }

  private fun checkActiveStateAndLimitValue(
    localActiveSpendingLimit: SpendingLimit?,
    f8eMobilePayBalanceValue: MobilePayBalance,
  ): F8eAndLocalMobilePayBalanceState {
    // If App & F8e disagree on active state.
    val isDisagreementOnActiveState =
      (localActiveSpendingLimit == null && f8eMobilePayBalanceValue.limit.active) ||
        (localActiveSpendingLimit != null && localActiveSpendingLimit.active != f8eMobilePayBalanceValue.limit.active)
    val isDisagreementOnLimitValue =
      localActiveSpendingLimit != null && localActiveSpendingLimit != f8eMobilePayBalanceValue.limit

    return if (isDisagreementOnActiveState || isDisagreementOnLimitValue) {
      F8eAndLocalMobilePayBalanceState.Mismatch
    } else {
      F8eAndLocalMobilePayBalanceState.Match
    }
  }

  private suspend fun getMobilePayBalanceFromF8e(keybox: Keybox) =
    mobilePayBalanceService.getMobilePayBalance(
      f8eEnvironment = keybox.config.f8eEnvironment,
      fullAccountId = keybox.fullAccountId
    )

  private sealed class F8eAndLocalMobilePayBalanceState {
    // Both F8e and Local Mobile Pay Balance have matching active state and values
    data object Match : F8eAndLocalMobilePayBalanceState()

    // Both F8e and Local Mobile Pay Balance do not have matching active state and values
    data object Mismatch : F8eAndLocalMobilePayBalanceState()
  }
}
