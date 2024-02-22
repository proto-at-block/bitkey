package build.wallet.statemachine.data.mobilepay

import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayBalance
import build.wallet.limit.SpendingLimit
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import com.github.michaelbull.result.Result

/**
 * Describes Mobile Pay status of the currently activated keybox.
 */
sealed interface MobilePayData {
  val spendingLimit: SpendingLimit?

  /**
   * Loading local and remote information to determine Mobile Pay data.
   */
  data object LoadingMobilePayData : MobilePayData {
    override val spendingLimit: SpendingLimit? = null
  }

  /**
   * Mobile Pay is enabled.
   *
   * @property activeSpendingLimit current spending limit set on this account.
   * @property balance current balance information of a customer's Mobile Pay setu
   * @property disableMobilePay disables Mobile Pay for this account.
   * @property changeSpendingLimit given new spending limit and hardware signature of the limit,
   * enables and activates the new limit on this account.
   */
  data class MobilePayEnabledData(
    val activeSpendingLimit: SpendingLimit,
    val balance: MobilePayBalance?,
    val disableMobilePay: () -> Unit,
    val remainingFiatSpendingAmount: FiatMoney?,
    val changeSpendingLimit: (
      newSpendingLimit: SpendingLimit,
      selectedFiatLimit: FiatMoney,
      hwFactorProofOfPossession: HwFactorProofOfPossession,
      onResult: (Result<Unit, Error>) -> Unit,
    ) -> Unit,
    val refreshBalance: () -> Unit,
    override val spendingLimit: SpendingLimit? = activeSpendingLimit,
  ) : MobilePayData

  /**
   * Mobile pay is disabled.
   *
   * @property mostRecentSpendingLimit a spending limit that was previous set on this account, if any.
   * @property enableMobilePay given new spending limit and hardware signature of the limit, enables
   * and activates the new limit on this account.
   */
  data class MobilePayDisabledData(
    val mostRecentSpendingLimit: SpendingLimit?,
    val enableMobilePay: (
      spendingLimit: SpendingLimit,
      selectedFiatLimit: Money,
      hwFactorProofOfPossession: HwFactorProofOfPossession,
      onResult: (Result<Unit, Error>) -> Unit,
    ) -> Unit,
    override val spendingLimit: SpendingLimit? = null,
  ) : MobilePayData
}
