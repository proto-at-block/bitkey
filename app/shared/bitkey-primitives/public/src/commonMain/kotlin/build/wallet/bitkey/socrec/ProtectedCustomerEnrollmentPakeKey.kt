package build.wallet.bitkey.socrec

/**
 * PAKE key used during the Social Recovery enrollment process to perform key confirmation and
 * establish a secure channel with the Trusted Contact. This key is owned by the Protected
 * Customer and persisted for the duration of the exchange. A new key is created per
 * Trusted Contact.
 */
interface ProtectedCustomerEnrollmentPakeKey : SocRecKey, ProtectedCustomerPakeKey
