import bitkey_fwa

from bitkey_fwa import dwarf_tools


class SymbolChecks(bitkey_fwa.TestCase):
    """Satisfy various properties of the firmware symbol table"""

    @bitkey_fwa.suffix("elf")
    def fwtest_elf_check_global_symbols_required(self):
        """"Ensure that the elf has the required symbols in all images"""

        symbols = [
            # Must exist for codesigning
            'sl_app_properties',
            # Secutils symbols
            '__secure_glitch_random_delay',
            'secure_glitch_random_delay',
            'secure_glitch_detect',
            # Stack canary symbols
            '__stack_chk_guard',
            '__stack_chk_init',
            '__wrap___stack_chk_fail',
        ]

        for sym in symbols:
            self.assertIsNotNone(self.get_elf_symbol(sym),
                                 f"symbol not found: {sym}")

    @bitkey_fwa.asset("app")
    @bitkey_fwa.suffix("elf")
    def fwtest_elf_check_app_symbols_required(self):
        """"Ensure that the app elf has the required symbols"""

        symbols = [
            # Firmware will compile, but we won't get coredumps
            'memfault_fault_handler',
        ]

        for sym in symbols:
            # will fail if not found
            self.get_elf_symbol(sym)

    @bitkey_fwa.security("prod")
    @bitkey_fwa.suffix("elf")
    def fwtest_elf_check_symbols_denylist(self):
        """"Check that various symbols do NOT exist in production images"""

        match_partial = [
            # Shell commands should not exist in production images
            '__shell_cmd_',
        ]

        match_whole = [
            # logging symbols
            '_log',
            'vprintf_',
            'log_level_strings',
            # used for testing stack canary protection
            'trigger_memset_stack_overflow',
            # mpu testing
            'mpu_test_map',
            # dev functions for testing biometrics
            'bio_provision_cryptographic_keys',
            'bio_storage_key_plaintext_save',
            'bio_write_plaintext_key',
        ]

        # match partial test
        for needle in match_partial:
            self.assertEquals(self.get_elf_num_symbols_with_substring(needle), 0,
                              f"denylist substring exists in production symbol table: {needle}")

        # match whole test
        for sym in match_whole:
            self.assertEquals(self.get_elf_num_symbols_with_match(sym), 0,
                              f"denylist symbol exists in production symbol table: {sym}")

    @bitkey_fwa.security("prod")
    @bitkey_fwa.suffix("elf")
    def fwtest_elf_verify_symbol_pair_empty(self):
        """"Check that various symbol pairs map to the same address in production images

            This ensures that certain arrays are empty for production images
        """

        symbol_pairs = [
            # Shell commands should not exist in production images
            ('__shell_cmds_start', '__shell_cmds_end'),
        ]
        for sym1, sym2 in symbol_pairs:
            self.assertEquals(self.get_elf_symbol_value_from_name(sym1),
                              self.get_elf_symbol_value_from_name(sym2), f"there is data between: {sym1}, {sym2}")

    @bitkey_fwa.suffix("elf")
    @bitkey_fwa.asset("app")
    def fwtest_elf_verify_symbol_pair_data(self):
        """Verify that various app symbol pairs are populated with data and aligned correctly"""

        symbol_pairs = [
            # dont check alignment for these symbols
            ('__privileged_functions_start__', '__privileged_functions_end__', 1),
            ('__syscalls_flash_start__', '__syscalls_flash_end__', 1),
            ('__unprivileged_flash_start__', '__unprivileged_flash_end__', 1),
            # end of symbols to skip alignment check

            ('__fwup_task_data_start__', '__fwup_task_data_end__', 32),
            ('__shared_task_data_start__', '__shared_task_data_end__', 32),
            ('__nfc_task_data_start__', '__nfc_task_data_end__', 32),
            ('__led_task_data_start__', '__led_task_data_end__', 32),
            ('__nfc_task_bss_start__', '__nfc_task_bss_end__', 32),
            ('__fwup_task_bss_start__', '__fwup_task_bss_end__', 32),
            ('__shared_task_bss_start__', '__shared_task_bss_end__', 32),
            ('__shared_task_protected_start__',
             '__shared_task_protected_end__', 32),
            # these symbols will not be defined until the auth matching task is used in the mpu
            # ('__auth_matching_task_bss_start__', '__auth_matching_task_bss_end__', 32),
        ]

        for sym1, sym2, alignment in symbol_pairs:
            sym1_addr = self.get_elf_symbol_value_from_name(sym1)
            sym2_addr = self.get_elf_symbol_value_from_name(sym2)
            self.assertGreater(sym2_addr, sym1_addr,
                               f"No data between {sym1} and {sym2}")
            self.assertEquals(sym1_addr % alignment, 0,
                              f"symbol {sym1} is not aligned to {alignment}")
            self.assertEquals(sym2_addr % alignment, 0,
                              f"symbol {sym2} is not aligned to {alignment}")

    @bitkey_fwa.suffix("elf")
    @bitkey_fwa.asset("app")
    def fwtest_elf_verify_symbol_alignment(self):
        """Verify that various symbols match alignment rules"""
        symbol_alignment_pairs = [
            # vectors table should be aligned to 512 bytes
            ('__Vectors', 256),
            ('__VectorsReal', 512),
        ]

        for sym, alignment in symbol_alignment_pairs:
            self.assertEquals(self.get_elf_symbol_value_from_name(sym) % alignment, 0,
                              f"symbol {sym} is not aligned to {alignment}")

    @bitkey_fwa.asset("app")
    @bitkey_fwa.suffix("elf")
    def fwtest_elf_verify_ramfuncs_in_ram(self):
        """"Verify that ramfuncs are in the ram section

            This involves checking that the symbols are within the bounds defined by the linker and in the data section
        """

        # the final two symbols are static functions that are not in the symbol table
        ramfuncs = [
            'mcu_flash_write_word',
            'mcu_flash_erase_page',
        ]

        ramfuncs_start = self.get_elf_symbol_value_from_name('__ramfunc_start__')
        ramfuncs_end = self.get_elf_symbol_value_from_name('__ramfunc_end__')

        self.assertNotEquals(ramfuncs_start, ramfuncs_end,
                             "ramfuncs is empty")

        for func in ramfuncs:
            func_addr = self.get_elf_symbol_value_from_name(func)
            self.assertTrue(ramfuncs_start <= func_addr < ramfuncs_end,
                            f"ramfunc {func} is not within the ramfuncs section")

        # verify that ramfuncs are within the data section
        data_section = self.get_elf_data_section()
        data_start = self.get_elf_section_addr(data_section)
        data_size = self.get_elf_section_size(data_section)

        self.assertTrue(data_start <= ramfuncs_start < ramfuncs_end <= data_start + data_size,
                        "ramfuncs is not within the data section")

    @bitkey_fwa.suffix("elf")
    @bitkey_fwa.asset("app")
    def fwtest_app_macro_denylist(self):
        """Ensure that various macros do not exist in the app"""

        # will fail if defined
        self.get_macros(b"AUTOMATED_TESTING", limit=0)

        macro_body = self.get_macros(b"BIO_DEV_MODE", limit=1)[0]
        self.assertEqual(macro_body, b"false", "BIO_DEV_MODE must be false")

    @bitkey_fwa.asset("app")
    @bitkey_fwa.suffix("elf")
    def fwtest_elf_check_for_function_regressions(self):
        """"Check that various functions are called from a parent function

            This ensures that no regressions are introduced that remove expected function calls
        """

        class Parent_Function_Info:
            """Helper class for fwtest_elf_check_for_function_regressions"""

            def __init__(self, name, file_glob, expected_calls):
                self.name = name
                self.file_glob = file_glob
                self.expected_calls = expected_calls

        dwarf = self.get_dwarf_section()

        parent_func_list = [
            # is_authenticated can regress if AUTOMATED_TESTING is set
            Parent_Function_Info(b'is_authenticated', b'*/auth_task.c', [
                b'secure_glitch_random_delay',
                b'secure_glitch_detect', b'rtos_mutex_lock', b'rtos_mutex_unlock']),
        ]

        for parent_func in parent_func_list:
            _func = dwarf_tools.get_function_from_file(
                dwarf, parent_func.name, parent_func.file_glob)
            _func_call_sites = list(dwarf_tools.gen_function_call_names(
                dwarf, _func))

        self.assertTrue(all(c in _func_call_sites for c in parent_func.expected_calls),
                        f"expected function calls missing from: {parent_func.name}")
