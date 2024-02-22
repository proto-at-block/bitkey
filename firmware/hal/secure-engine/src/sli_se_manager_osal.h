#pragma once

#ifdef SECURE_ENGINE_BOOTLOADER
#include "port-bootloader/sli_se_manager_osal_cmsis_rtos2.h"
#else
#include "port-application/sli_se_manager_osal_cmsis_rtos2.h"
#endif
