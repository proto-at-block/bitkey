package build.wallet.bitkey.socrec

import build.wallet.bitkey.relationships.TrustedContactPakeKey

/**
 * PAKE key used during the Social Recovery process to perform key confirmation and
 * establish a secure channel with the Protected Customer. This key is owned by the Trusted Contact
 * and exists only ephemerally for the duration of the exchange. It is not persisted.
 */
interface TrustedContactRecoveryPakeKey : TrustedContactPakeKey
