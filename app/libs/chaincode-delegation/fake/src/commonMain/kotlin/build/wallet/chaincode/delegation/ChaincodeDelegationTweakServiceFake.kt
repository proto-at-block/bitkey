package build.wallet.chaincode.delegation

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class ChaincodeDelegationTweakServiceFake : ChaincodeDelegationTweakService {
  val psbtMock = Psbt(
    id = "psbt-id",
    base64 = "delegated-base-64",
    fee = Fee(amount = BitcoinMoney.sats(10_000)),
    baseSize = 10000,
    numOfInputs = 1,
    amountSats = 10000UL
  )

  var psbtWithTweaksResult: Result<Psbt, ChaincodeDelegationError> =
    Ok(psbtMock)
  val psbtWithTweaksCalls = mutableListOf<Psbt>()

  var sweepPsbtWithTweaksResult: Result<Psbt, ChaincodeDelegationError> =
    Ok(psbtMock.copy(base64 = "sweep-tweaked-psbt"))

  var migrationSweepPsbtWithTweaksResult: Result<Psbt, ChaincodeDelegationError> =
    Ok(psbtMock.copy(base64 = "migration-tweaked-psbt"))

  override suspend fun psbtWithTweaks(
    psbt: Psbt,
    appSpendingPrivateKey: ExtendedPrivateKey,
    spendingKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> {
    return psbtWithTweaksResult
  }

  override suspend fun psbtWithTweaks(psbt: Psbt): Result<Psbt, ChaincodeDelegationError> {
    psbtWithTweaksCalls += psbt
    return psbtWithTweaksResult
  }

  override suspend fun sweepPsbtWithTweaks(
    psbt: Psbt,
    sourceKeyset: SpendingKeyset,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> = sweepPsbtWithTweaksResult

  override suspend fun migrationSweepPsbtWithTweaks(
    psbt: Psbt,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> = migrationSweepPsbtWithTweaksResult

  fun reset() {
    psbtWithTweaksResult = Ok(psbtMock)
    psbtWithTweaksCalls.clear()
    sweepPsbtWithTweaksResult = Ok(psbtMock.copy(base64 = "sweep-tweaked-psbt"))
    migrationSweepPsbtWithTweaksResult = Ok(psbtMock.copy(base64 = "migration-tweaked-psbt"))
  }
}
