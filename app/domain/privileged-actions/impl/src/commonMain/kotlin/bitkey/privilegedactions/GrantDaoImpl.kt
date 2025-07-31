package bitkey.privilegedactions

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.GrantEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class GrantDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val clock: Clock,
) : GrantDao {
  override suspend fun saveGrant(grant: Grant): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        grantQueries.insertGrant(
          action = grant.getGrantAction(),
          version = grant.version.toLong(),
          serializedRequest = grant.serializedRequest,
          signature = grant.signature,
          createdAt = clock.now()
        )
      }
      .logFailure { "Failed to save grant for action: ${grant.getGrantAction()}" }
  }

  override suspend fun getGrantByAction(action: GrantAction): Result<Grant?, DbError> {
    return databaseProvider.database()
      .grantQueries
      .getGrantByAction(action)
      .awaitAsOneOrNullResult()
      .map { entity -> entity?.toGrant() }
      .logFailure { "Failed to get grant by action: $action" }
  }

  override suspend fun deleteGrantByAction(action: GrantAction): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        grantQueries.deleteGrantByAction(action)
      }
      .logFailure { "Failed to delete grant by action: $action" }
  }

  override fun grantByAction(action: GrantAction): Flow<Grant?> {
    return flow {
      databaseProvider.database()
        .grantQueries
        .getGrantByAction(action)
        .asFlowOfOneOrNull()
        .map { result -> result.get()?.toGrant() }
        .collect(::emit)
    }
  }

  /**
   * Converts database entity to domain Grant object.
   */
  private fun GrantEntity.toGrant(): Grant {
    return Grant(
      version = this.version.toByte(),
      serializedRequest = this.serializedRequest,
      signature = this.signature
    )
  }
}
