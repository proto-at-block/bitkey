package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.verification.TxVerificationApproval
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class MobilePayServiceMock(
  turbine: (name: String) -> Turbine<Any?>,
) : MobilePayService {
  override val mobilePayData = MutableStateFlow<MobilePayData?>(null)

  val disableCalls = turbine("disable mobile pay calls")
  val signPsbtCalls = turbine("sign psbt with mobile pay calls")
  val signPsbtGrants = turbine("sign psbt associated grants")
  val getDailySpendingLimitStatusCalls = turbine("get daily spending limit calls")
  var disableResult: Result<Unit, Error> = Ok(Unit)
  var signPsbtWithMobilePayResult: Result<Psbt, Error>? = null
  var keysetId: String? = null
  var status: DailySpendingLimitStatus = DailySpendingLimitStatus.RequiresHardware

  override suspend fun disable(): Result<Unit, Error> {
    disableCalls += Unit
    return disableResult
  }

  val deleteLocalCalls = turbine("delete local calls")

  override suspend fun deleteLocal(): Result<Unit, Error> {
    deleteLocalCalls += Unit
    return Ok(Unit)
  }

  override suspend fun signPsbtWithMobilePay(
    psbt: Psbt,
    grant: TxVerificationApproval?,
  ): Result<Psbt, Error> {
    val signedPsbt = psbt.copy(base64 = "${psbt.base64} ${signature(keysetId ?: "")}")
    signPsbtCalls += signedPsbt
    signPsbtGrants += grant
    return signPsbtWithMobilePayResult ?: Ok(signedPsbt)
  }

  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
  ): DailySpendingLimitStatus {
    getDailySpendingLimitStatusCalls += transactionAmount
    return status
  }

  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinTransactionSendAmount,
  ): DailySpendingLimitStatus {
    getDailySpendingLimitStatusCalls += transactionAmount
    return status
  }

  val setLimitCalls = turbine("set mobile pay limit calls")

  override suspend fun setLimit(
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    setLimitCalls += spendingLimit
    return Ok(Unit)
  }

  fun reset() {
    disableResult = Ok(Unit)
    mobilePayData.value = null
    keysetId = null
    signPsbtWithMobilePayResult = null
    status = DailySpendingLimitStatus.RequiresHardware
  }

  private fun signature(keysetId: String) = "is_server_signed($keysetId)"
}
