package build.wallet.bitkey.relationships

import build.wallet.bitkey.socrec.SocRecKey

/**
 * PAKE key used during the Social Recovery enrollment process to perform key confirmation and
 * establish a secure channel with the Protected Customer. This key is owned by the Trusted Contact
 * and exists only ephemerally for the duration of the exchange. It is not persisted.
 */
interface TrustedContactEnrollmentPakeKey : SocRecKey, TrustedContactPakeKey
