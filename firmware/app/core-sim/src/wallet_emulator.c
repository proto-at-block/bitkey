/**
 * @file wallet_emulator.c
 * @brief Wallet operations for firmware emulator
 *
 * This implements wallet operations using the real firmware wallet/bip32 libraries
 * for POSIX builds. Seeds can be injected from tests for deterministic behavior.
 *
 * ## Initialization Chain
 *
 * The initialization follows the same pattern as firmware tests (seed_test.c):
 * 1. lfs_emubd_create() - Create RAM-backed block device
 * 2. Try to restore filesystem from disk, OR format fresh
 * 3. lfs_mount() - Mount the filesystem
 * 4. set_lfs() - Set the global LFS pointer for filesystem operations
 * 5. wkek_lazy_init() - Initialize WKEK (auto-generates via crypto_random)
 * 6. mempool_create() / wallet_init() - Initialize wallet memory pool
 * 7. wkek_encrypt_and_store(SEED_PATH, seed) - Store the seed (injected or default)
 *
 * ## Persistence
 *
 * The LittleFS filesystem is persisted to disk at ~/.core-sim/wallet_fs.bin.
 * On startup, we restore from disk if available, preserving wallet state
 * (seed, derived keys, WKEK) across emulator restarts.
 *
 * ## Deterministic Testing
 *
 * For deterministic testing, call fwup_emu_set_seed() BEFORE any wallet operations.
 * If no seed is set, the default BIP32 Test Vector 4 seed is used.
 */

#include "wallet_emulator.h"

#include "bd/lfs_emubd.h"
#include "filesystem.h"
#include "lfs.h"
#include "mempool.h"
#include "seed_impl.h"
#include "sim_persistence.h"
#include "wallet.h"
#include "wkek.h"

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define WALLET_FS_FILE "wallet_fs.bin"

/* LittleFS config matching firmware tests (wallet_test.c, seed_test.c) */
#define FS_BLOCK_CYCLES   (500)
#define FS_LOOKAHEAD_SIZE (128)
#define FLASH_PAGE_SIZE   (0x00002000UL)

static lfs_t lfs_instance;
static lfs_emubd_t emubd = {0};
static uint8_t lfs_read_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_prog_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_lookahead_buf[FS_LOOKAHEAD_SIZE];
static bool filesystem_initialized = false;
static bool wallet_fs_dirty = false;

static int wallet_fs_prog(const struct lfs_config* c, lfs_block_t block, lfs_off_t off,
                          const void* buffer, lfs_size_t size) {
  int err = lfs_emubd_prog(c, block, off, buffer, size);
  if (err == 0 && filesystem_initialized) {
    wallet_fs_dirty = true;
  }
  return err;
}

static int wallet_fs_erase(const struct lfs_config* c, lfs_block_t block) {
  int err = lfs_emubd_erase(c, block);
  if (err == 0 && filesystem_initialized) {
    wallet_fs_dirty = true;
  }
  return err;
}

static int wallet_fs_sync(const struct lfs_config* c) {
  int err = lfs_emubd_sync(c);
  if (err == 0 && wallet_fs_dirty) {
    wallet_fs_save();
  }
  return err;
}

/* Default seed: BIP32 Test Vector 4 for deterministic key derivation */
static const uint8_t default_seed[] = {
  0x3d, 0xdd, 0x56, 0x02, 0x28, 0x58, 0x99, 0xa9, 0x46, 0x11, 0x45, 0x06, 0x15, 0x7c, 0x79, 0x97,
  0xe5, 0x44, 0x45, 0x28, 0xf3, 0x00, 0x3f, 0x61, 0x34, 0x71, 0x21, 0x47, 0xdb, 0x19, 0xb6, 0x78};
#define DEFAULT_SEED_SIZE 32

static uint8_t custom_seed[64] = {0};
static size_t custom_seed_size = 0;
static mempool_t* wallet_mempool = NULL;

static const struct lfs_emubd_config emubd_cfg = {
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .erase_size = FLASH_PAGE_SIZE,
  .erase_count = FS_BLOCK_COUNT,
  .erase_value = -1,
};

