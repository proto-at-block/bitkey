package build.wallet.bitcoin.transactions

import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class SpeedUpTransactionServiceFake : SpeedUpTransactionService {
  var result: Result<SpeedUpTransactionResult, SpeedUpTransactionError> =
    Err(SpeedUpTransactionError.FailedToPrepareData)

  override suspend fun prepareTransactionSpeedUp(
    account: Account,
    transaction: BitcoinTransaction,
  ): Result<SpeedUpTransactionResult, SpeedUpTransactionError> = result

  fun reset() {
    result = Err(SpeedUpTransactionError.FailedToPrepareData)
  }
}
