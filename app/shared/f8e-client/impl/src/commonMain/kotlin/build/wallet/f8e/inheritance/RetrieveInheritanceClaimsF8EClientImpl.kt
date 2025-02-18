package build.wallet.f8e.inheritance

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class RetrieveInheritanceClaimsF8EClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RetrieveInheritanceClaimsF8eClient {
  override suspend fun retrieveInheritanceClaims(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<InheritanceClaims, Error> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<RetrieveInheritanceClaimResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/recovery/inheritance/claims") {
          withDescription("Fetch inheritance claims")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId, AuthTokenScope.Recovery)
        }
      }
      .map { response ->
        response.toInheritanceClaims()
      }
  }

  private fun RetrieveInheritanceClaimResponseBody.toInheritanceClaims() =
    InheritanceClaims(
      benefactorClaims = claimsAsBenefactor,
      beneficiaryClaims = claimsAsBeneficiary
    )
}

@Serializable
internal data class RetrieveInheritanceClaimResponseBody(
  @SerialName("claims_as_benefactor")
  val claimsAsBenefactor: List<
    @Serializable(with = BenefactorClaimSerializer::class)
    BenefactorClaim
  >,
  @SerialName("claims_as_beneficiary")
  val claimsAsBeneficiary: List<
    @Serializable(with = BeneficiaryClaimSerializer::class)
    BeneficiaryClaim
  >,
) : RedactedResponseBody
