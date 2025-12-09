package bitkey.verification

import bitkey.privilegedactions.AuthorizationStrategy.OutOfBand
import bitkey.privilegedactions.AuthorizationStrategyType.OUT_OF_BAND
import bitkey.privilegedactions.PrivilegedActionType.LOOSEN_TRANSACTION_VERIFICATION_POLICY
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Err
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

  var updateThresholdReturnsPending: Boolean = false
  var updateThresholdReturnsError: Boolean = false

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
    policy: TxVerificationPolicy.Active,
    amountBtc: BitcoinMoney?,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy, Error> {
    if (updateThresholdReturnsError) {
      return Err(Error("Test error: update failed"))
    }

    if (updateThresholdReturnsPending) {
      return Ok(
        TxVerificationPolicy.Pending(
          authorization = bitkey.privilegedactions.PrivilegedActionInstance(
            id = "test-pending-action",
            privilegedActionType = LOOSEN_TRANSACTION_VERIFICATION_POLICY,
            authorizationStrategy = OutOfBand(
              authorizationStrategyType = OUT_OF_BAND
            )
          )
        )
      )
    }

    currentThreshold.update { policy.threshold }

    return Ok(policy)
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
    updateThresholdReturnsPending = false
    updateThresholdReturnsError = false
  }
}
