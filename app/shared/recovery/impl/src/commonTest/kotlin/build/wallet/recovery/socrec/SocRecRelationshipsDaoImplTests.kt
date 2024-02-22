package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.TrustedContactEnrollmentKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.CurveType
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

class SocRecRelationshipsDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: SocRecRelationshipsDao

  val socRecRelationships =
    SocRecRelationships(
      invitations =
        listOf(
          Invitation(
            recoveryRelationshipId = "c",
            trustedContactAlias = TrustedContactAlias("Dad"),
            token = "dadToken",
            expiresAt = Instant.DISTANT_FUTURE
          ),
          Invitation(
            recoveryRelationshipId = "d",
            trustedContactAlias = TrustedContactAlias("Bro"),
            token = "broToken",
            expiresAt = Instant.DISTANT_FUTURE
          ),
          Invitation(
            recoveryRelationshipId = "f",
            trustedContactAlias = TrustedContactAlias("Gramps"),
            token = "grampsToken",
            expiresAt = Instant.fromEpochMilliseconds(0)
          )
        ),
      trustedContacts =
        listOf(
          TrustedContact(
            recoveryRelationshipId = "a",
            trustedContactAlias = TrustedContactAlias("Mom"),
            identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("momKey"))
          ),
          TrustedContact(
            recoveryRelationshipId = "b",
            trustedContactAlias = TrustedContactAlias("Sis"),
            identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("sisKey"))
          )
        ),
      protectedCustomers =
        immutableListOf(
          ProtectedCustomer(
            recoveryRelationshipId = "d",
            alias = ProtectedCustomerAlias("Aunt")
          ),
          ProtectedCustomer(
            recoveryRelationshipId = "e",
            alias = ProtectedCustomerAlias("Uncle")
          )
        ),
      unendorsedTrustedContacts =
        listOf(
          UnendorsedTrustedContact(
            recoveryRelationshipId = "g",
            trustedContactAlias = TrustedContactAlias("Cousin"),
            identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("cousinKey")),
            identityPublicKeyMac = "cousinKeyMac",
            enrollmentKey =
              TrustedContactEnrollmentKey(
                AppKey.fromPublicKey("cousinEnrollmentKey", CurveType.Curve25519)
              ),
            enrollmentKeyConfirmation = "cousinEnrollmentKeyConfirmation"
          )
        )
    )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = SocRecRelationshipsDaoImpl(databaseProvider)
  }

  test("get empty social relationships") {
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(
        SocRecRelationships(
          invitations = emptyList(),
          trustedContacts = emptyList(),
          protectedCustomers = immutableListOf(),
          unendorsedTrustedContacts = emptyList()
        )
      )
    )
  }

  test("set social relationships") {
    dao.setSocRecRelationships(socRecRelationships)
    dao.socRecRelationships().first().shouldBeEqual(Ok(socRecRelationships))
  }

  test("set social relationships and handle accepted invites") {
    dao.setSocRecRelationships(socRecRelationships)

    val invitationToAccept = socRecRelationships.invitations[0]
    val newTrustedContact =
      TrustedContact(
        recoveryRelationshipId = invitationToAccept.recoveryRelationshipId,
        trustedContactAlias = invitationToAccept.trustedContactAlias,
        identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("newKey"))
      )

    val socRecRelationshipsWithAcceptedInvite =
      socRecRelationships.copy(
        invitations = socRecRelationships.invitations.filter { it != invitationToAccept },
        trustedContacts = socRecRelationships.trustedContacts + newTrustedContact
      )

    dao.setSocRecRelationships(socRecRelationshipsWithAcceptedInvite)
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationshipsWithAcceptedInvite)
    )
  }

  test("set unendorsed trusted contact does not overwrite authentication state") {
    dao.setSocRecRelationships(socRecRelationships)
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationships)
    )

    val endorsedTc =
      socRecRelationships.unendorsedTrustedContacts[0].copy(
        authenticationState = TrustedContactAuthenticationState.ENDORSED
      )
    val relationshipsWithEndorsedTc =
      socRecRelationships.copy(
        unendorsedTrustedContacts = listOf(endorsedTc)
      )
    dao.setSocRecRelationships(relationshipsWithEndorsedTc)

    // The authentication state should not have changed.
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationships)
    )

    dao.setUnendorsedTrustedContactAuthenticationState(
      endorsedTc.recoveryRelationshipId,
      TrustedContactAuthenticationState.ENDORSED
    )
    // The authentication state should have changed after setting the state directly.
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(relationshipsWithEndorsedTc)
    )
  }
})
