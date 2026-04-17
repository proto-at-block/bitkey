package build.wallet.bitcoin.transactions

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class SpeedUpDepositServiceFake : SpeedUpDepositService {
  var result: Result<SpeedUpDepositTransaction, Error> = Err(Error("Unable to speed up deposit"))

  override suspend fun prepareSpeedUpDepositTransaction(
    transaction: BitcoinTransaction,
  ): Result<SpeedUpDepositTransaction, Error> = result

  fun reset() {
    result = Err(Error("Unable to speed up deposit"))
  }
}
