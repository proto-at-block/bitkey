package build.wallet.home

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class HomeUiBottomSheetDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : HomeUiBottomSheetDao {
  private suspend fun database() = databaseProvider.database()

  override fun currentHomeUiBottomSheet() =
    flow {
      databaseProvider.database()
        .homeUiBottomSheetQueries
        .getHomeUiBottomSheet()
        .asFlowOfOneOrNull()
        .map { result ->
          result.logFailure { "Failed to load home bottom sheet ID from database" }
          result.map { it?.sheetId }.get()
        }
        .distinctUntilChanged()
        .collect(::emit)
    }

  override suspend fun setHomeUiBottomSheet(sheetId: HomeUiBottomSheetId) =
    database().homeUiBottomSheetQueries.awaitTransaction {
      setHomeUiBottomSheet(sheetId)
    }.logFailure { "Failed to set home bottom sheet ID to database" }

  override suspend fun clearHomeUiBottomSheet() =
    database().homeUiBottomSheetQueries
      .awaitTransaction { clearHomeUiBottomSheet() }
      .logFailure { "Failed to clear home bottom sheet ID from database" }
}
