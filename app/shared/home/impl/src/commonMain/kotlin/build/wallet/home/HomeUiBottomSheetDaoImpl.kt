package build.wallet.home

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class HomeUiBottomSheetDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : HomeUiBottomSheetDao {
  private val db by lazy { databaseProvider.database() }

  override fun currentHomeUiBottomSheet() =
    db.homeUiBottomSheetQueries
      .getHomeUiBottomSheet()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .map { result ->
        result.logFailure { "Failed to load home bottom sheet ID from database" }
        result.map { it?.sheetId }.get()
      }
      .distinctUntilChanged()

  override suspend fun setHomeUiBottomSheet(sheetId: HomeUiBottomSheetId) =
    db.homeUiBottomSheetQueries.awaitTransaction {
      setHomeUiBottomSheet(sheetId)
    }.logFailure { "Failed to set home bottom sheet ID to database" }

  override suspend fun clearHomeUiBottomSheet() =
    db.homeUiBottomSheetQueries
      .awaitTransaction { clearHomeUiBottomSheet() }
      .logFailure { "Failed to clear home bottom sheet ID from database" }
}
