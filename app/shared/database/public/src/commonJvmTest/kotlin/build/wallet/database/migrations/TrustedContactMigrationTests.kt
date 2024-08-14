package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec

class TrustedContactMigrationTests : FunSpec({
  test("Data migrated from socrec tables") {
    usingDatabaseWithFixtures(14) {
      tableShouldNotExist("socRecTrustedContactEntity")
      tableAtRow("trustedContactEntity", 0) {
        // Migrated Data:
        valueShouldBe("rowId", "1")
        valueShouldBe("trustedContactAlias", "trustedContactAlias-val")
        valueShouldBe("authenticationState", "AWAITING_VERIFY")
        valueShouldBe("certificate", "certificate-val")
        // Renamed Columns:
        valueShouldBe("relationshipId", "recoveryRelationshipId-val")
        // New Columns:
        valueShouldBe("roles", "SOCIAL_RECOVERY_CONTACT")
      }

      tableShouldNotExist("socRecProtectedCustomerEntity")
      tableAtRow("protectedCustomerEntity", 0) {
        // Migrated Data:
        valueShouldBe("rowId", "1")
        valueShouldBe("alias", "alias-val")
        // Renamed Columns:
        valueShouldBe("relationshipId", "recoveryRelationshipId-val")
        // New Columns:
        valueShouldBe("roles", "SOCIAL_RECOVERY_CONTACT")
      }

      tableShouldNotExist("socRecTrustedContactInvitationEntity")
      tableAtRow("trustedContactInvitationEntity", 0) {
        // Migrated Data:
        valueShouldBe("rowId", "1")
        valueShouldBe("trustedContactAlias", "trustedContactAlias-val")
        valueShouldBe("token", "token-val")
        valueShouldBe("tokenBitLength", "1")
        valueShouldBe("expiresAt", "1")
        // Renamed Columns:
        valueShouldBe("relationshipId", "recoveryRelationshipId-val")
        // New Columns:
        valueShouldBe("roles", "SOCIAL_RECOVERY_CONTACT")
      }

      tableShouldNotExist("socRecUnendorsedTrustedContactEntity")
      tableAtRow("unendorsedTrustedContactEntity", 0) {
        // Migrated Data:
        valueShouldBe("rowId", "1")
        valueShouldBe("trustedContactAlias", "trustedContactAlias-val")
        valueShouldBe("enrollmentPakeKey", "enrollmentPakeKey-val")
        valueShouldBe("enrollmentKeyConfirmation", "enrollmentKeyConfirmation-val")
        valueShouldBe("sealedDelegatedDecryptionKey", "sealedDelegatedDecryptionKey-val")
        valueShouldBe("authenticationState", "authenticationState-val")
        // Renamed Columns:
        valueShouldBe("relationshipId", "recoveryRelationshipId-val")
        // New Columns:
        valueShouldBe("roles", "SOCIAL_RECOVERY_CONTACT")
      }
    }
  }
})
