package build.wallet.nfc.platform

import okio.ByteString

/**
 * Context required to sweep-sign inputs belonging to a non-current account
 * through [NfcCommands.sweepTransaction] on W3 hardware.
 *
 * The regular signing path reconstructs cosigner pubkeys from the stored
 * (current) keyset's xpubs. For a sweep from an OLD account, the firmware
 * cannot derive the old app + server pubkeys on its own, so the app supplies
 * them here. HW pubkeys are derived on-device from master at
 * [oldAccountIndex].
 *
 * Providing incorrect xpubs produces a signature that fails on-chain; it
 * cannot be used to steal funds because the HW still only signs with its
 * own key.
 *
 * Named `SweepSigningContext` to avoid collision with
 * [build.wallet.recovery.sweep.SweepContext] which describes the high-level
 * recovery-flow context (lost-app vs lost-hw vs upgrade, etc.).
 */
data class SweepSigningContext(
  /** Account index of the OLD keyset being swept from. */
  val oldAccountIndex: UInt,
  /** OLD account's app xpub at depth 3 (m/84'/coin'/old_account'). Firmware
   *  derives the per-input child pubkey off this via `[change, addr]`. */
  val oldAppXpub: SweepXpub,
  /** OLD account's server ROOT xpub at depth 0. Server keys use chain-code
   *  delegation — firmware derives the per-input child pubkey off this root
   *  via `[84, coin, 0, change, addr]` (account index is hardcoded to 0 for
   *  server derivation regardless of the real account; the account-specific
   *  tweak is baked into the root xpub itself at keyset creation time). */
  val oldServerXpub: SweepXpub,
)

/** BIP32 xpub material: 33-byte compressed pubkey + 32-byte chaincode.
 *  The depth this represents depends on the field it's attached to (see
 *  [SweepSigningContext]); the wire format is the same either way. */
data class SweepXpub(
  val pubkey: ByteString,
  val chaincode: ByteString,
)
