#include "fwup.h"

#include "application_properties.h"
#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "bl_secureboot.h"
#include "ecc.h"
#include "fwup_impl.h"
#include "hash.h"
#include "hex.h"
#include "log.h"
#include "perf.h"
#include "wallet.pb.h"

static struct {
  perf_counter_t* erase;
  perf_counter_t* write;
  perf_counter_t* transfer;
  perf_counter_t* transfer_cmd;
} perf;

typedef enum {
  // Success AF. Just make sure the SUCCESS constant has a large hamming distance from the other
  // values
  FWUP_VERIFY_SUCCESS = 0x50CCE55AF,
  FWUP_VERIFY_SIGNATURE_INVALID = 1,
  FWUP_VERIFY_VERSION_INVALID = 2,
  FWUP_VERIFY_BAD_OFFSET = 3,
  FWUP_VERIFY_ERROR = 4,
  FWUP_VERIFY_CANT_FIND_PROPERTIES = 5,
  FWUP_VERIFY_FLASH_ERROR = 6,
} fwup_verify_status_t;

static void* target_slot_addr = 0;
static void* current_slot_addr = 0;
static void* target_slot_signature = 0;
static uint32_t app_slot_size = 0;
static bool support_bl_upgrade = false;

// App properties for the active slot.
extern ApplicationProperties_t sl_app_properties;

#ifdef EMBEDDED_BUILD
extern char bl_slot_size[];
extern char bl_base_addr[];
extern char bl_signature_addr[];
#else
const uint32_t bl_slot_size = 48 * 1024;
const uint32_t bl_base_addr = 0x08000000;
const uint32_t bl_signature_addr = bl_base_addr + bl_slot_size - 64;
#endif

static fwup_verify_status_t fwup_verify_new_app(uint32_t app_properties_offset,
                                                uint32_t signature_offset);
static fwup_verify_status_t fwup_finalize_bl_upgrade(void);
static bool locate_bl_sl_app_properties(uint32_t bl_base_addr, uint32_t bl_size,
                                        uint32_t* addr_out);
static fwup_verify_status_t verify_new_bootloader(ApplicationCertificate_t* cert, void* data,
                                                  uint32_t length, uint8_t* signature);

static bool is_delta_update(fwpb_fwup_mode mode) {
  return (mode == fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE ||
          mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT);
}

void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool _support_bl_upgrade) {
  perf.erase = perf_create(PERF_ELAPSED, fwup_erase);
  perf.write = perf_create(PERF_ELAPSED, fwup_write);
  perf.transfer = perf_create(PERF_INTERVAL, fwup_transfer);
  perf.transfer_cmd = perf_create(PERF_ELAPSED, fwup_transfer_cmd);

  target_slot_addr = _target_slot_addr;
  current_slot_addr = _current_slot_addr;
  app_slot_size = target_app_slot_size;
  target_slot_signature = _target_slot_signature;
  support_bl_upgrade = _support_bl_upgrade;
}

bool fwup_start(fwpb_fwup_start_cmd* cmd, fwpb_fwup_start_rsp* rsp_out) {
  if (is_delta_update(cmd->mode)) {
    if (!fwup_delta_init(
          (fwup_delta_cfg_t){
            .mode = cmd->mode,
            .patch_size = cmd->patch_size,
            .active_slot_base_addr = (uint32_t)current_slot_addr,
            .target_slot_base_addr = (uint32_t)target_slot_addr,
          },
          perf.write, perf.erase)) {
      LOGE("Delta update initialization failed");
      goto error;
    }
    // Delta updates defer erasing most of flash until patch application. However, we must erase
    // the signature's page now, because we transfer the signature using fwup_transfer() in normal
    // mode.
    if (!fwup_flash_erase_app_signature(perf.erase, target_slot_signature)) {
      goto error;
    }
  } else {
    /* Erase the correct flash slot */
    if (!fwup_flash_erase_app(perf.erase, target_slot_addr)) {
      goto error;
    }
  }

  /* Reset the transfer interval perf counter */
  perf_reset(perf.transfer);

  rsp_out->rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS;
  return true;

error:
  rsp_out->rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
  return false;
}

static bool fwup_handle_write(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out) {
  fwpb_fwup_transfer_cmd_fwup_data_t data = cmd->fwup_data;

  const uint32_t max_chunk_size = sizeof(data.bytes);
  const uint32_t write_address_offset = (cmd->sequence_id * max_chunk_size) + cmd->offset;

  const uint32_t end = write_address_offset + data.size;
  if (end < write_address_offset) {
    LOGE("Wrapped");
    BITLOG_EVENT(fwup_write_err, 1);
    goto error;  // Wrapped
  }

  if (end > app_slot_size) {
    LOGE("End too big");
    BITLOG_EVENT(fwup_write_err, 2);
    goto error;
  }

  /* Write the data */
  void* write_addr = (uint8_t*)target_slot_addr + write_address_offset;
  if (!fwup_flash_write(perf.write, write_addr, data.bytes, data.size)) {
    LOGE("Failed to write flash: %p, %d", write_addr, data.size);
    BITLOG_EVENT(fwup_write_err, 3);
    goto error;
  }

  rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS;
  perf_end(perf.transfer_cmd);
  return true;

error:
  LOGE("fwup_transfer failed");
  rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
  perf_end(perf.transfer_cmd);
  return false;
}

