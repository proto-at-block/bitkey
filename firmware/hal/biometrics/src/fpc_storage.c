#include "aes.h"
#include "bio_flash_storage.h"
#include "bio_impl.h"
#include "bitlog.h"
#include "bitops.h"
#include "filesystem.h"
#include "fpc_malloc.h"
#include "key_management.h"
#include "log.h"

// Template id that is stored in the dedicated bio_flash partition
// instead of the filesystem, to save a flash block.
#define BIO_FLASH_TEMPLATE_ID (2)

#define TEMPLATE_PATH_LEN    (sizeof("fpc-template-.bin") + 2)
#define TEMPLATE_PATH_FORMAT ("fpc-template-%02d.bin")

#define CALIBRATION_PATH    ("fpc-calibration.bin")
#define AUTH_TIMESTAMP_PATH ("auth-timestamp.bin")

#define BIO_LABELS_PATH_LEN    (sizeof("bio-label-.bin") + 2)
#define BIO_LABELS_PATH_FORMAT ("bio-label-%02d.bin")

// Biometrics anti-replay key (BARK)
#define BARK_PATH           ("fpc-wrapped-bark.bin")
#define BARK_PLAINTEXT_PATH ("fpc-plaintext-bark.bin")

static bool bio_storage_template_id_valid(bio_template_id_t id) {
  bool ok = id < TEMPLATE_MAX_COUNT;
  if (!ok) {
    LOGE("Bad tmpl id %d", id);
  }
  return ok;
}

// Writes data to a file, truncating the file if it already exists.
static bool save_data(char* filename, uint8_t* data, uint32_t size) {
  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    LOGE("Open fail: %s", filename);
    return false;
  }

  if (fs_file_exists(filename)) {
    if (fs_file_truncate(file, 0) != 0) {
      LOGE("Truncate fail");
      goto out;
    }
  }

  if (fs_file_write(file, data, size) != (int32_t)size) {
    LOGE("Wr: %ld to %s", size, filename);
    goto out;
  }

  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

// Serialize a template into a malloc'd buffer. Caller must fpc_free() the result.
static uint8_t* serialize_template(fpc_bep_template_t* template, size_t* size_out) {
  size_t size = 0;
  fpc_bep_result_t res = fpc_bep_template_get_size(template, &size);
  if ((res != FPC_BEP_RESULT_OK) || (size == 0)) {
    return NULL;
  }

  uint8_t* buf = fpc_malloc(size);
  if (buf == NULL) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_OOM);
    return NULL;
  }

  res = fpc_bep_template_serialize(template, buf, size);
  if (res != FPC_BEP_RESULT_OK) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_SERIALIZE);
    fpc_free(buf);
    return NULL;
  }

  *size_out = size;
  return buf;
}

static bool save_template_to_fs(bio_template_id_t id, uint8_t* data, size_t size) {
  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_OPEN);
    return false;
  }

  if (fs_file_truncate(file, 0) != 0) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_WRITE);
    (void)fs_close_global(file);
    return false;
  }

  bool result = (fs_file_write(file, data, size) == (int32_t)size);
  (void)fs_close_global(file);

  if (!result) {
    BITLOG_EVENT(bio_template_save_error, id);
    fs_remove(filename);
  }

  return result;
}

bool bio_storage_template_save(bio_template_id_t id, fpc_bep_template_t* template) {
  ASSERT(template != NULL);

  size_t size = 0;
  uint8_t* serialized_template = serialize_template(template, &size);
  if (serialized_template == NULL) {
    return false;
  }

  bool result;
  if (id == BIO_FLASH_TEMPLATE_ID) {
    result = bio_flash_storage_save(serialized_template, (uint32_t)size);
    if (result) {
      // Remove any lingering filesystem file so that retrieve doesn't
      // prefer the stale FS copy over the freshly written flash data.
      char filename[TEMPLATE_PATH_LEN] = {0};
      snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);
      fs_remove(filename);
    }
  } else {
    result = save_template_to_fs(id, serialized_template, size);
  }

  fpc_free(serialized_template);
  return result;
}

