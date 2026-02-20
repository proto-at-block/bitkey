package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.money.BitcoinMoney
import uniffi.bdk.Amount
import uniffi.bdk.CreateTxException
import uniffi.bdk.ElectrumException
import uniffi.bdk.OutPoint
import uniffi.bdk.Script
import uniffi.bdk.SignerException
import uniffi.bdk.TxIn
import uniffi.bdk.TxOut
import uniffi.bdk.Txid
import kotlin.math.ceil
import uniffi.bdk.FeeRate as BdkV2FeeRate

/**
 * Converts BDK v2's [TxIn] to our legacy [BdkTxIn] type.
 */
internal fun TxIn.toBdkTxIn(): BdkTxIn {
  return BdkTxIn(
    outpoint = BdkOutPoint(
      txid = previousOutput.txid.toString(),
      vout = previousOutput.vout
    ),
    sequence = sequence,
    witness = witness.map { bytes -> bytes.map { it.toUByte() } }
  )
}

/**
 * Converts BDK v2's [TxOut] to our legacy [BdkTxOut] type.
 */
internal fun TxOut.toBdkTxOut(): BdkTxOut {
  return BdkTxOut(
    value = value.toSat(),
    scriptPubkey = scriptPubkey.toBdkScript()
  )
}

/**
 * Converts BDK v2's [Script] to our legacy [BdkScript] type.
 */
internal fun Script.toBdkScript(): BdkScript {
  return object : BdkScript {
    override val rawOutputScript: List<UByte> = toBytes().map { it.toUByte() }
  }
}

/**
 * Converts our legacy [BdkScript] to BDK v2's [Script].
 */
internal fun BdkScript.toBdkV2Script(): Script {
  return Script(rawOutputScript.map { it.toByte() }.toByteArray())
}

/**
 * Converts our domain [FeeRate] to BDK v2's [BdkV2FeeRate].
 *
 * Use sat/kwu with ceiling to avoid precision loss from integer sat/vB
 * (1 sat/vB = 250 sat/kwu).
 * Example: 3.004 sat/vB → 751 sat/kwu (vs 1000 sat/kwu via sat/vB rounding).
 */
internal fun FeeRate.toBdkV2FeeRate(): BdkV2FeeRate {
  val satsPerKwu = ceil(satsPerVByte.toDouble() * 250.0).toULong()
  return BdkV2FeeRate.fromSatPerKwu(satsPerKwu)
}

/**
 * Maps BDK v2's [CreateTxException], [ElectrumException], and [SignerException] to our legacy [BdkError] types.
 */
internal fun Throwable.toBdkError(): BdkError {
  return when (this) {
    is ElectrumException -> BdkError.Electrum(this, message)
    is CreateTxException.InsufficientFunds -> BdkError.InsufficientFunds(this, message)
    is CreateTxException.FeeRateTooLow -> BdkError.FeeRateTooLow(this, message)
    is CreateTxException.FeeTooLow -> BdkError.FeeTooLow(this, message)
    is CreateTxException.RbfSequenceCsv -> BdkError.IrreplaceableTransaction(this, message)
    is CreateTxException.NoUtxosSelected -> BdkError.NoUtxosSelected(this, message)
    is CreateTxException.OutputBelowDustLimit -> BdkError.OutputBelowDustLimit(this, message)
    is CreateTxException.SpendingPolicyRequired -> BdkError.SpendingPolicyRequired(this, message)
    is CreateTxException.Descriptor -> BdkError.Descriptor(this, message)
    is CreateTxException.Psbt -> BdkError.Psbt(this, message)
    is SignerException -> BdkError.Signer(this, message)
    else -> BdkError.Generic(this, message)
  }
}

/**
 * Converts our legacy [BdkOutPoint] to BDK v2's [OutPoint].
 */
internal fun BdkOutPoint.toOutPoint(): OutPoint {
  return OutPoint(
    txid = Txid.fromString(txid),
    vout = vout
  )
}

/**
 * Converts [BitcoinMoney] to BDK v2's [Amount].
 */
internal fun BitcoinMoney.toBdkV2Amount(): Amount {
  return Amount.fromSat(fractionalUnitValue.longValue().toULong())
}
