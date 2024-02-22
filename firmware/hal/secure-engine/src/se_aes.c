#include "printf.h"
#include "secure_engine.h"
#include "secutils.h"
#include "sli_se_manager_internal.h"

#include "em_se.h"

#include <stdlib.h>
#include <string.h>

// Based on SiLabs Secure Engine Manager API. See license in README.

sl_status_t se_aes_gcm(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length,
                       uint8_t iv[SE_AES_GCM_IV_LENGTH], const unsigned char* aad,
                       size_t aad_length, const unsigned char* input, unsigned char* output,
                       uint8_t tag[SE_AES_GCM_TAG_LENGTH]) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status = SL_STATUS_OK;

  if (cmd_ctx == NULL || key == NULL || iv == NULL || tag == NULL ||
      ((aad_length > 0) && (aad == NULL)) || ((length > 0) && (input == NULL || output == NULL))) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  switch (key->type) {
    case SL_SE_KEY_TYPE_AES_128:
    case SL_SE_KEY_TYPE_AES_192:
    case SL_SE_KEY_TYPE_AES_256:
      break;
    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  if (mode == SL_SE_DECRYPT) {
    sli_se_command_init(cmd_ctx,
                        SLI_SE_COMMAND_AES_GCM_DECRYPT | ((SE_AES_GCM_TAG_LENGTH & 0xFF) << 8));

    sli_add_key_parameters(cmd_ctx, key, status);
    SE_addParameter(se_cmd, aad_length);
    SE_addParameter(se_cmd, length);

    sli_add_key_metadata(cmd_ctx, key, status);
    sli_add_key_input(cmd_ctx, key, status);

    SE_DataTransfer_t iv_in = SE_DATATRANSFER_DEFAULT(iv, SE_AES_GCM_IV_LENGTH);
    SE_addDataInput(se_cmd, &iv_in);

    SE_DataTransfer_t aad_in = SE_DATATRANSFER_DEFAULT(aad, aad_length);
    SE_addDataInput(se_cmd, &aad_in);

    SE_DataTransfer_t data_in = SE_DATATRANSFER_DEFAULT(input, length);
    SE_addDataInput(se_cmd, &data_in);

    SE_DataTransfer_t tag_in = SE_DATATRANSFER_DEFAULT(tag, SE_AES_GCM_TAG_LENGTH);
    SE_addDataInput(se_cmd, &tag_in);

    SE_DataTransfer_t data_out = SE_DATATRANSFER_DEFAULT(output, length);
    if (output == NULL) {
      data_out.length |= SE_DATATRANSFER_DISCARD;
    }
    SE_addDataOutput(se_cmd, &data_out);
  } else if (mode == SL_SE_ENCRYPT) {
    sli_se_command_init(cmd_ctx, SLI_SE_COMMAND_AES_GCM_ENCRYPT);

    sli_add_key_parameters(cmd_ctx, key, status);
    SE_addParameter(se_cmd, aad_length);
    SE_addParameter(se_cmd, length);

    sli_add_key_metadata(cmd_ctx, key, status);
    sli_add_key_input(cmd_ctx, key, status);

    SE_DataTransfer_t iv_in = SE_DATATRANSFER_DEFAULT(iv, SE_AES_GCM_IV_LENGTH);
    SE_addDataInput(se_cmd, &iv_in);

    SE_DataTransfer_t aad_in = SE_DATATRANSFER_DEFAULT(aad, aad_length);
    SE_addDataInput(se_cmd, &aad_in);

    SE_DataTransfer_t data_in = SE_DATATRANSFER_DEFAULT(input, length);
    SE_addDataInput(se_cmd, &data_in);

    SE_DataTransfer_t data_out = SE_DATATRANSFER_DEFAULT(output, length);
    if (output == NULL) {
      data_out.length |= SE_DATATRANSFER_DISCARD;
    }
    SE_addDataOutput(se_cmd, &data_out);

    SE_DataTransfer_t mac_out = SE_DATATRANSFER_DEFAULT(tag, SE_AES_GCM_TAG_LENGTH);
    SE_addDataOutput(se_cmd, &mac_out);
  } else {
    return false;
  }

  status = sli_se_execute_and_wait(cmd_ctx);
  if (status != SL_STATUS_OK) {
    memzero(output, length);
    return status;
  }
  return status;
}