static bool retrieve_template_from_flash(fpc_bep_template_t** template_out) {
  // Query stored size first, then allocate exactly that much.
  uint32_t size = 0;
  if (!bio_flash_storage_get_size(&size)) {
    return false;
  }

  uint8_t* serialized_template = fpc_malloc(size);
  if (serialized_template == NULL) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_OOM);
    return false;
  }

  uint32_t read_size = 0;
  if (!bio_flash_storage_read(serialized_template, &read_size)) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_READ);
    fpc_free(serialized_template);
    return false;
  }

  fpc_bep_result_t bep_result = fpc_bep_template_deserialize(template_out, serialized_template,
                                                             read_size, FPC_BEP_ENABLE_MEM_RELEASE);
  if (bep_result != FPC_BEP_RESULT_OK) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_DESERIALIZE);
    fpc_free(serialized_template);
    fpc_bep_template_delete(template_out);
    return false;
  }

  return true;
}

static bool retrieve_template_from_fs(bio_template_id_t id, fpc_bep_template_t** template_out) {
  uint8_t* serialized_template = NULL;
  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  fs_file_t* file;
  int ret = fs_open_global(&file, filename, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Open fail: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0) {
    LOGE("Sz: %" PRId32, size);
    goto fail;
  }

  serialized_template = fpc_malloc(size);
  if (serialized_template == NULL) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_OOM);
    goto fail;
  }

  ret = fs_file_read(file, serialized_template, size);
  if (ret != size) {
    LOGE("Rd: %d", ret);
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_READ);
    goto fail;
  }

  fpc_bep_result_t bep_result = fpc_bep_template_deserialize(template_out, serialized_template,
                                                             size, FPC_BEP_ENABLE_MEM_RELEASE);
  if (bep_result != FPC_BEP_RESULT_OK) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_DESERIALIZE);
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

bool bio_storage_template_retrieve(bio_template_id_t id, fpc_bep_template_t** template_out) {
  if (!bio_storage_template_id_valid(id)) {
    return false;
  }

  if (id == BIO_FLASH_TEMPLATE_ID) {
    // Check filesystem first for rollback/migration-failure hardening:
    // the filesystem copy is the proven, known-good data. Only fall
    // through to raw flash once the filesystem file has been cleaned up.
    char filename[TEMPLATE_PATH_LEN] = {0};
    snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);
    if (fs_file_exists(filename)) {
      return retrieve_template_from_fs(id, template_out);
    }
    return retrieve_template_from_flash(template_out);
  } else {
    return retrieve_template_from_fs(id, template_out);
  }
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
    LOGE("Open fail: %d", ret);
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_OPEN);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0 || size > UINT16_MAX) {
    LOGE("Sz: %" PRId32, size);
    goto fail;
  }

  *calibration_data = fpc_malloc(size);
  if (*calibration_data == NULL) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_OOM);
    goto fail;
  }

  ret = fs_file_read(file, *calibration_data, size);
  if (ret != size) {
    LOGE("Rd: %d", ret);
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_READ);
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
    LOGE("Open fail: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0) {
    LOGE("Sz: %" PRId32, size);
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
    LOGE("Rd: %d", ret);
    goto fail;
  }

  if (!export_key(&wrapped_key, raw_key_handle)) {
    LOGE("Key export fail");
    return false;
  }

  (void)fs_close_global(file);
  return true;

fail:
  (void)fs_close_global(file);
  return false;
}

static bool bio_template_file_exists(bio_template_id_t id) {
  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

  if (id == BIO_FLASH_TEMPLATE_ID) {
    // Check filesystem first, then flash. This ensures the template is
    // found even if migration hasn't run or failed partway through.
    return (fs_file_exists(filename)) || (bio_flash_storage_template_exists());
  }
  return fs_file_exists(filename);
}

void bio_storage_get_template_count(uint32_t* count) {
  ASSERT(count);

  *count = 0;
  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    if (bio_template_file_exists(id)) {
      (*count)++;
    }
  }
}

bool bio_fingerprint_exists(void) {
  uint32_t count = 0;
  bio_storage_get_template_count(&count);
  return (count > 0);
}

bool bio_fingerprint_index_exists(bio_template_id_t id) {
  if (!bio_storage_template_id_valid(id)) {
    return false;
  }
  return bio_template_file_exists(id);
}

