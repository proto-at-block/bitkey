package build.wallet.f8e.client.plugins

import build.wallet.analytics.v1.PlatformInfo
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.platform.device.DeviceInfo
import io.ktor.client.request.*
import io.ktor.util.*

/**
 * This file contains all the various Ktor Request Attributes for
 * configuring plugins, and simplified methods for direct configuration.
 */

val F8eEnvironmentAttribute = AttributeKey<F8eEnvironment>("f8-environment")
val AccountIdAttribute = AttributeKey<AccountId>("account-id")
val AuthTokenScopeAttribute = AttributeKey<AuthTokenScope>("auth-token-scope")
val AppAuthKeyAttribute = AttributeKey<PublicKey<out AppAuthKey>>("app-auth-key")
val HwProofOfPossessionAttribute = AttributeKey<HwFactorProofOfPossession>("hw-proof-of-possession")
val DeviceInfoAttribute = AttributeKey<DeviceInfo>("device-info")
val PlatformInfoAttribute = AttributeKey<PlatformInfo>("platform-info")
val CheckReachabilityAttribute = AttributeKey<Boolean>("check-reachability")

/**
 * Configure the request to target the [environment],
 * required for all F8e requests.
 */
fun HttpRequestBuilder.withEnvironment(environment: F8eEnvironment) {
  attributes.put(F8eEnvironmentAttribute, environment)
}

fun HttpRequestBuilder.withAccountId(
  accountId: AccountId,
  tokenScope: AuthTokenScope = AuthTokenScope.Global,
) {
  attributes.put(AccountIdAttribute, accountId)
  attributes.put(AuthTokenScopeAttribute, tokenScope)
}

/**
 * Disable reporting of the requests to the network reachability system.
 */
fun HttpRequestBuilder.disableReachabilityCheck() {
  attributes.put(CheckReachabilityAttribute, false)
}

fun HttpRequestBuilder.withHardwareFactor(hwFactorProofOfPossession: HwFactorProofOfPossession) {
  attributes.put(HwProofOfPossessionAttribute, hwFactorProofOfPossession)
}

fun HttpRequestBuilder.withAppAuthKey(appAuthKey: PublicKey<out AppAuthKey>) {
  attributes.put(AppAuthKeyAttribute, appAuthKey)
}
