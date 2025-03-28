package build.wallet.home

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface HomeUiBottomSheetDao {
  /**
   * Returns the currently stored [HomeUiBottomSheetId], or null if none is stored or
   * there was an error retrieving.
   */
  fun currentHomeUiBottomSheet(): Flow<HomeUiBottomSheetId?>

  /**
   * Sets the given [HomeUiBottomSheetId] to be persisted, indicating that it should
   * be shown the next time the Money Home or Settings screen is showing.
   */
  suspend fun setHomeUiBottomSheet(sheetId: HomeUiBottomSheetId): Result<Unit, Error>

  /**
   * Clears the current [HomeUiBottomSheetId].
   * Should be called once the sheet has been shown to not show it again.
   */
  suspend fun clearHomeUiBottomSheet(): Result<Unit, Error>
}