bool bio_storage_timestamp_retrieve(uint32_t* timestamp_out) {
  ASSERT(timestamp_out);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, AUTH_TIMESTAMP_PATH, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Open fail: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0 || (size != sizeof(uint32_t))) {
    LOGE("Sz: %" PRId32, size);
    goto fail;
  }

  ret = fs_file_read(file, timestamp_out, size);
  if (ret != size) {
    LOGE("Rd: %d", ret);
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
    LOGE("Bad tmpl id %d", id);
    return BIO_ERR_TEMPLATE_INVALID;
  }

  // Delete template
  if (id == BIO_FLASH_TEMPLATE_ID) {
    char filename[TEMPLATE_PATH_LEN] = {0};
    snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);
    bool in_flash = bio_flash_storage_exists();
    bool in_fs = fs_file_exists(filename);

    if (!in_flash && !in_fs) {
      return BIO_ERR_TEMPLATE_DOESNT_EXIST;
    }
    if (in_flash && !bio_flash_storage_erase()) {
      return BIO_ERR_GENERIC;
    }
    if (in_fs) {
      fs_remove(filename);
    }
  } else {
    char filename[TEMPLATE_PATH_LEN] = {0};
    snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);

    if (!fs_file_exists(filename)) {
      return BIO_ERR_TEMPLATE_DOESNT_EXIST;
    }

    if (fs_remove(filename) < 0) {
      return BIO_ERR_GENERIC;
    }
  }

  // Delete label file (always on filesystem)
  char label_filename[BIO_LABELS_PATH_LEN] = {0};
  snprintf(label_filename, sizeof(label_filename), BIO_LABELS_PATH_FORMAT, id);

  if (!fs_file_exists(label_filename)) {
    return BIO_ERR_LABEL_DOESNT_EXIST;
  }

  if (fs_remove(label_filename) < 0) {
    return BIO_ERR_GENERIC;
  }

  return BIO_ERR_NONE;
}

bool bio_storage_label_save(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]) {
  if (!bio_storage_template_id_valid(id) || label == NULL) {
    return false;
  }

  char filename[BIO_LABELS_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), BIO_LABELS_PATH_FORMAT, id);

  return save_data(filename, (uint8_t*)label, strnlen(label, BIO_LABEL_MAX_LEN));
}

bool bio_storage_label_retrieve(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]) {
  if (!bio_storage_template_id_valid(id) || label == NULL) {
    return false;
  }

  char filename[BIO_LABELS_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), BIO_LABELS_PATH_FORMAT, id);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Open fail: %d", ret);
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0 || size > BIO_LABEL_MAX_LEN) {
    LOGE("Sz: %" PRId32, size);
    (void)fs_close_global(file);
    return false;
  }

  ret = (int)fs_file_read(file, (uint8_t*)label, size);
  if (ret != size) {
    LOGE("Rd: %d", ret);
    (void)fs_close_global(file);
    return false;
  }

  label[size] = '\0';

  (void)fs_close_global(file);
  return true;
}

void bio_wipe_state(void) {
  char filename[TEMPLATE_PATH_LEN];

  for (int id = 0; id < (TEMPLATE_MAX_COUNT + 3); id++) {
    snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, id);
    fs_remove(filename);
    if (id == BIO_FLASH_TEMPLATE_ID) {
      bio_flash_storage_erase();
    }

    snprintf(filename, sizeof(filename), BIO_LABELS_PATH_FORMAT, id);
    fs_remove(filename);
  }
}

void bio_storage_migrate_to_flash(void) {
  // Initialize the cached existence flag from flash. This runs on the
  // privileged auth task during bio_lib_init(), before any other task
  // calls onboarding_complete().
  bio_flash_storage_init();

  // Migrate fingerprint template 3 from filesystem to the raw flash partition.
  // This is a one-time migration for devices that enrolled a 3rd fingerprint
  // before this change.
  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, BIO_FLASH_TEMPLATE_ID);

  if (!fs_file_exists(filename)) {
    return;  // Nothing to migrate.
  }

  if (bio_flash_storage_template_exists()) {
    fs_remove(filename);
    return;
  }

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_RDONLY) != 0) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_OPEN);
    return;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_READ);
    fs_close_global(file);
    return;
  }

  uint8_t* buf = fpc_malloc((uint32_t)size);
  if (buf == NULL) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_OOM);
    fs_close_global(file);
    return;
  }

  int32_t bytes_read = fs_file_read(file, buf, (uint32_t)size);
  fs_close_global(file);

  if (bytes_read != size) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_READ);
    fpc_free(buf);
    return;
  }

  if (!bio_flash_storage_save(buf, (uint32_t)size)) {
    BITLOG_EVENT(fp_err, BIO_FP_ERR_FILE_WRITE);
    fpc_free(buf);
    return;
  }

  fpc_free(buf);
  fs_remove(filename);
}
