#pragma once

#include "lfs.h"

lfs_t* bd_mount(void);
int bd_erase_all(void);
bool bd_error_str(char* buffer, const size_t len, const int error);