bool fwup_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out) {
  perf_count(perf.transfer);
  perf_begin(perf.transfer_cmd);

  if (is_delta_update(cmd->mode)) {
    return fwup_delta_transfer(cmd, rsp_out);
  } else {
    // For non-delta updates, we write the data to the inactive slot.
    return fwup_handle_write(cmd, rsp_out);
  }
}

bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out) {
  bool result = false;

  if (cmd->bl_upgrade && !support_bl_upgrade) {
    LOGE("BL upgrade not supported");
    rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    return false;
  }

  if (is_delta_update(cmd->mode)) {
    LOGI("Finalizing delta update...");
    fwup_delta_finish(cmd);
  }

  fwup_verify_status_t status = FWUP_VERIFY_ERROR;
  if (cmd->bl_upgrade) {
    status = fwup_finalize_bl_upgrade();
  } else {
    status = fwup_verify_new_app(cmd->app_properties_offset, cmd->signature_offset);
    if (status != FWUP_VERIFY_SUCCESS) {
      BITLOG_EVENT(fwup_verify_err, status);
    }
  }

  BITLOG_EVENT(fwup_finish, status);
  switch (status) {
    case FWUP_VERIFY_VERSION_INVALID:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_VERSION_INVALID;
      break;
    case FWUP_VERIFY_SIGNATURE_INVALID:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SIGNATURE_INVALID;
      break;
    case FWUP_VERIFY_SUCCESS:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS;
      result = true;
      break;
    default:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
      break;
  }
  return result;
}

static NO_OPTIMIZE fwup_verify_status_t verify_new_bootloader(ApplicationCertificate_t* cert,
                                                              void* data, uint32_t length,
                                                              uint8_t* signature) {
  uint8_t hash[SHA256_DIGEST_SIZE] = {0};

  // Verify the cert is signed with secure boot OTP pubkey
  key_handle_t secure_boot_public_key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_INTERNAL_IMMUTABLE,
    .slot = SE_SECURE_BOOT_PUBKEY_SLOT,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  uint32_t hashable_cert_size = offsetof(ApplicationCertificate_t, signature);
  if (!crypto_hash((uint8_t*)cert, hashable_cert_size, hash, sizeof(hash), ALG_SHA256)) {
    return FWUP_VERIFY_SIGNATURE_INVALID;
  }

  secure_bool_t result =
    crypto_ecc_verify_hash(&secure_boot_public_key, hash, sizeof(hash), cert->signature);
  SECURE_IF_FAILIN(result != SECURE_TRUE) { return FWUP_VERIFY_SIGNATURE_INVALID; }

  // Verify the actual bootloader
  memset(hash, 0, sizeof(hash));
  if (!crypto_hash((uint8_t*)data, length, hash, sizeof(hash), ALG_SHA256)) {
    return FWUP_VERIFY_SIGNATURE_INVALID;
  }

  key_handle_t bl_key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)cert->key,
    .key.size = sizeof(cert->key),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  return (crypto_ecc_verify_hash(&bl_key, hash, SHA256_DIGEST_SIZE, (uint8_t*)signature) ==
          SECURE_TRUE)
           ? FWUP_VERIFY_SUCCESS
           : FWUP_VERIFY_SIGNATURE_INVALID;
}

