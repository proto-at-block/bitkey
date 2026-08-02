/**
 * @file wallet_emulator.h
 * @brief Wallet emulator API for POSIX core-sim
 *
 * This header provides the wallet initialization API used by core-sim.
 * The real wallet operations (derive, sign, seal/unseal) are handled by
 * key_manager_task via IPC routing.
 */

#ifndef WALLET_EMULATOR_H
#define WALLET_EMULATOR_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque context (unused, kept for API compatibility) */
typedef struct fwup_emu_ctx fwup_emu_ctx_t;

/**
 * Wallet operation result.
 */
typedef enum {
  EMU_WALLET_OK = 0,
  EMU_WALLET_ERR_NOT_INITIALIZED = 1,
  EMU_WALLET_ERR_DERIVATION_FAILED = 2,
  EMU_WALLET_ERR_SEAL_FAILED = 3,
  EMU_WALLET_ERR_UNSEAL_FAILED = 4,
  EMU_WALLET_ERR_SIGN_FAILED = 5,
  EMU_WALLET_ERR_INVALID_PARAMS = 6,
} emu_wallet_result_t;

/**
 * Initialize the wallet emulator (LittleFS + WKEK + seed storage).
 * Must be called before any wallet operations.
 *
 * @param ctx Unused, pass NULL
 * @return EMU_WALLET_OK on success
 */
emu_wallet_result_t fwup_emu_wallet_init(fwup_emu_ctx_t* ctx);

/**
 * Set a custom seed for deterministic testing.
 * Must be called BEFORE fwup_emu_wallet_init().
 *
 * @param seed The seed bytes
 * @param seed_len Length of seed (16-64 bytes)
 * @return true on success
 */
bool fwup_emu_set_seed(const uint8_t* seed, size_t seed_len);

/**
 * Reset the wallet emulator state.
 */
void fwup_emu_wallet_reset(void);

/**
 * Save the wallet filesystem to disk.
 * Called after critical operations to persist filesystem state.
 *
 * @return true on success
 */
bool wallet_fs_save(void);

/**
 * Wipe the wallet filesystem persistence (delete saved state from disk).
 * Called during device wipe.
 *
 * @return true on success
 */
bool wallet_fs_wipe(void);

#ifdef __cplusplus
}
#endif

#endif /* WALLET_EMULATOR_H */
