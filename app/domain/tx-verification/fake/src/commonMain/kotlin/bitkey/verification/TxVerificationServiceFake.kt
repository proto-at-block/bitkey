package bitkey.verification

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.*

class TxVerificationServiceFake : TxVerificationService {
  val currentThreshold = MutableStateFlow<VerificationThreshold?>(null)

  var requireVerification: Boolean = false
  var verificationResult: Result<ConfirmationFlow<TxVerificationApproval>, Throwable> = Ok(
    flowOf(
      ConfirmationState.Pending,
      ConfirmationState.Confirmed(FakeTxVerificationApproval)
    )
  )

  override suspend fun isVerificationRequired(
    amount: BitcoinMoney,
    exchangeRates: List<ExchangeRate>?,
  ): Boolean = requireVerification

  override suspend fun requestGrant(psbt: Psbt): Result<TxVerificationApproval, Throwable> {
    return Ok(FakeTxVerificationApproval)
  }

  override suspend fun requestVerification(
    psbt: Psbt,
  ): Result<ConfirmationFlow<TxVerificationApproval>, Throwable> {
    return verificationResult
  }

  override fun getCurrentThreshold(): Flow<Result<VerificationThreshold?, Error>> {
    return currentThreshold.map {
      Ok(it)
    }
  }

  override fun getPendingPolicy(): Flow<Result<TxVerificationPolicy.Pending?, Error>> {
    return flowOf(Ok(null))
  }

  override suspend fun updateThreshold(
    txVerificationPolicy: TxVerificationPolicy.Active,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    currentThreshold.update { txVerificationPolicy.threshold }

    return Ok(Unit)
  }

  fun reset() {
    requireVerification = false
    verificationResult = Ok(
      flowOf(
        ConfirmationState.Pending,
        ConfirmationState.Confirmed(FakeTxVerificationApproval)
      )
    )
    currentThreshold.value = null
  }
}
