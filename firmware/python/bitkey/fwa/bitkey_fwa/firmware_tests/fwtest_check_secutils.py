import bitkey_fwa

from bitkey_fwa import dwarf_tools


class SecutilsChecks(bitkey_fwa.TestCase):
    """Check that various secutils macros have not unexpectedly changed

    Does not verify correctness of secutils macros
    """

    @bitkey_fwa.suffix("elf")
    def fwtest_verify_secutils_macros(self):
        """"Ensure that various secutils macros have not unexpectedly changed.

        Note: This test only checks against unexpected source code changes and not compiler optimizations.
        """

        keyword_count_checks = [
            (b'MAGIC_COUNTER_STEP', 1, b"(42U)"),
            (b'_FIXED_READ()', 1, b"(volatile bool*)"),
            (b'SECURE_IF_FAILIN', 3, b"_FIXED_READ()"),
            (b'SECURE_IF_FAILIN', 4, b"condition"),
            (b'SECURE_IF_FAILOUT', 3, b"_FIXED_READ()"),
            (b'SECURE_IF_FAILOUT', 4, b"condition"),
            (b'SECURE_ASSERT', 1, b"volatile"),
            (b'SECURE_ASSERT', 3, b"secure_glitch_random_delay()"),
            (b'SECURE_ASSERT', 3, b"ASSERT(condition)"),
            (b'SECURE_ASSERT', 4, b"MAGIC_COUNTER_STEP"),
            (b'SECURE_ASSERT', 1, b"secure_glitch_detect()"),
            (b'SECURE_ASSERT', 1, b"SECURE_IF_FAILIN"),
            (b'SECURE_DO_ONCE(...)', 1, b"volatile"),
            (b'SECURE_DO_ONCE(...)', 1, b"secure_glitch_random_delay()"),
            (b'SECURE_DO_ONCE(...)', 2, b"MAGIC_COUNTER_STEP"),
            (b'SECURE_DO_ONCE(...)', 1, b"secure_glitch_detect()"),
            (b'SECURE_DO_ONCE(...)', 1, b"SECURE_IF_FAILIN"),
            (b'SECURE_DO(...)', 1, b"volatile"),
            (b'SECURE_DO(...)', 3, b"secure_glitch_random_delay()"),
            (b'SECURE_DO(...)', 4, b"MAGIC_COUNTER_STEP"),
            (b'SECURE_DO(...)', 1, b"secure_glitch_detect()"),
            (b'SECURE_DO(...)', 1, b"SECURE_IF_FAILIN"),
            (b'SECURE_DO_FAILOUT', 5, b"volatile"),
            (b'SECURE_DO_FAILOUT', 3, b"secure_glitch_random_delay()"),
            (b'SECURE_DO_FAILOUT', 4, b"MAGIC_COUNTER_STEP"),
            (b'SECURE_DO_FAILOUT', 2, b"secure_glitch_detect()"),
            (b'SECURE_DO_FAILOUT', 2, b"SECURE_IF_FAILIN"),
            (b'SECURE_DO_FAILOUT', 1, b"SECURE_IF_FAILOUT"),
            (b'SECURE_DO_FAILIN', 5, b"volatile"),
            (b'SECURE_DO_FAILIN', 3, b"secure_glitch_random_delay()"),
            (b'SECURE_DO_FAILIN', 4, b"MAGIC_COUNTER_STEP"),
            (b'SECURE_DO_FAILIN', 2, b"secure_glitch_detect()"),
            (b'SECURE_DO_FAILIN', 3, b"SECURE_IF_FAILIN"),
        ]

        # iterate over each tuple in keyword_count_checks
        # and verify that the keyword appears the expected number of times

        for (name, count, keyword) in keyword_count_checks:
            body = self.get_macros(name, limit=1)
            self.assertGreater(len(body), 0,
                               f"Macro not found: {name}")
            body = body[0]
            self.assertEqual(body.count(keyword), count,
                             f"Macro body changed: {name}, {keyword}:{count}")