NO_OPTIMIZE static fwup_verify_status_t fwup_verify_new_app(uint32_t app_properties_offset,
                                                            uint32_t signature_offset) {
  if (app_properties_offset > (app_slot_size - sizeof(ApplicationProperties_t)) ||
      signature_offset > (app_slot_size - ECC_SIG_SIZE)) {
    LOGE("Bad offset (%ld, %ld)", app_properties_offset, signature_offset);
    return FWUP_VERIFY_BAD_OFFSET;
  }

#ifndef EMBEDDED_BUILD
  // The below code is not suitable for fuzzing (it's going to read from invalid memory).
  return FWUP_VERIFY_SUCCESS;
#endif

  const ApplicationProperties_t* current_slot_app_props = &sl_app_properties;
  const ApplicationProperties_t* new_app_props =
    (ApplicationProperties_t*)((uint8_t*)target_slot_addr + app_properties_offset);

  LOGI("Verifying firmware update from version %ld to %ld", current_slot_app_props->app.version,
       new_app_props->app.version);

  // First, check the version to ensure no downgrades.
  SECURE_DO_FAILIN((new_app_props->app.version <= current_slot_app_props->app.version), {
    LOGE("Version invalid");
    return FWUP_VERIFY_VERSION_INVALID;
  });

  uint32_t bl_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties((uint32_t)bl_base_addr, (uint32_t)bl_slot_size,
                                   &bl_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in current bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  // Second, check the signature. While secure boot does protect us from booting into
  // invalid slots, checking the signature here allows us to inform that the FWUP failed.
  //
  // NOTE: This function is called `bl_verify_app_slot` because it's the same code we use
  // in the bootloader (hence the prefix). This function checks the application certificate
  // and verifies the image itself.
  ApplicationProperties_t* bl_props = (ApplicationProperties_t*)bl_sl_app_properties_addr;
  SECURE_DO_FAILOUT(
    bl_verify_app_slot(bl_props->cert, (ApplicationProperties_t*)new_app_props, target_slot_addr,
                       app_slot_size - ECC_SIG_SIZE,
                       (uint8_t*)target_slot_addr + signature_offset) == SECURE_TRUE,
    { return FWUP_VERIFY_SUCCESS; });

  LOGE("Signature invalid");
  return FWUP_VERIFY_SIGNATURE_INVALID;
}

static bool locate_bl_sl_app_properties(uint32_t bl_base_addr, uint32_t bl_size,
                                        uint32_t* addr_out) {
  // The sl_app_properties in the *current* bootloader is not known to the app, and
  // must be located by searching for the magic value.
  for (uint32_t addr = bl_base_addr; addr < (bl_base_addr + bl_size); addr += sizeof(uint32_t)) {
    // Grab the magic from *our own* sl_app_properties -- this is consistent across the BL and app
    // and should never change.
    if (memcmp((void*)addr, sl_app_properties.magic, sizeof(sl_app_properties.magic)) == 0) {
      *addr_out = addr;
      return true;
    }
  }

  LOGE("Failed to locate sl_app_properties in bootloader");
  return false;
}

NO_OPTIMIZE static fwup_verify_status_t fwup_finalize_bl_upgrade(void) {
  const uint32_t base_addr = (uint32_t)bl_base_addr;
  const uint32_t slot_size = (uint32_t)bl_slot_size;

  // This is the address of the signature in the *inactive* slot.
  const uint32_t sig_addr = (uint32_t)target_slot_addr + slot_size - ECC_SIG_SIZE;

  LOGD("Parameters: base_addr=%p, slot_size=%ld, sig_addr=%p", (void*)base_addr, slot_size,
       (void*)sig_addr);

  // Get current and new bootloader sl_app_properties, which is not at a fixed location.
  uint32_t current_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties(base_addr, slot_size, &current_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in current bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  const ApplicationProperties_t* current_bl_app_props =
    (ApplicationProperties_t*)current_sl_app_properties_addr;

  uint32_t new_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties((uint32_t)target_slot_addr, slot_size,
                                   &new_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in new bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }
  const ApplicationProperties_t* new_bl_app_props =
    (ApplicationProperties_t*)new_sl_app_properties_addr;

  // Check new bootloader version against the old one
  LOGD("Upgrade from version %ld to %ld", current_bl_app_props->app.version,
       new_bl_app_props->app.version);
  SECURE_DO_FAILIN((new_bl_app_props->app.version <= current_bl_app_props->app.version), {
    LOGE("New bootloader version is not newer than the current bootloader version (%ld <= %ld)",
         new_bl_app_props->app.version, current_bl_app_props->app.version);
    return FWUP_VERIFY_VERSION_INVALID;
  });

  SECURE_DO_FAILIN(
    verify_new_bootloader(current_bl_app_props->cert, (uint8_t*)target_slot_addr,
                          slot_size - ECC_SIG_SIZE,  // Signature must be excluded from hashing
                          (uint8_t*)sig_addr) != FWUP_VERIFY_SUCCESS,
    { return FWUP_VERIFY_SIGNATURE_INVALID; });

  // Erase the BL slot
  if (!fwup_flash_erase_bl(perf.erase, (void*)bl_base_addr, slot_size)) {
    LOGE("Flash erase of bootloader failed");
    return FWUP_VERIFY_FLASH_ERROR;
  }

  LOGD("Done with erase...");

  // Copy the BL from the inactive slot to the BL slot.
  if (!fwup_flash_write(perf.write, (void*)bl_base_addr, target_slot_addr, slot_size)) {
    LOGE("Flash write of bootloader update failed");
    return FWUP_VERIFY_FLASH_ERROR;
  }

  LOGD("Done with write...");

  // Re-verify the BL in the BL slot; note the difference in the parameters.
  SECURE_DO_FAILIN(
    verify_new_bootloader(new_bl_app_props->cert, (uint8_t*)bl_base_addr, slot_size - ECC_SIG_SIZE,
                          (uint8_t*)bl_signature_addr) != FWUP_VERIFY_SUCCESS,
    { return FWUP_VERIFY_SIGNATURE_INVALID; });

  return FWUP_VERIFY_SUCCESS;
}
