package build.wallet.recovery.socrec

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result

class SocRecStartedChallengeDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : SocRecStartedChallengeDao {
  override suspend fun get(): Result<String?, DbError> {
    return databaseProvider.database()
      .socRecPendingChallengeQueries
      .getPendingChallenge()
      .awaitAsOneOrNullResult()
  }

  override suspend fun set(challengeId: String): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      socRecPendingChallengeQueries.setPendingChallenge(challengeId)
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        socRecPendingChallengeQueries.clear()
      }
  }
}
