package build.wallet.statemachine.cloud

import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

interface LiteAccountCloudSignInAndBackupUiStateMachine :
  StateMachine<LiteAccountCloudSignInAndBackupProps, ScreenModel>

data class LiteAccountCloudSignInAndBackupProps(
  val liteAccount: LiteAccount,
  val onBackupFailed: () -> Unit,
  val onBackupSaved: () -> Unit,
  val presentationStyle: ScreenPresentationStyle,
)