sl_status_t se_aes_cmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        const unsigned char* input, size_t input_len, unsigned char* output) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status = SL_STATUS_OK;

  if (cmd_ctx == NULL || key == NULL || input == NULL || output == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  switch (key->type) {
    case SL_SE_KEY_TYPE_AES_128:
    case SL_SE_KEY_TYPE_AES_192:
    case SL_SE_KEY_TYPE_AES_256:
      break;

    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(cmd_ctx, SLI_SE_COMMAND_AES_CMAC);

  // Add key parameter to command.
  sli_add_key_parameters(cmd_ctx, key, status);

  // Message size parameter.
  SE_addParameter(se_cmd, input_len);

  // Key metadata.
  sli_add_key_metadata(cmd_ctx, key, status);
  sli_add_key_input(cmd_ctx, key, status);

  // Data input.
  SE_DataTransfer_t in_data = SE_DATATRANSFER_DEFAULT(input, input_len);
  SE_addDataInput(se_cmd, &in_data);

  // Data output.
  SE_DataTransfer_t out_tag = SE_DATATRANSFER_DEFAULT(output, 16);
  SE_addDataOutput(se_cmd, &out_tag);

  return sli_se_execute_and_wait(cmd_ctx);
}

sl_status_t se_aes_cbc(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, unsigned char iv[16],
                       const unsigned char* input, unsigned char* output) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status;

  if (cmd_ctx == NULL || key == NULL || input == NULL || output == NULL || iv == NULL ||
      length == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Input length must be a multiple of 16 bytes which is the AES block length
  if (length & 0xf) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(
    cmd_ctx, (mode == SL_SE_ENCRYPT ? SLI_SE_COMMAND_AES_ENCRYPT : SLI_SE_COMMAND_AES_DECRYPT) |
               SLI_SE_COMMAND_OPTION_MODE_CBC | SLI_SE_COMMAND_OPTION_CONTEXT_ADD);

  // Add key parameters to command
  sli_add_key_parameters(cmd_ctx, key, status);
  // Message size (number of bytes)
  SE_addParameter(se_cmd, length);

  // Add key metadata block to command
  sli_add_key_metadata(cmd_ctx, key, status);
  // Add key input block to command
  sli_add_key_input(cmd_ctx, key, status);

  SE_DataTransfer_t iv_in = SE_DATATRANSFER_DEFAULT(iv, 16);
  SE_DataTransfer_t in = SE_DATATRANSFER_DEFAULT(input, length);
  SE_addDataInput(se_cmd, &iv_in);
  SE_addDataInput(se_cmd, &in);

  SE_DataTransfer_t out = SE_DATATRANSFER_DEFAULT(output, length);
  SE_addDataOutput(se_cmd, &out);

  return sli_se_execute_and_wait(cmd_ctx);
}

sl_status_t se_aes_ecb(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, const unsigned char* input,
                       unsigned char* output) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status;

  if (cmd_ctx == NULL || key == NULL || input == NULL || output == NULL || (length & 0xFU) != 0U) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(
    cmd_ctx, (mode == SL_SE_ENCRYPT ? SLI_SE_COMMAND_AES_ENCRYPT : SLI_SE_COMMAND_AES_DECRYPT) |
               SLI_SE_COMMAND_OPTION_MODE_ECB | SLI_SE_COMMAND_OPTION_CONTEXT_WHOLE);

  // Add key parameters to command
  sli_add_key_parameters(cmd_ctx, key, status);
  // Message size (number of bytes)
  SE_addParameter(se_cmd, length);

  // Add key metadata block to command
  sli_add_key_metadata(cmd_ctx, key, status);
  // Add key input block to command
  sli_add_key_input(cmd_ctx, key, status);

  SE_DataTransfer_t in = SE_DATATRANSFER_DEFAULT(input, length);
  SE_addDataInput(se_cmd, &in);

  SE_DataTransfer_t out = SE_DATATRANSFER_DEFAULT(output, length);
  SE_addDataOutput(se_cmd, &out);

  return sli_se_execute_and_wait(cmd_ctx);
}
