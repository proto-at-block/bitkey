package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.Authorization
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategy.DelayAndNotify
import bitkey.f8e.privilegedactions.AuthorizationStrategy.OutOfBand
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionInstanceRef
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Clock

suspend fun <Req, Res> PrivilegedActionService<Req, Res>.getFullAccount(): Result<FullAccount, PrivilegedActionError> {
  return accountService.getAccount<FullAccount>()
    .mapError { PrivilegedActionError.IncorrectAccountType }
}

suspend fun <Req, Res> PrivilegedActionService<Req, Res>.getPrivilegedActions(): Result<List<PrivilegedActionInfo>, PrivilegedActionError> {
  val fullAccount = getFullAccount().getOrElse { return Err(it) }
  return privilegedActionF8eClient
    .getPrivilegedActionInstances(fullAccount.config.f8eEnvironment, fullAccount.accountId)
    .mapError { PrivilegedActionError.ServerError(it) }
    .map { instances ->
      instances.map { inst ->
        val status = when (val strat = inst.authorizationStrategy) {
          is DelayAndNotify -> if (strat.delayEndTime > clock.now()) {
            PrivilegedActionStatus.PENDING
          } else {
            PrivilegedActionStatus.AUTHORIZED
          }
          is OutOfBand -> PrivilegedActionStatus.PENDING
        }
        PrivilegedActionInfo(inst, status)
      }
    }
}

suspend fun <Req, Res> PrivilegedActionService<Req, Res>.getPrivilegedActionsByType(
  type: PrivilegedActionType,
): Result<List<PrivilegedActionInfo>, PrivilegedActionError> {
  return getPrivilegedActions().flatMap { infos ->
    Ok(infos.filter { it.instance.privilegedActionType == type })
  }
}

suspend fun <Req, Res> DirectPrivilegedActionService<Req, Res>.createAction(
  request: Req,
): Result<PrivilegedActionInstance, PrivilegedActionError> {
  val fullAccount = getFullAccount().getOrElse { return Err(it) }
  return privilegedActionF8eClient
    .createPrivilegedAction(fullAccount.config.f8eEnvironment, fullAccount.accountId, request)
    .mapError { PrivilegedActionError.ServerError(it) }
}

suspend fun <Req, Res> DirectPrivilegedActionService<Req, Res>.continueAction(
  actionInstance: PrivilegedActionInstance,
): Result<Res, PrivilegedActionError> {
  val fullAccount = getFullAccount().getOrElse { return Err(it) }

  val strat = actionInstance.authorizationStrategy
  if (strat !is DelayAndNotify) {
    return Err(PrivilegedActionError.UnsupportedActionType)
  }

  val token = strat.completionToken
  if (strat.delayEndTime > clock.now() || token.isNullOrBlank()) {
    return Err(PrivilegedActionError.NotAuthorized)
  }

  val auth = Authorization(strat.authorizationStrategyType, token)
  val req = ContinuePrivilegedActionRequest(
    PrivilegedActionInstanceRef(actionInstance.id, auth)
  )

  return privilegedActionF8eClient
    .continuePrivilegedAction(fullAccount.config.f8eEnvironment, fullAccount.accountId, req)
    .mapError { PrivilegedActionError.ServerError(it) }
}

/**
 * Extension function to check if a D+N privileged action is ready to be completed.
 * Returns true if the delay period has ended, false otherwise.
 */
fun PrivilegedActionInstance.isDelayAndNotifyReadyToComplete(clock: Clock): Boolean {
  val delayAndNotifyStrategy = authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
  return delayAndNotifyStrategy?.let { strategy ->
    clock.now() >= strategy.delayEndTime
  } ?: false
}
