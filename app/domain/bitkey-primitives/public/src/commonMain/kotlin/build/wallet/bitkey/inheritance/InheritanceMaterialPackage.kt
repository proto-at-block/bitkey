package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.encrypt.XCiphertext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Inheritance data needed for a beneficiary to perform an inheritance claim.
 *
 * This data is uploaded by a customer to be stored in the backend and only
 * released to the Recovery Contact after a delay period. Sensitive data is
 * encrypted with the RC's public key, so no data is accessible until
 * released by the backend to the Recovery Contact.
 */
@Serializable
data class InheritanceMaterialPackage(
  /**
   * The ID of the Recovery Contact relationship for which this is encrypted.
   */
  @SerialName("recovery_relationship_id")
  val relationshipId: RelationshipId,
  /**
   * A symmetric key used to encrypt the App Key, encrypted by the
   * contact's public key.
   */
  @SerialName("sealed_dek")
  val sealedDek: XCiphertext,
  /**
   * The customer's App Key, encrypted with [sealedDek].
   */
  @SerialName("sealed_mobile_key")
  val sealedMobileKey: XCiphertext,
)
