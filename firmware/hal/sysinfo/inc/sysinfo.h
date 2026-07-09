#include <stdbool.h>
#include <stdint.h>

#define SYSINFO_SERIAL_NUMBER_LENGTH         (16)
#define SYSINFO_SOFTWARE_TYPE_MAX_LENGTH     (32)
#define SYSINFO_VERSION_MAX_LENGTH           (12)
#define SYSINFO_HARDWARE_REVISION_MAX_LENGTH (32)

typedef struct {
  char serial[SYSINFO_SERIAL_NUMBER_LENGTH + 1];  // +1 for null term
  char* software_type;
  char* version_string;
  char* hardware_revision;
} sysinfo_t;

bool sysinfo_load(void);
sysinfo_t* sysinfo_get(void);

// Overwrite the in-memory serial buffer. Used by UXC to record the device
// serial pushed by Core via UC at boot. `length` must equal
// SYSINFO_SERIAL_NUMBER_LENGTH; the call is a no-op otherwise.
void sysinfo_set_serial(const char* serial, uint32_t length);

bool sysinfo_mlb_serial_write(char* serial, uint32_t length);
bool sysinfo_mlb_serial_read(char* serial_out, uint32_t* length_out);

bool sysinfo_assy_serial_write(char* serial, uint32_t length);
bool sysinfo_assy_serial_read(char* serial_out, uint32_t* length_out);

void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out);

void sysinfo_chip_info_read(uint8_t* buffer, uint32_t size);
