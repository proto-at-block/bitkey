package build.wallet.home

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class HomeUiBottomSheetDaoMock(
  turbine: (String) -> Turbine<Any>,
) : HomeUiBottomSheetDao {
  var homeUiBottomSheetFlow: Flow<HomeUiBottomSheetId?> = flowOf(null)

  override fun currentHomeUiBottomSheet() = homeUiBottomSheetFlow

  val setHomeUiBottomSheetCalls = turbine("setHomeUiBottomSheet calls")
  var setHomeUiBottomSheetReturn = Ok(Unit)

  override suspend fun setHomeUiBottomSheet(sheetId: HomeUiBottomSheetId): Result<Unit, DbError> {
    setHomeUiBottomSheetCalls.add(sheetId)
    return setHomeUiBottomSheetReturn
  }

  val clearHomeUiBottomSheetCalls = turbine("clearHomeUiBottomSheet calls")
  var clearHomeUiBottomSheetReturn = Ok(Unit)

  override suspend fun clearHomeUiBottomSheet(): Result<Unit, DbError> {
    clearHomeUiBottomSheetCalls.add(Unit)
    return clearHomeUiBottomSheetReturn
  }
}
