package bitkey.f8e.verify

import bitkey.verification.FakePendingVerification
import bitkey.verification.FakeTxVerificationApproval
import bitkey.verification.PendingTransactionVerification
import bitkey.verification.TxVerificationApproval
import bitkey.verification.TxVerificationId
import bitkey.verification.TxVerificationState
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class TxVerificationF8eClientFake : TxVerificationF8eClient {
  var status: TxVerificationState = TxVerificationState.Pending

  override suspend fun createVerificationRequest(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<PendingTransactionVerification, Throwable> {
    return Ok(FakePendingVerification)
  }

  override suspend fun requestGrant(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<TxVerificationApproval, Throwable> {
    return Ok(FakeTxVerificationApproval)
  }

  override suspend fun getVerificationStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<TxVerificationState, Throwable> {
    return Ok(status)
  }

  override suspend fun cancelVerification(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<Unit, Throwable> {
    return Ok(Unit)
  }
}
