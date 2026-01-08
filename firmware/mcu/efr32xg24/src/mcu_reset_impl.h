#pragma once

#include "attributes.h"
#include "mcu_reset.h"

NO_RETURN void __mcu_reset_with_reason(mcu_reset_reason_t reason);
