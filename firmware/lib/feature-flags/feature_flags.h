#pragma once

#include "wallet.pb.h"

#include <stdbool.h>

bool feature_flags_init(void);
bool feature_flags_get(fwpb_feature_flag flag);
bool* feature_flags_get_all(pb_size_t* len);
bool feature_flags_set(fwpb_feature_flag flag, bool value);
bool feature_flags_set_multiple(fwpb_feature_flag_cfg* flags, pb_size_t num_flags);
