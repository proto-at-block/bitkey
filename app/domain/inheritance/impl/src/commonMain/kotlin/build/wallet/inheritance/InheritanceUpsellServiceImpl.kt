package build.wallet.inheritance

import bitkey.relationships.Relationships
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.onboarding.OnboardingCompletionDao
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

private const val INHERITANCE_UPSELL_ID = "inheritance_upsell"
private const val ONBOARDING_COMPLETION_ID = "onboarding_completion"

@BitkeyInject(AppScope::class)
class InheritanceUpsellServiceImpl(
  coroutineScope: CoroutineScope,
  private val dao: InheritanceUpsellViewDao,
  private val onboardingCompletionDao: OnboardingCompletionDao,
  private val inheritanceService: InheritanceService,
  private val clock: Clock,
) : InheritanceUpsellService {
  private var hasSeenUpsellFlow = false

  init {
    coroutineScope.launch {
      dao.get(INHERITANCE_UPSELL_ID).onSuccess { viewed ->
        hasSeenUpsellFlow = viewed
      }
    }
  }

  override suspend fun markUpsellAsSeen() {
    dao.insert(INHERITANCE_UPSELL_ID).onSuccess {
      dao.setViewed(INHERITANCE_UPSELL_ID).onSuccess {
        hasSeenUpsellFlow = true
      }
    }
  }

  override suspend fun shouldShowUpsell(): Boolean {
    // Don't show if already seen
    if (hasSeenUpsellFlow) {
      return false
    }

    // Don't show if inheritance is already set up
    val hasActiveInheritance = with(inheritanceService) {
      inheritanceRelationships.firstOrNull()?.let { it != Relationships.EMPTY } == true ||
        claims.firstOrNull()?.isNotEmpty() == true
    }

    if (hasActiveInheritance) {
      return false
    }

    // Check if enough time has passed since onboarding
    var shouldShow = false
    onboardingCompletionDao.getCompletionTimestamp(ONBOARDING_COMPLETION_ID)
      .onSuccess { completionTime ->
        shouldShow = completionTime?.let { nonNullTime ->
          val now = clock.now()
          now.minus(nonNullTime) >= 14.days
        } ?: true // Show immediately for existing users (they won't have an onboarding timestamp)
      }

    return shouldShow
  }

  override suspend fun reset() {
    hasSeenUpsellFlow = false
    dao.insert(INHERITANCE_UPSELL_ID)
  }
}
