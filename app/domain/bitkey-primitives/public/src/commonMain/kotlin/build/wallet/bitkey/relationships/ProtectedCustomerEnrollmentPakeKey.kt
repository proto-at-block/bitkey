package build.wallet.bitkey.relationships

import build.wallet.bitkey.socrec.SocRecKey

/**
 * PAKE key used during the Social Recovery enrollment process to perform key confirmation and
 * establish a secure channel with the Recovery Contact. This key is owned by the Protected
 * Customer and persisted for the duration of the exchange. A new key is created per
 * Recovery Contact.
 */
interface ProtectedCustomerEnrollmentPakeKey : SocRecKey, ProtectedCustomerPakeKey
