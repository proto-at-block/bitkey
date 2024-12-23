package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8eClient
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.mapResult
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.minutes

@BitkeyInject(AppScope::class)
class InheritanceClaimsRepositoryImpl(
  accountService: AccountService,
  private val inheritanceClaimsDao: InheritanceClaimsDao,
  private val retrieveInheritanceClaimsF8eClient: RetrieveInheritanceClaimsF8eClient,
  private val inheritanceFeatureFlag: InheritanceFeatureFlag,
  stateScope: CoroutineScope,
) : InheritanceClaimsRepository {
  /**
   * Current account status.
   */
  private val account = accountService.accountStatus()
    .mapResult { it as? AccountStatus.ActiveAccount }
    .mapNotNull { it.get()?.account as? FullAccount }
    .distinctUntilChanged()

  /**
   * Local database stored claims.
   *
   * This list is not complete, but has the benefit of being offline and is
   * therefore fetched eagerly to improve responsiveness.
   */
  private val databaseClaims = combine(
    inheritanceClaimsDao.pendingBeneficiaryClaims,
    inheritanceClaimsDao.pendingBenefactorClaims
  ) { beneficiaryClaims, benefactorClaims ->
    coroutineBinding {
      InheritanceClaims(
        benefactorClaims = benefactorClaims.bind(),
        beneficiaryClaims = beneficiaryClaims.bind()
      )
    }
  }.stateIn(
    scope = stateScope,
    started = SharingStarted.Eagerly,
    initialValue = null
  )

  /**
   * In-memory cache for claim data.
   *
   * This is the latest full set of claims from either the server or by
   * local modification.
   */
  private val claimsState = MutableStateFlow<Result<InheritanceClaims, Error>?>(null)

  /**
   * Latest claims from the server.
   *
   * This is a cold flow that will continue to emit latest claims from the
   * server while subscribed to.
   */
  private val serverClaims: Flow<Result<InheritanceClaims, Error>> =
    account.flatMapLatest { account ->
      flow {
        while (currentCoroutineContext().isActive) {
          retrieveInheritanceClaimsF8eClient
            .retrieveInheritanceClaims(
              account.config.f8eEnvironment,
              account.accountId
            ).let { emit(it) }
          delay(1.minutes)
        }
      }
    }.distinctUntilChanged()

  /**
   * Flow of the latest available data about user claims.
   *
   * Data from this flow may be stale (only locally saved data) and will
   * continue to emit updates while observed.
   *
   * This flow will not emit until a value is available, either by local
   * or server updates, and does not emit a default empty value initially
   * before load.
   */
  override val claims: Flow<Result<InheritanceClaims, Error>> =
    inheritanceFeatureFlag.flagValue()
      .flatMapLatest { flag ->
        if (flag.isEnabled()) {
          claimsState.filterNotNull()
            .onStart { databaseClaims.value?.let { emit(it) } }
        } else {
          emptyFlow()
        }
      }
      .shareIn(stateScope, SharingStarted.WhileSubscribed())

  init {
    stateScope.launch {
      inheritanceFeatureFlag.flagValue()
        .collectLatest { flag ->
          if (flag.isEnabled()) {
            syncServerClaims()
          }
        }
    }
  }

  override suspend fun fetchClaims(): Result<InheritanceClaims, Error> {
    if (!inheritanceFeatureFlag.isEnabled()) {
      return Err(Error("Inheritance feature flag is disabled"))
    }

    return serverClaims.first()
      .onSuccess { newClaims ->
        claimsState.value = Ok(newClaims)
        inheritanceClaimsDao.setInheritanceClaims(newClaims)
      }
  }

  /**
   * Update the state of a single inheritance claim.
   */
  override suspend fun updateSingleClaim(claim: BeneficiaryClaim) {
    if (!inheritanceFeatureFlag.isEnabled()) {
      return
    }

    val currentClaim = claimsState.value
      ?.get()
      ?.beneficiaryClaims
      ?.find { it.claimId == claim.claimId }

    claimsState.value = if (currentClaim == null) {
      claimsState.value?.map { it.copy(beneficiaryClaims = it.beneficiaryClaims + claim) }
    } else {
      claimsState.value?.map { it.copy(beneficiaryClaims = it.beneficiaryClaims - currentClaim + claim) }
    }
  }

  /**
   * Sync the latest server data with cache and database.
   *
   * This will only update the database if the server data is successful,
   * and it will only update the in-memory state if there is no existing
   * results.
   */
  private suspend fun syncServerClaims() {
    serverClaims.collect { newClaims ->
      newClaims.logFailure { "Failed to sync new claims" }

      if (newClaims.isOk || claimsState.value?.isOk != true) {
        claimsState.value = newClaims
      }

      newClaims.onSuccess {
        inheritanceClaimsDao.setInheritanceClaims(it)
          .logFailure { "Failed to save new claims to database" }
      }
    }
  }
}
