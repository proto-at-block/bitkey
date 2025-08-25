package build.wallet.home

data class GettingStartedTask(
  val id: TaskId,
  val state: TaskState,
) {
  enum class TaskId {
    AddBitcoin,
    EnableSpendingLimit,

    // These tasks were moved to Security Hub as recommendations and do not appear on MoneyHome
    InviteTrustedContact,
    AddAdditionalFingerprint,
  }

  enum class TaskState {
    Incomplete,
    Complete,
  }
}
