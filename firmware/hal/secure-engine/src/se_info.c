#include "printf.h"
#include "secure_engine.h"
#include "secure_engine_priv.h"

#include <stdalign.h>
#include <string.h>

// A95750D30EDDFB969DE69642753853CCD7D5758ECC6EA2BD6FB0DAF06E5139F168F777B047948591D77079611884F62553D76B769F5257F1D8B01E0F8FE49B13
static const uint8_t dev_secure_boot_pubkey_first_byte = 0xa9;
// 9817BF12E0C01FE5D18AC39AC40A0093FCA389F9DF37C232E21F09CBD35EC14139940E1CFCE2CF13D80E0DF8EA39E8BA5504D4D45DB5D8E3861D8DF79ABCF4D2
static const uint8_t prod_secure_boot_pubkey_first_byte = 0x98;

// pubkey is optional. If it's NULL, the pubkey will not be copied.
//
// secure_boot_config_t is optional. If not NULL and `kind` is SL_SE_KEY_TYPE_IMMUTABLE_BOOT, the
// function will populate it.
static sl_status_t do_se_read_pubkey(sl_se_device_key_type_t kind, uint8_t* pubkey, uint32_t size,
                                     secure_boot_config_t* config) {
  sl_status_t status = SL_STATUS_FAIL;

  if (pubkey && size != SE_PUBKEY_SIZE) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    goto out;
  }

  // Buffer supplied to sl_se_read_pubkey() must be word aligned.
  alignas(sizeof(uint32_t)) uint8_t buf[SE_PUBKEY_SIZE] = {0};

  status = sl_se_read_pubkey(&cmd_ctx, kind, buf, SE_PUBKEY_SIZE);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // We check only the first byte of the dev or prod public key, since they differ.
  // We could compare the full keys, but that requires 126 extra bytes.
  // Security-critical decisions are not made using the the secure_boot_config_t; it's
  // purely informational.
  if (config && kind == SL_SE_KEY_TYPE_IMMUTABLE_BOOT) {
    switch (buf[0]) {
      case dev_secure_boot_pubkey_first_byte:
        *config = SECURE_BOOT_CONFIG_DEV;
        break;
      case prod_secure_boot_pubkey_first_byte:
        *config = SECURE_BOOT_CONFIG_PROD;
        break;
      default:
        *config = SECURE_BOOT_CONFIG_INVALID;
        break;
    }
  }

  if (pubkey) {
    memcpy(pubkey, buf, SE_PUBKEY_SIZE);
  }

out:
  memset(buf, 0, SE_PUBKEY_SIZE);
  return status;
}

sl_status_t se_get_secinfo(se_info_t* info) {
  sl_status_t status = SL_STATUS_FAIL;

  memset(info, 0, sizeof(se_info_t));

  sl_se_command_context_t cmd_ctx = {0};
  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = sl_se_get_se_version(&cmd_ctx, &info->version);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  memset(&cmd_ctx, 0, sizeof(cmd_ctx));
  status = sl_se_get_otp_version(&cmd_ctx, &info->otp_version);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  memset(&cmd_ctx, 0, sizeof(cmd_ctx));
  status = sl_se_get_serialnumber(&cmd_ctx, info->serial);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  memset(&cmd_ctx, 0, sizeof(cmd_ctx));
  status = sl_se_read_otp(&cmd_ctx, &info->otp);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  memset(&cmd_ctx, 0, sizeof(cmd_ctx));
  status = se_get_status(&info->se_status);  // Note: NOT sl_se_get_status.
  if (status != SL_STATUS_OK) {
    goto out;
  }

out:
  return status;
}

sl_status_t se_read_cert(sl_se_cert_type_t kind, uint8_t cert[512], uint16_t* size) {
  sl_status_t status = SL_STATUS_FAIL;

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    goto out;
  }

  sl_se_cert_size_type_t sizes = {0};
  status = sl_se_read_cert_size(&cmd_ctx, &sizes);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  switch (kind) {
    case SL_SE_CERT_BATCH:
      *size = sizes.batch_id_size;
      break;
    case SL_SE_CERT_DEVICE_SE:
      *size = sizes.se_id_size;
      break;
    case SL_SE_CERT_DEVICE_HOST:
      *size = sizes.host_id_size;
      break;
  }

  memset(&cmd_ctx, 0, sizeof(cmd_ctx));

  status = sl_se_read_cert(&cmd_ctx, kind, cert, *size);
  if (status != SL_STATUS_OK) {
    goto out;
  }

out:
  return status;
}

sl_status_t se_read_pubkeys(se_pubkeys_t* pubkeys) {
  sl_status_t status = SL_STATUS_FAIL;

  status = se_read_pubkey(SL_SE_KEY_TYPE_IMMUTABLE_BOOT, pubkeys->boot, SE_PUBKEY_SIZE);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status =
    se_read_pubkey(SL_SE_KEY_TYPE_IMMUTABLE_ATTESTATION, pubkeys->attestation, SE_PUBKEY_SIZE);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = se_read_pubkey(SL_SE_KEY_TYPE_IMMUTABLE_SE_ATTESTATION, pubkeys->se_attestation,
                          SE_PUBKEY_SIZE);
  if (status != SL_STATUS_OK) {
    goto out;
  }

out:
  return status;
}

sl_status_t se_get_status(sl_se_status_t* se_status) {
  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = sl_se_get_status(&cmd_ctx, se_status);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Tamper source ID=0 is not cleared by the SE on POR. This is a reserved bit that we can safely
  // ignore, so we mask it out in case this is the first time we've read the tamper source.
  // After one read, the tamper status will be cleared.
  se_status->tamper_status &= ~1;

out:
  return status;
}

sl_status_t se_read_pubkey(sl_se_device_key_type_t kind, uint8_t* pubkey, uint32_t size) {
  return do_se_read_pubkey(kind, pubkey, size, NULL);
}

sl_status_t se_get_secure_boot_config(secure_boot_config_t* config) {
  return do_se_read_pubkey(SL_SE_KEY_TYPE_IMMUTABLE_BOOT, NULL, 0, config);
}

sl_status_t se_read_serial(uint8_t serial[SE_SERIAL_SIZE]) {
  sl_status_t status = SL_STATUS_FAIL;

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    return status;
  }

  return sl_se_get_serialnumber(&cmd_ctx, serial);
}
