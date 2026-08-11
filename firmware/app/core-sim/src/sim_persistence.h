/**
 * @file sim_persistence.h
 * @brief File-based persistent storage for core-sim
 *
 * Provides persistence for device state across process restarts.
 * Data is stored in a directory specified by CORE_SIM_DATA_DIR env var
 * or defaults to ~/.core-sim/
 *
 * Environment variables:
 * - CORE_SIM_DATA_DIR: Directory for persistent data (default: ~/.core-sim)
 * - CORE_SIM_RESET_STORAGE: If set to "1", clears all persistent data on startup
 */

#ifndef SIM_PERSISTENCE_H
#define SIM_PERSISTENCE_H

#include <stdbool.h>
#include <stddef.h>

/**
 * Initialize the persistence layer.
 * Creates the data directory if it doesn't exist.
 * If CORE_SIM_RESET_STORAGE=1, clears all existing data.
 *
 * @return true on success
 */
bool sim_persistence_init(void);

/**
 * Check if persistence is enabled.
 *
 * @return true if persistence layer is initialized
 */
bool sim_persistence_enabled(void);

/**
 * Get the persistence data directory path.
 *
 * @return Path to data directory, or NULL if not initialized
 */
const char* sim_persistence_get_dir(void);

/**
 * Save binary data to a named file.
 *
 * @param name File name (stored in data directory)
 * @param data Data to write
 * @param len Length of data
 * @return true on success
 */
bool sim_persistence_save(const char* name, const void* data, size_t len);

/**
 * Load binary data from a named file.
 *
 * @param name File name (in data directory)
 * @param data Buffer to read into
 * @param len Expected length
 * @return true if file exists and was read successfully
 */
bool sim_persistence_load(const char* name, void* data, size_t len);

/**
 * Delete a named file.
 *
 * @param name File name (in data directory)
 * @return true if deleted or didn't exist
 */
bool sim_persistence_delete(const char* name);

/**
 * Delete all persistent data (wipe device).
 * Called when device wipe is executed.
 *
 * @return true on success
 */
bool sim_persistence_wipe_all(void);

#endif /* SIM_PERSISTENCE_H */
