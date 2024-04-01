#include "aes.h"
#include "bio_impl.h"
#include "bitops.h"
#include "filesystem.h"
#include "fpc_malloc.h"
#include "key_management.h"
#include "log.h"

#define TEMPLATE_PATH_LEN    (sizeof("fpc-template-.bin") + 2)
#define TEMPLATE_PATH_FORMAT ("fpc-template-%02d.bin")

#define CALIBRATION_PATH    ("fpc-calibration.bin")
#define AUTH_TIMESTAMP_PATH ("auth-timestamp.bin")

// Biometrics anti-replay key (BARK)
#define BARK_PATH           ("fpc-wrapped-bark.bin")
#define BARK_PLAINTEXT_PATH ("fpc-plaintext-bark.bin")

static bool bio_storage_template_id_valid(bio_template_id_t id) {
  bool ok = id < TEMPLATE_MAX_COUNT;
  if (!ok) {
    LOGE("Invalid template id %d", id);
  }
  return ok;
}

static bool save_template(fs_file_t* file, fpc_bep_template_t* template) {
  bool result = false;
  uint8_t* serialized_template = NULL;

  size_t size = 0;
  fpc_bep_result_t res = fpc_bep_template_get_size(template, &size);
  if (res != FPC_BEP_RESULT_OK || (size == 0)) {
    goto out;
  }

  serialized_template = fpc_malloc(size);
  if (serialized_template == NULL) {
    LOGE("Out of memory");
    goto out;
  }

  res = fpc_bep_template_serialize(template, serialized_template, size);
  if (res != FPC_BEP_RESULT_OK) {
    goto out;
  }

  if (fs_file_write(file, serialized_template, size) != (int32_t)size) {
    goto out;
  }

  result = true;

out:
  if (serialized_template) {
    fpc_free(serialized_template);
  }
  return result;
}

static bool save_data(char* filename, uint8_t* data, uint32_t size) {
  ASSERT(data && size > 0);
  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    LOGE("Failed to open %s\n", filename);
    return false;
  }

  if (fs_file_write(file, data, size) != (int32_t)size) {
    LOGE("Failed to write %ld bytes to %s\n", size, filename);
    goto out;
  }

  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

bool bio_storage_template_save(bio_template_id_t id, fpc_bep_template_t* template) {
  ASSERT(template != NULL);

  bool result = false;

  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    LOGE("Failed to open file for template %d", id);
    return false;
  }

  result = save_template(file, template);

  (void)fs_close_global(file);
  return result;
}

bool bio_storage_template_retrieve(bio_template_id_t id, fpc_bep_template_t** template_out) {
  if (!bio_storage_template_id_valid(id)) {
    return false;
  }

  uint8_t* serialized_template = NULL;
  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  fs_file_t* file;
  int ret = fs_open_global(&file, filename, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Failed to open file: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0) {
    LOGE("Bad file size: %" PRId32, size);
    goto fail;
  }

  serialized_template = fpc_malloc(size);
  if (serialized_template == NULL) {
    LOGE("Failed to allocate memory of size %" PRId32, size);
    goto fail;
  }

  ret = fs_file_read(file, serialized_template, size);
  if (ret != size) {
    LOGE("Partial read: %d", ret);
    goto fail;
  }

  fpc_bep_result_t bep_result = fpc_bep_template_deserialize(template_out, serialized_template,
                                                             size, FPC_BEP_ENABLE_MEM_RELEASE);
  if (bep_result != FPC_BEP_RESULT_OK) {
    LOGE("Failed to deserialize template: %d", bep_result);
    goto fail;
  }

  (void)fs_close_global(file);
  return true;

fail:
  (void)fs_close_global(file);
  if (serialized_template)
    fpc_free(serialized_template);
  fpc_bep_template_delete(template_out);
  return false;
}

bool bio_storage_calibration_data_exists(void) {
  return fs_file_exists(CALIBRATION_PATH);
}

bool bio_storage_calibration_data_save(uint8_t* calibration_data, uint32_t size) {
  return save_data(CALIBRATION_PATH, calibration_data, size);
}

