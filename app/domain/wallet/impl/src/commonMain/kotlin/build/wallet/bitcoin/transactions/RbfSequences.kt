package build.wallet.bitcoin.transactions

/**
 * BIP125 sequence threshold for opt-in RBF signaling.
 */
internal const val BIP125_SEQUENCE_SIGNAL_THRESHOLD: UInt = 0xFFFFFFFEu

/**
 * BIP125 opt‑in RBF sequence value.
 */
internal const val RBF_SEQUENCE: UInt = 0xFFFFFFFDu
