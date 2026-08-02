/**
 * @file wca_glue.h
 * @brief POSIX glue layer for lib/wca in core-sim
 */

#ifndef POSIX_WCA_GLUE_H
#define POSIX_WCA_GLUE_H

/**
 * Initialize lib/wca for POSIX core-sim.
 * Sets up the mempool and registers the proto handler for routing
 * commands to the appropriate handlers.
 *
 * After calling this, use lib/wca's wca_handle_command() to process APDUs.
 */
void posix_wca_init(void);

#endif /* POSIX_WCA_GLUE_H */