static const struct lfs_config lfs_cfg = {
  .read = lfs_emubd_read,
  .prog = wallet_fs_prog,
  .erase = wallet_fs_erase,
  .sync = wallet_fs_sync,
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = FS_BLOCK_COUNT,
  .cache_size = FLASH_PAGE_SIZE,
  .lookahead_size = FS_LOOKAHEAD_SIZE,
  .block_cycles = FS_BLOCK_CYCLES,
  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,
  .context = &emubd,
};

static bool wallet_initialized = false;
static bool seed_stored = false;
static bool filesystem_restored = false;  // Track if we restored from disk

/**
 * Save entire LittleFS block device to disk.
 * Called after critical operations to persist filesystem state.
 */
bool wallet_fs_save(void) {
  if (!filesystem_initialized || !sim_persistence_enabled()) {
    return false;
  }

  size_t total_size = FS_BLOCK_COUNT * FLASH_PAGE_SIZE;
  uint8_t* buffer = malloc(total_size);
  if (!buffer) {
    fprintf(stderr, "[wallet_emulator] Failed to allocate buffer for fs save\n");
    return false;
  }

  // Read all blocks from emubd
  for (lfs_block_t block = 0; block < FS_BLOCK_COUNT; block++) {
    int err = lfs_emubd_read(&lfs_cfg, block, 0, buffer + block * FLASH_PAGE_SIZE, FLASH_PAGE_SIZE);
    if (err != 0) {
      fprintf(stderr, "[wallet_emulator] Failed to read block %u: %d\n", block, err);
      free(buffer);
      return false;
    }
  }

  bool ok = sim_persistence_save(WALLET_FS_FILE, buffer, total_size);
  free(buffer);

  if (ok) {
    wallet_fs_dirty = false;
    fprintf(stderr, "[wallet_emulator] Saved filesystem to disk (%zu bytes)\n", total_size);
  } else {
    fprintf(stderr, "[wallet_emulator] Failed to save filesystem to disk\n");
  }

  return ok;
}

/**
 * Restore LittleFS block device from disk.
 * Must be called after lfs_emubd_create() but before lfs_mount().
 */
static bool wallet_fs_restore(void) {
  size_t total_size = FS_BLOCK_COUNT * FLASH_PAGE_SIZE;
  uint8_t* buffer = malloc(total_size);
  if (!buffer) {
    return false;
  }

  if (!sim_persistence_load(WALLET_FS_FILE, buffer, total_size)) {
    free(buffer);
    return false;
  }

  // Erase and program all blocks
  for (lfs_block_t block = 0; block < FS_BLOCK_COUNT; block++) {
    int err = lfs_emubd_erase(&lfs_cfg, block);
    if (err != 0) {
      fprintf(stderr, "[wallet_emulator] Failed to erase block %u: %d\n", block, err);
      free(buffer);
      return false;
    }

    err = lfs_emubd_prog(&lfs_cfg, block, 0, buffer + block * FLASH_PAGE_SIZE, FLASH_PAGE_SIZE);
    if (err != 0) {
      fprintf(stderr, "[wallet_emulator] Failed to program block %u: %d\n", block, err);
      free(buffer);
      return false;
    }
  }

  free(buffer);
  fprintf(stderr, "[wallet_emulator] Restored filesystem from disk (%zu bytes)\n", total_size);
  return true;
}

/**
 * Wipe the filesystem persistence (delete saved state from disk).
 * Called during device wipe or reset.
 */
bool wallet_fs_wipe(void) {
  wallet_fs_dirty = false;
  if (!sim_persistence_enabled()) {
    return true;  // Nothing to wipe
  }
  if (!sim_persistence_delete(WALLET_FS_FILE)) {
    fprintf(stderr, "[wallet_emulator] Warning: Failed to delete persisted filesystem\n");
    return false;
  }
  fprintf(stderr, "[wallet_emulator] Deleted persisted filesystem\n");
  return true;
}

/**
 * Initialize the emulated filesystem.
 * This must be called before any wallet operations or filesystem access.
 */
