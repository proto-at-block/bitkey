#include "hash.h"
#include "key_management.h"
#include "secure_engine.h"

sl_se_key_descriptor_t se_key_descriptor_for_key_handle(key_handle_t* handle);
sl_se_hash_type_t convert_hash_type(hash_alg_t alg);
