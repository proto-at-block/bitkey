package bitkey.f8e.verify

import bitkey.verification.VerificationThreshold
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney

data class TxVerificationUpdateRequest(
  val threshold: VerificationThreshold,
  val amountBtc: BitcoinMoney?,
  val hwFactorProofOfPossession: HwFactorProofOfPossession,
  val useBip177: Boolean,
)
