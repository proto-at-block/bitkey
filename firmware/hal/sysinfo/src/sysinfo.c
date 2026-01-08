#include "sysinfo.h"

#include "attributes.h"
#include "bitops.h"
#include "filesystem.h"
#include "log.h"
#include "mcu_devinfo.h"

#define MLB_SERIAL_FILE_NAME  ("mlb-serial.txt")
#define ASSY_SERIAL_FILE_NAME ("assy-serial.txt")

extern char _sysinfo_software_type[SYSINFO_SOFTWARE_TYPE_MAX_LENGTH];
extern char _sysinfo_hardware_revision[SYSINFO_HARDWARE_REVISION_MAX_LENGTH];
extern char _sysinfo_version_string[SYSINFO_VERSION_MAX_LENGTH];

static sysinfo_t sysinfo_priv = {
  .serial = "XXXXXXXXXXXXXXXX\0",  // Must be null-terminated
  .software_type = _sysinfo_software_type,
  .hardware_revision = _sysinfo_hardware_revision,
  .version_string = _sysinfo_version_string,
};

static bool sysinfo_serial_write(char* filename, char* serial, uint32_t length) {
  ASSERT(serial);
  ASSERT(length == SYSINFO_SERIAL_NUMBER_LENGTH);

  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    return false;
  }

  if (fs_file_write(file, serial, length) != (int32_t)length) {
    goto out;
  }

  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

static bool sysinfo_serial_read(char* filename, char* serial_out, uint32_t* length_out) {
  ASSERT(serial_out && length_out);

  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, filename, FS_O_RDONLY);
  if (ret != 0) {
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0) {
    goto out;
  }

  ret = fs_file_read(file, serial_out, size);
  if (ret != size) {
    goto out;
  }

  *length_out = size;
  ASSERT(*length_out == SYSINFO_SERIAL_NUMBER_LENGTH);
  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

bool sysinfo_mlb_serial_write(char* serial, uint32_t length) {
  return sysinfo_serial_write(MLB_SERIAL_FILE_NAME, serial, length);
}

bool sysinfo_mlb_serial_read(char* serial_out, uint32_t* length_out) {
  return sysinfo_serial_read(MLB_SERIAL_FILE_NAME, serial_out, length_out);
}

bool sysinfo_assy_serial_write(char* serial, uint32_t length) {
  return sysinfo_serial_write(ASSY_SERIAL_FILE_NAME, serial, length);
}

bool sysinfo_assy_serial_read(char* serial_out, uint32_t* length_out) {
  return sysinfo_serial_read(ASSY_SERIAL_FILE_NAME, serial_out, length_out);
}

void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out) {
  *length_out = CHIPID_LENGTH;
  mcu_devinfo_chipid(chip_id_out);
}

bool sysinfo_load(void) {
  uint32_t length = 0;
  bool use_placeholder_serial = false;
  if (!sysinfo_assy_serial_read(sysinfo_priv.serial, &length)) {
    use_placeholder_serial = true;
  }
  if (length != SYSINFO_SERIAL_NUMBER_LENGTH) {
    use_placeholder_serial = true;
  }
  return (!use_placeholder_serial);
}

sysinfo_t* sysinfo_get(void) {
  return &sysinfo_priv;
}
