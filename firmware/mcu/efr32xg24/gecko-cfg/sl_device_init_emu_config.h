#pragma once

#include "em_emu.h"

// <q> Allow debugger to remain connected in EM2
// <i> Force PD0B to stay on on EM2 entry. This allows the debugger to remain connected in EM2 and
// EM3. <i> Enabling debug connectivity results in an increased power consumption in EM2/EM3. <i>
// Default: 1
#define SL_DEVICE_INIT_EMU_EM2_DEBUG_ENABLE 1

// <o SL_DEVICE_INIT_EMU_EM4_PIN_RETENTION_MODE> EM4 pin retention mode
// <emuPinRetentionDisable=> No Retention: Pads enter reset state when entering EM4.
// <emuPinRetentionEm4Exit=> Retention through EM4: Pads enter reset state when exiting EM4.
// <emuPinRetentionLatch=> Retention through EM4 and wakeup.
// <i> Default: emuPinRetentionDisable
#define SL_DEVICE_INIT_EMU_EM4_PIN_RETENTION_MODE emuPinRetentionDisable