bool bio_storage_calibration_data_retrieve(uint8_t** calibration_data, uint16_t* size_out) {
  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, CALIBRATION_PATH, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Failed to open file: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0 || size > UINT16_MAX) {
    LOGE("Bad file size: %" PRId32, size);
    goto fail;
  }

  *calibration_data = fpc_malloc(size);
  if (*calibration_data == NULL) {
    LOGE("Failed to allocate memory of size %" PRId32, size);
    goto fail;
  }

  ret = fs_file_read(file, *calibration_data, size);
  if (ret != size) {
    LOGE("Partial read: %d", ret);
    goto fail;
  }

  (void)fs_close_global(file);
  *size_out = size;
  return true;

fail:
  (void)fs_close_global(file);
  if (*calibration_data)
    fpc_free(*calibration_data);
  return false;
}

bool bio_storage_key_save(uint8_t* wrapped_key, uint32_t size) {
  return save_data(BARK_PATH, wrapped_key, size);
}

bool bio_storage_key_plaintext_save(uint8_t* plaintext_key, uint32_t size) {
  return save_data(BARK_PLAINTEXT_PATH, plaintext_key, size);
}

bool bio_storage_key_exists(void) {
  return fs_file_exists(BARK_PATH);
}

bool bio_storage_key_retrieve_unwrapped(key_handle_t* raw_key_handle) {
  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, BARK_PATH, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Failed to open file: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0) {
    LOGE("Bad file size: %" PRId32, size);
    goto fail;
  }

  uint8_t wrapped_key_bytes[AES_128_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD] = {0};
  key_handle_t wrapped_key = {
    .alg = ALG_AES_128,
    .storage_type = KEY_STORAGE_EXTERNAL_WRAPPED,
    .key.bytes = wrapped_key_bytes,
    .key.size = sizeof(wrapped_key_bytes),
  };

  ret = fs_file_read(file, wrapped_key_bytes, size);
  if (ret != size) {
    LOGE("Partial read: %d", ret);
    goto fail;
  }

  if (!export_key(&wrapped_key, raw_key_handle)) {
    LOGE("Failed to export key");
    return false;
  }

  (void)fs_close_global(file);
  return true;

fail:
  (void)fs_close_global(file);
  return false;
}

void bio_storage_get_template_count(uint32_t* count) {
  ASSERT(count);

  *count = 0;

  char filename[TEMPLATE_PATH_LEN] = {0};
  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);
    if (fs_file_exists(filename)) {
      (*count)++;
    }
  }
}

bool bio_fingerprint_exists(void) {
  uint32_t count = 0;
  bio_storage_get_template_count(&count);
  return (count > 0);
}

bool bio_storage_timestamp_retrieve(uint32_t* timestamp_out) {
  ASSERT(timestamp_out);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, AUTH_TIMESTAMP_PATH, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Failed to open file: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0 || (size != sizeof(uint32_t))) {
    LOGE("Bad file size: %" PRId32, size);
    goto fail;
  }

  ret = fs_file_read(file, timestamp_out, size);
  if (ret != size) {
    LOGE("Partial read: %d", ret);
    *timestamp_out = 0;
    goto fail;
  }

  *timestamp_out = htonl(*timestamp_out);

  (void)fs_close_global(file);
  return true;

fail:
  (void)fs_close_global(file);
  return false;
}

bool bio_storage_timestamp_save(uint32_t timestamp) {
  uint32_t t = htonl(timestamp);
  return save_data(AUTH_TIMESTAMP_PATH, (uint8_t*)&t, sizeof(timestamp));
}

bio_err_t bio_storage_delete_template(bio_template_id_t id) {
  if (!bio_storage_template_id_valid(id)) {
    LOGE("Invalid template id %d", id);
    return BIO_ERR_TEMPLATE_INVALID;
  }

  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  if (!fs_file_exists(filename)) {
    LOGE("Template %d does not exist", id);
    return BIO_ERR_TEMPLATE_DOESNT_EXIST;
  }

  if (fs_remove(filename) >= 0) {
    LOGD("Deleted template %d", id);
    return BIO_ERR_NONE;
  } else {
    LOGE("Failed to delete template %d", id);
    return BIO_ERR_GENERIC;
  }
}

void bio_wipe_state(void) {
  fs_remove("fpc-template-00.bin");
  fs_remove("fpc-template-01.bin");
  fs_remove("fpc-template-02.bin");
  fs_remove("fpc-template-03.bin");
  fs_remove("fpc-template-04.bin");
  fs_remove("fpc-template-05.bin");
  fs_remove("fpc-template-06.bin");
}
