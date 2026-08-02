/**
 * @file stubs.h
 * @brief POSIX stub declarations
 */

#ifndef POSIX_STUBS_H
#define POSIX_STUBS_H

#include <stdbool.h>
#include <stdint.h>

// Secutils initialization (must be called before crypto operations)
void init_secutils_if_needed(void);

// Bitlog initialization (must be called before FWUP operations)
void init_bitlog_if_needed(void);

// Sysinfo serial read functions
bool sysinfo_mlb_serial_read(char* serial_out, uint32_t* length_out);
bool sysinfo_assy_serial_read(char* serial_out, uint32_t* length_out);

#endif /* POSIX_STUBS_H */
