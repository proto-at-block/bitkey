package build.wallet.ui.app.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TcIdentityKeyAppSignature
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.relationships.TrustedContactKeyCertificate
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.datetime.Instant

@Preview(name = "Recovery Contacts Empty")
@Composable
fun RecoveryContactsListEmptyPreview() {
  PreviewWalletTheme {
    RecoveryContactsListPreviewContent(
      model = previewTrustedContactsListBodyModel()
    )
  }
}

@Preview(name = "Recovery Contacts Populated")
@Composable
fun RecoveryContactsListPopulatedPreview() {
  PreviewWalletTheme {
    RecoveryContactsListPreviewContent(
      model =
        previewTrustedContactsListBodyModel(
          contacts =
            listOf(
              sampleEndorsedTrustedContact(
                alias = "Bob",
                relationshipId = "bob-contact-id"
              )
            ),
          invitations =
            listOf(
              sampleInvitation(
                alias = "Alice",
                relationshipId = "alice-invite-id"
              )
            ),
          protectedCustomers =
            immutableListOf(
              sampleProtectedCustomer(
                alias = "Charlie",
                relationshipId = "charlie-protected-id"
              ),
              sampleProtectedCustomer(
                alias = "Dana",
                relationshipId = "dana-protected-id"
              )
            )
        )
    )
  }
}

@Preview(name = "Recovery Contacts Empty (Design System V2)")
@Composable
fun RecoveryContactsListEmptyPreviewDesignSystemV2() {
  PreviewWalletTheme {
    RecoveryContactsListPreviewContent(
      model = previewTrustedContactsListBodyModel()
    )
  }
}

@Preview(name = "Recovery Contacts Populated (Design System V2)")
@Composable
fun RecoveryContactsListPopulatedPreviewDesignSystemV2() {
  PreviewWalletTheme {
    RecoveryContactsListPreviewContent(
      model =
        previewTrustedContactsListBodyModel(
          contacts =
            listOf(
              sampleEndorsedTrustedContact(
                alias = "Bob",
                relationshipId = "bob-contact-id"
              )
            ),
          invitations =
            listOf(
              sampleInvitation(
                alias = "Alice",
                relationshipId = "alice-invite-id"
              )
            ),
          protectedCustomers =
            immutableListOf(
              sampleProtectedCustomer(
                alias = "Charlie",
                relationshipId = "charlie-protected-id"
              ),
              sampleProtectedCustomer(
                alias = "Dana",
                relationshipId = "dana-protected-id"
              )
            )
        )
    )
  }
}

@Composable
private fun RecoveryContactsListPreviewContent(model: TrustedContactsListBodyModel) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(WalletTheme.colors.background)
  ) {
    FormScreen(model = model)
  }
}

private fun previewTrustedContactsListBodyModel(
  contacts: List<EndorsedTrustedContact> = emptyList(),
  invitations: List<Invitation> = emptyList(),
  protectedCustomers: List<ProtectedCustomer> = emptyList(),
): TrustedContactsListBodyModel {
  return TrustedContactsListBodyModel(
    contacts = contacts,
    invitations = invitations,
    protectedCustomers = protectedCustomers,
    now = 0L,
    onAddPressed = {},
    onContactPressed = {},
    onProtectedCustomerPressed = {},
    onAcceptInvitePressed = {},
    onBackPressed = {}
  )
}

private fun sampleEndorsedTrustedContact(
  alias: String,
  relationshipId: String,
): EndorsedTrustedContact {
  return EndorsedTrustedContact(
    relationshipId = relationshipId,
    trustedContactAlias = TrustedContactAlias(alias = alias),
    keyCertificate = TrustedContactKeyCertificate(
      delegatedDecryptionKey = PublicKey(""),
      appGlobalAuthPublicKey = PublicKey(""),
      hwAuthPublicKey = HwAuthPublicKey(Secp256k1PublicKey("")),
      appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature(""),
      trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature("")
    ),
    authenticationState = VERIFIED,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
}

private fun sampleInvitation(
  alias: String,
  relationshipId: String,
): Invitation {
  return Invitation(
    relationshipId = relationshipId,
    trustedContactAlias = TrustedContactAlias(alias),
    roles = setOf(TrustedContactRole.SocialRecoveryContact),
    code = "$relationshipId-code",
    codeBitLength = 20,
    expiresAt = Instant.DISTANT_FUTURE
  )
}

private fun sampleProtectedCustomer(
  alias: String,
  relationshipId: String,
): ProtectedCustomer {
  return ProtectedCustomer(
    relationshipId = relationshipId,
    alias = ProtectedCustomerAlias(alias),
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
}
