package build.wallet.onboarding

import build.wallet.bitkey.account.LiteAccount

/**
 * The context in which the Full Account is being created.
 * We either create a Full Account when we are creating a brand new account, or we
 * "create" a Full Account by upgrading an existing Lite Account to a Full Account.
 */
sealed interface CreateFullAccountContext {
  data object NewFullAccount : CreateFullAccountContext

  data class LiteToFullAccountUpgrade(val liteAccount: LiteAccount) : CreateFullAccountContext
}
