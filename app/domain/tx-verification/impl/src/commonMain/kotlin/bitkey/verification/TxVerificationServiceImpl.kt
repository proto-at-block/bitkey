package bitkey.verification

import bitkey.f8e.verify.TxVerifyPolicyF8eClient
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.mapResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@BitkeyInject(AppScope::class)
class TxVerificationServiceImpl(
  private val txVerificationDao: TxVerificationDao,
  private val txVerificationF8eClient: TxVerifyPolicyF8eClient,
  private val accountService: AccountService,
) : TxVerificationService {
  override fun getCurrentThreshold(): Flow<Result<VerificationThreshold, Error>> {
    return flow {
      txVerificationDao.getActivePolicy()
        .mapResult { it?.threshold ?: VerificationThreshold.Disabled }
        .collect(::emit)
    }
  }

  override fun getPendingPolicy(): Flow<Result<TxVerificationPolicy.Pending?, Error>> {
    return flow {
      txVerificationDao.getPendingPolicies()
        .mapResult { policies ->
          policies.firstOrNull().also {
            if (policies.size > 1) logError { "More than one policy is stored locally. Will use first policy." }
          }
        }
        .collect(::emit)
    }
  }

  override suspend fun updateThreshold(
    verificationThreshold: VerificationThreshold,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy, Error> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .logFailure { "Update Threshold cannot be called without full account." }
        .bind()
      val apiResult = txVerificationF8eClient.setPolicy(
        fullAccountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        threshold = verificationThreshold,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      ).bind()

      if (apiResult == null) {
        logInfo { "Verification policy is active" }
        txVerificationDao.setActivePolicy(verificationThreshold).bind()
      } else {
        logInfo { "Verification policy is pending." }
        txVerificationDao.createPendingPolicy(
          threshold = verificationThreshold,
          auth = apiResult
        ).bind()
      }
    }
  }
}
