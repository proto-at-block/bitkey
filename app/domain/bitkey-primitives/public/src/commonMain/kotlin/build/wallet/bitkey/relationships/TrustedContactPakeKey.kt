package build.wallet.bitkey.relationships

import build.wallet.crypto.PakeKey

/**
 * Represents a PAKE key belonging to the Recovery Contact, used within the SocRec verification protocol.
 */
interface TrustedContactPakeKey : PakeKey
