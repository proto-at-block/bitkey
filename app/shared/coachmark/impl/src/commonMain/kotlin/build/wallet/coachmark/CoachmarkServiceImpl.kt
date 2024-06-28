package build.wallet.coachmark

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class CoachmarkServiceImpl(
  private val coachmarkDao: CoachmarkDao,
  private val accountRepository: AccountRepository,
  private val coachmarkFeatureFlagVisibilityDecider: CoachmarkFeatureFlagVisibilityDecider,
  private val clock: Clock,
) : CoachmarkService {
  override suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error> =
    coroutineBinding {
      val accountStatus = accountRepository
        .accountStatus()
        .first()
        .bind()

      // Currently we only show coachmarks for full account use cases
      // in the future we can add more conditions here based on the coachmark id
      // early return if the account is not a full account
      if (AccountStatus.accountFromAccountStatus(accountStatus) !is FullAccount) {
        return@coroutineBinding emptyList<CoachmarkIdentifier>()
      }

      // Get all existing coachmarks from the DB
      val existingCoachmarks = coachmarkDao
        .getAllCoachmarks()
        .bind()

      // If we've never seen a coachmark identifier before, create it.
      coachmarkIds
        .filter { id -> existingCoachmarks.none { it.coachmarkId == id.string } }
        .forEach { createCoachmark(it) }

      // Get all coachmarks from the DB again now that we've created the new ones
      coachmarkDao
        .getAllCoachmarks()
        .map { coachmarks ->
          coachmarks
            .filter { coachmark ->
              val featureFlagged = coachmarkFeatureFlagVisibilityDecider
                .shouldShow(coachmark.coachmarkId)
              coachmark.expiration > clock.now() && !coachmark.viewed && featureFlagged
            }.map { coachmark ->
              coachmarkIds.first { it.string == coachmark.coachmarkId }
            }
        }.bind()
    }

  override suspend fun markCoachmarkAsDisplayed(
    coachmarkId: CoachmarkIdentifier,
  ): Result<Unit, Error> =
    coroutineBinding {
      coachmarkDao.setViewed(coachmarkId)
    }

  override suspend fun resetCoachmarks(): Result<Unit, Error> =
    coroutineBinding {
      coachmarkDao.resetCoachmarks()
    }

  private suspend fun createCoachmark(coachmarkId: CoachmarkIdentifier): Result<Unit, Error> =
    coroutineBinding {
      // expiration date is 2 weeks from creation
      val expiresAt = clock.now() + 14.days
      coachmarkDao.insertCoachmark(coachmarkId, expiresAt)
    }
}
