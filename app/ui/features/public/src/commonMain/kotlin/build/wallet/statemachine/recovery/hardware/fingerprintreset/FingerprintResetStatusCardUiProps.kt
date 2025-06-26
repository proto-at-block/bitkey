package build.wallet.statemachine.recovery.hardware.fingerprintreset

import build.wallet.bitkey.account.FullAccount

data class FingerprintResetStatusCardUiProps(
  val account: FullAccount,
  val onClick: (actionId: String) -> Unit,
)