static bool init_filesystem(void) {
  if (filesystem_initialized) {
    return true;
  }

  if (lfs_emubd_create(&lfs_cfg, &emubd_cfg) != 0) {
    return false;
  }

  // Try to restore from disk first (only if persistence is enabled)
  if (sim_persistence_enabled() && wallet_fs_restore()) {
    // Attempt to mount the restored filesystem
    if (lfs_mount(&lfs_instance, &lfs_cfg) == 0) {
      fs_init_globals();
      set_lfs(&lfs_instance);
      filesystem_initialized = true;
      filesystem_restored = true;
      return true;
    }
    // Mount failed - fall through to format
    fprintf(stderr, "[wallet_emulator] Restored filesystem failed to mount, formatting fresh\n");
  }

  // No saved state or restore failed - format fresh
  if (lfs_format(&lfs_instance, &lfs_cfg) != 0 || lfs_mount(&lfs_instance, &lfs_cfg) != 0) {
    lfs_emubd_destroy(&lfs_cfg);
    return false;
  }

  fs_init_globals();
  set_lfs(&lfs_instance);
  filesystem_initialized = true;
  filesystem_restored = false;
  return true;
}

/**
 * Initialize the wallet subsystems.
 * Creates the mempool and initializes the wallet library.
 * Note: wallet_init() calls wkek_init() internally which creates the WKEK mutex.
 */
static bool init_wallet_subsystems(void) {
  if (wallet_initialized) {
    return true;
  }

  if (!init_filesystem()) {
    return false;
  }

  if (wallet_mempool == NULL) {
#define REGIONS(X)                                                       \
  X(wallet_pool, extended_keys, WALLET_POOL_R0_SIZE, WALLET_POOL_R0_NUM) \
  X(wallet_pool, r1, WALLET_POOL_R1_SIZE, WALLET_POOL_R1_NUM)
    wallet_mempool = mempool_create(wallet_pool);
#undef REGIONS
  }

  /* wallet_init() calls wkek_init() - must be before wkek_lazy_init() */
  wallet_init(wallet_mempool);
  wallet_initialized = true;

  if (!wkek_lazy_init()) {
    return false;
  }

  return true;
}

/**
 * Store the seed (injected or default) encrypted with WKEK.
 * This follows the same pattern as firmware tests (seed_test.c).
 *
 * If we restored from disk and the seed file already exists, skip storing
 * and just mark as initialized.
 */
static bool store_seed(void) {
  if (seed_stored) {
    return true;
  }

  if (!init_wallet_subsystems()) {
    return false;
  }

  // If we restored from disk and seed already exists, we're done
  if (filesystem_restored && wallet_is_initialized()) {
    fprintf(stderr, "[wallet_emulator] Seed already exists (restored from disk)\n");
    seed_stored = true;
    return true;
  }

  // Store the seed
  bool use_custom = custom_seed_size > 0;
  const uint8_t* seed = use_custom ? custom_seed : default_seed;
  size_t size = use_custom ? custom_seed_size : DEFAULT_SEED_SIZE;

  if (!wkek_encrypt_and_store(SEED_PATH, seed, size)) {
    return false;
  }

  seed_stored = true;

  // Save filesystem after storing seed (critical operation)
  wallet_fs_save();

  return true;
}

/**
 * Public API: Set a custom seed for deterministic testing.
 * Must be called BEFORE any wallet operations.
 *
 * @param seed The seed bytes
 * @param seed_len Length of seed (16-64 bytes)
 * @return true on success
 */
bool fwup_emu_set_seed(const uint8_t* seed, size_t seed_len) {
  if (!seed || seed_len < 16 || seed_len > 64) {
    return false;
  }

  if (seed_stored) {
    return false;
  }

  memcpy(custom_seed, seed, seed_len);
  custom_seed_size = seed_len;
  return true;
}

/**
 * Public API: Reset the wallet emulator state.
 * Clears the seed, wipes persisted filesystem, and allows re-initialization.
 */
void fwup_emu_wallet_reset(void) {
  memset(custom_seed, 0, sizeof(custom_seed));
  custom_seed_size = 0;
  seed_stored = false;
  wallet_initialized = false;
  filesystem_initialized = false;
  filesystem_restored = false;
  wallet_fs_dirty = false;
  lfs_unmount(&lfs_instance);
  lfs_emubd_destroy(&lfs_cfg);
  wallet_fs_wipe();
}

emu_wallet_result_t fwup_emu_wallet_init(fwup_emu_ctx_t* ctx) {
  (void)ctx;

  if (!store_seed()) {
    return EMU_WALLET_ERR_NOT_INITIALIZED;
  }

  return EMU_WALLET_OK;
}
