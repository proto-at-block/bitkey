package build.wallet.home

data class GettingStartedTask(
  val id: TaskId,
  val state: TaskState,
) {
  enum class TaskId {
    AddBitcoin,
    EnableSpendingLimit,
  }

  enum class TaskState {
    Incomplete,
    Complete,
  }
}
