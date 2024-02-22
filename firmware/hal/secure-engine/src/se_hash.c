#include "secure_engine.h"
#include "sli_se_manager_internal.h"

#include <string.h>

sl_status_t se_hash(sl_se_command_context_t* cmd_ctx, sl_se_hash_type_t hash_type,
                    const uint8_t* message, unsigned int message_size, uint8_t* digest,
                    size_t digest_size) {
  if (cmd_ctx == NULL || digest == NULL || (message == NULL && message_size != 0)) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  SE_Command_t* se_cmd = &cmd_ctx->command;
  uint32_t command_word = SE_COMMAND_HASH;

  switch (hash_type) {
    case SL_SE_HASH_SHA256:
      if (digest_size != 32) {
        return SL_STATUS_INVALID_PARAMETER;
      }
      command_word |= SE_COMMAND_OPTION_HASH_SHA256;
      break;
    case SL_SE_HASH_SHA512:
      if (digest_size != 64) {
        return SL_STATUS_INVALID_PARAMETER;
      }
      command_word |= SE_COMMAND_OPTION_HASH_SHA512;
      break;
    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(cmd_ctx, command_word);

  SE_addParameter(se_cmd, message_size);

  SE_DataTransfer_t data_in = SE_DATATRANSFER_DEFAULT(message, message_size);
  SE_DataTransfer_t data_out = SE_DATATRANSFER_DEFAULT(digest, digest_size);

  SE_addDataInput(se_cmd, &data_in);
  SE_addDataOutput(se_cmd, &data_out);

  return sli_se_execute_and_wait(cmd_ctx);
}

sl_status_t se_hmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                    sl_se_hash_type_t hash_type, const uint8_t* message, size_t message_len,
                    uint8_t* output, size_t output_len) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status = SL_STATUS_OK;
  uint32_t command_word;
  size_t hmac_len;

  if (cmd_ctx == NULL || key == NULL || message == NULL || output == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  switch (hash_type) {
    case SL_SE_HASH_SHA256:
      command_word = SLI_SE_COMMAND_HMAC | SLI_SE_COMMAND_OPTION_HASH_SHA256;
      hmac_len = 32;
      break;

    case SL_SE_HASH_SHA512:
      command_word = SLI_SE_COMMAND_HMAC | SLI_SE_COMMAND_OPTION_HASH_SHA512;
      hmac_len = 64;
      break;

    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  if (output_len < hmac_len) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(cmd_ctx, command_word);

  // Add key parameter to command.
  sli_add_key_parameters(cmd_ctx, key, status);

  // Message size parameter.
  SE_addParameter(se_cmd, message_len);

  // Key metadata.
  sli_add_key_metadata(cmd_ctx, key, status);

  sli_add_key_input(cmd_ctx, key, status);

  // Data input.
  SE_DataTransfer_t in_data = SE_DATATRANSFER_DEFAULT(message, message_len);
  SE_addDataInput(se_cmd, &in_data);

  // Data output.
  SE_DataTransfer_t out_hmac = SE_DATATRANSFER_DEFAULT(output, hmac_len);
  SE_addDataOutput(se_cmd, &out_hmac);

  return sli_se_execute_and_wait(cmd_ctx);
}

sl_status_t se_hash_sha256_multipart_starts(sl_se_sha256_multipart_context_t* sha256_ctx,
                                            sl_se_command_context_t* cmd_ctx) {
  static const uint8_t init_state_sha256[32] = {
    0x6A, 0x09, 0xE6, 0x67, 0xBB, 0x67, 0xAE, 0x85, 0x3C, 0x6E, 0xF3, 0x72, 0xA5, 0x4F, 0xF5, 0x3A,
    0x51, 0x0E, 0x52, 0x7F, 0x9B, 0x05, 0x68, 0x8C, 0x1F, 0x83, 0xD9, 0xAB, 0x5B, 0xE0, 0xCD, 0x19};

  if (cmd_ctx == NULL || sha256_ctx == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  sha256_ctx->total[0] = 0;
  sha256_ctx->total[1] = 0;
  memcpy(sha256_ctx->state, init_state_sha256, sizeof(sha256_ctx->state));

  sha256_ctx->hash_type = SL_SE_HASH_SHA256;

  return SL_STATUS_OK;
}

// Copied from Gecko SDK, but support for everything but SHA256 removed.
static sl_status_t se_cmd_hash_multipart_update(void* hash_type_ctx,
                                                sl_se_command_context_t* cmd_ctx,
                                                const uint8_t* input, uint32_t num_blocks) {
  SE_Command_t* se_cmd = &cmd_ctx->command;
  uint32_t command_word;
  unsigned int ilen, state_len;
  uint8_t* state;

  switch (((sl_se_sha1_multipart_context_t*)hash_type_ctx)->hash_type) {
    case SL_SE_HASH_SHA256:
      command_word = SE_COMMAND_HASHUPDATE | SE_COMMAND_OPTION_HASH_SHA256;
      // SHA256 block size is 64 bytes
      ilen = 64 * num_blocks;
      // SHA256 state size is 32 bytes
      state_len = 32;
      state = ((sl_se_sha256_multipart_context_t*)hash_type_ctx)->state;
      break;

    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  sli_se_command_init(cmd_ctx, command_word);

  SE_addParameter(se_cmd, ilen);

  SE_DataTransfer_t data_in = SE_DATATRANSFER_DEFAULT(input, ilen);
  SE_DataTransfer_t iv_in = SE_DATATRANSFER_DEFAULT(state, state_len);
  SE_DataTransfer_t iv_out = SE_DATATRANSFER_DEFAULT(state, state_len);

  SE_addDataInput(se_cmd, &iv_in);
  SE_addDataInput(se_cmd, &data_in);
  SE_addDataOutput(se_cmd, &iv_out);

  // Execute and wait
  return sli_se_execute_and_wait(cmd_ctx);
}

// Copied from Gecko SDK, but support for everything but SHA256 removed.
sl_status_t se_hash_multipart_update(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     const uint8_t* input, size_t input_len) {
  size_t blocksize, countersize, blocks, fill, left;
  uint32_t* counter;
  uint8_t* buffer;
  sl_status_t status;

  if (input_len == 0) {
    return SL_STATUS_OK;
  }

  if (hash_type_ctx == NULL || cmd_ctx == NULL || input == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  switch (((sl_se_sha1_multipart_context_t*)hash_type_ctx)->hash_type) {
    case SL_SE_HASH_SHA256:
      blocksize = 64;
      countersize = 64 / 32;
      counter = ((sl_se_sha256_multipart_context_t*)hash_type_ctx)->total;
      buffer = ((sl_se_sha256_multipart_context_t*)hash_type_ctx)->buffer;
      break;

    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  left = (counter[0] & (blocksize - 1));
  fill = blocksize - left;

  counter[0] += input_len;

  // ripple counter
  if (counter[0] < input_len) {
    counter[1] += 1;
    for (size_t i = 1; i < (countersize - 1); i++) {
      if (counter[i] == 0) {
        counter[i + 1]++;
      }
    }
  }

  if ((left > 0) && (input_len >= fill)) {
    memcpy((void*)(buffer + left), input, fill);
    status = se_cmd_hash_multipart_update(hash_type_ctx, cmd_ctx, buffer, 1);
    if (status != SL_STATUS_OK) {
      return status;
    }
    input += fill;
    input_len -= fill;
    left = 0;
  }

  if (input_len >= blocksize) {
    blocks = input_len / blocksize;
    status = se_cmd_hash_multipart_update(hash_type_ctx, cmd_ctx, input, blocks);
    if (status != SL_STATUS_OK) {
      return status;
    }
    input += blocksize * blocks;
    input_len -= blocksize * blocks;
  }

  if (input_len > 0) {
    memcpy((void*)(buffer + left), input, input_len);
  }

  return SL_STATUS_OK;
}

// Copied from Gecko SDK, but support for everything but SHA256 removed.
sl_status_t se_hash_multipart_finish(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     uint8_t* digest_out, size_t digest_len) {
  size_t last_data_byte, num_pad_bytes, blocksize, countersize, outputsize;
  uint8_t msglen[16];
  uint32_t* counter;
  uint8_t* state;

  static const unsigned char sha_padding[64] = {0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                                0,    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                                0,    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                                0,    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

  if (hash_type_ctx == NULL || cmd_ctx == NULL || digest_out == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  switch (((sl_se_sha1_multipart_context_t*)hash_type_ctx)->hash_type) {
    case SL_SE_HASH_SHA256:
      blocksize = 64;
      outputsize = 32;
      countersize = 64 / 32;
      counter = ((sl_se_sha256_multipart_context_t*)hash_type_ctx)->total;
      state = ((sl_se_sha256_multipart_context_t*)hash_type_ctx)->state;
      break;

    default:
      return SL_STATUS_INVALID_PARAMETER;
  }

  if (digest_len < outputsize) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  /* Convert counter value to bits, and put in big-endian array */
  uint8_t residual = 0;
  for (size_t i = 0; i < countersize; i++) {
    size_t msglen_index = ((countersize - i) * sizeof(uint32_t)) - 1;

    msglen[msglen_index - 0] = ((counter[i] << 3) + residual) & 0xFF;
    msglen[msglen_index - 1] = (counter[i] >> 5) & 0xFF;
    msglen[msglen_index - 2] = (counter[i] >> 13) & 0xFF;
    msglen[msglen_index - 3] = (counter[i] >> 21) & 0xFF;

    residual = (counter[i] >> 29) & 0xFF;
  }

  last_data_byte = (counter[0] & (blocksize - 1));
  num_pad_bytes = (last_data_byte < (blocksize - (countersize * 4)))
                    ? ((blocksize - (countersize * 4)) - last_data_byte)
                    : (((2 * blocksize) - (countersize * 4)) - last_data_byte);

  sl_status_t status = se_hash_multipart_update(hash_type_ctx, cmd_ctx, sha_padding, num_pad_bytes);

  if (status == SL_STATUS_OK) {
    status = se_hash_multipart_update(hash_type_ctx, cmd_ctx, msglen, countersize * 4);
  }

  if (status == SL_STATUS_OK) {
    memcpy(digest_out, state, outputsize);
  }

  return status;
}
