package build.wallet.coachmark

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.EventTracker
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.CoachmarksGlobalFeatureFlag
import build.wallet.feature.isEnabled
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

@BitkeyInject(AppScope::class)
class CoachmarkServiceImpl(
  private val coachmarkDao: CoachmarkDao,
  private val accountService: AccountService,
  private val coachmarkVisibilityDecider: CoachmarkVisibilityDecider,
  private val coachmarksGlobalFeatureFlag: CoachmarksGlobalFeatureFlag,
  private val eventTracker: EventTracker,
  private val clock: Clock,
) : CoachmarkService {
  override suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error> =
    coroutineBinding {
      // If the global coachmark kill switch feature flag is enabled, short circuit and return an empty list
      if (coachmarksGlobalFeatureFlag.isEnabled()) {
        return@coroutineBinding emptyList<CoachmarkIdentifier>()
      }

      val accountStatus = accountService
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

      // If we've never seen a coachmark identifier before, create it if criteria are met.
      val newCoachmarkIds = coachmarkIds.filter { id -> existingCoachmarks.none { it.id == id } }
      for (id in newCoachmarkIds) {
        if (coachmarkVisibilityDecider.shouldCreate(id)) {
          createCoachmark(id)
        }
      }

      // Get all coachmarks from the DB again now that we've created the new ones
      val allCoachmarks = coachmarkDao.getAllCoachmarks().bind()
      val visibleCoachmarkIds = mutableListOf<CoachmarkIdentifier>()
      for (coachmark in allCoachmarks) {
        if (coachmarkVisibilityDecider.shouldShow(coachmark)) {
          visibleCoachmarkIds.add(coachmark.id)
        }
      }
      visibleCoachmarkIds
    }

  override suspend fun markCoachmarkAsDisplayed(
    coachmarkId: CoachmarkIdentifier,
  ): Result<Unit, Error> {
    // track that the coachmark was viewed
    eventTracker.track(coachmarkId.action, null)
    return coroutineBinding {
      coachmarkDao.setViewed(coachmarkId)
    }
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
