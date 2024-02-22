package build.wallet.statemachine.dev.logs

import kotlinx.collections.immutable.ImmutableList

data class LogsModel(
  val logRows: ImmutableList<LogRowModel>,
)

data class LogRowModel(
  val dateTime: String,
  val level: String,
  val tag: String,
  val isError: Boolean,
  val message: String,
  val throwableDescription: String? = null,
)
