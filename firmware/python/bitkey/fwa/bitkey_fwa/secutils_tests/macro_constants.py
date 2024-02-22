# List of macros and number of times variable_name appears in macro
SECUTILS_SYMBOL = "secutils_fixed_true"

macro_weight = {
    "SECURE_IF_FAILIN": 3,
    "SECURE_IF_FAILOUT": 3,
    "SECURE_DO_FAILIN": 9,
    "SECURE_DO_FAILOUT": 9,
    "SECURE_DO": 3,
    "SECURE_DO_ONCE": 3,
    "SECURE_ASSERT": 3
}

# Some functions are in assembly and for some, usage of secutils macro is not straight forward in the source code.
# st25r3916PerformCollisionAvoidance comes from a third party library that we are not tracking in git.
exception_functions = ['mcu_reset_handler', 'mpu_regions_init', '__secure_glitch_random_delay', 'st25r3916PerformCollisionAvoidance']
