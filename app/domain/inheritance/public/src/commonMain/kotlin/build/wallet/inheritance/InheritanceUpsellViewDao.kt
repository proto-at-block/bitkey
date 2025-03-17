package build.wallet.inheritance

import build.wallet.db.DbError
import com.github.michaelbull.result.Result

interface InheritanceUpsellViewDao {
  suspend fun insert(id: String): Result<Unit, DbError>

  suspend fun setViewed(id: String): Result<Unit, DbError>

  suspend fun get(id: String): Result<Boolean, DbError>
}
