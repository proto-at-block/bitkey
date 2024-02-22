import re
import unittest

from typing import Optional
from elftools.elf.sections import Section, Symbol
from elftools.elf.elffile import ELFFile
from elftools.dwarf.dwarfinfo import DWARFInfo

from Crypto.Signature import DSS
from Crypto.Hash import SHA256

from . import decorators, fwut, reporter
from .keys import get_key, get_all_keys


class TestCase(unittest.TestCase):
    """Test class that adds additional functionality for aiding in filtering, discovery, and reporting.

    See documentation for unittest.TestCase for basic usage.
    """

    def _dry_run(self, result: reporter.TestResult):
        result.startTest(self)
        try:
            decorators.skip_unless_firmware_applies(
                getattr(self, self._testMethodName))
            result.addSuccess(self)
        except unittest.SkipTest as e:
            result.addSkip(self, str(e))

    def run(self, result: Optional[reporter.TestResult] = None):
        """Wraps the default run function to support dry-runs.

        Note: This run() method is the entry point for each individual test.
        """
        if fwut.FirmwareUnderTest.dry_run:
            self._dry_run(result)
            return

        super(TestCase, self).run(result)

    def assertInFirmware(self, hex_pattern: bytes, message: Optional[str] = None):
        """Fail if the given regular expression cannot be found in the firmware's hexadecimal representation.

        Note that the firmware hexadecimal is represented as lower-case, but the search is case-insensitive.

        Args:
            hex_pattern: Hexadecimal regular expression pattern to search for
            message: Optional message to display upon failure.
        """
        if re.search(hex_pattern, fwut.FirmwareUnderTest.firmware_hex, re.IGNORECASE) is None:
            if message is None:
                message = f"Pattern '{hex_pattern}' was not found in firmware"
            self.fail(message)

    def get_elf_file(self) -> Optional[ELFFile]:
        """Get the elf file from the FirmwareUnderTest"""
        return fwut.FirmwareUnderTest.get_elf()

    def get_dwarf_section(self) -> DWARFInfo:
        """Get the dwarf section

        Asserts that the elf file has dwarf info
        """
        elf = self.get_elf_file()
        self.assertTrue(elf.has_dwarf_info(),
                        "Firmware does not have dwarf info for analysis")

        return elf.get_dwarf_info()

    def get_elf_symbol(self, symbol_name: str) -> Symbol:
        """Get the symbol with the given symbol_name
        Buffer the symbols to avoid searching each time

        Asserts that there is exactly one symbol with the given name
        """
        elf_symbols = fwut.FirmwareUnderTest.get_elf_symbols()
        self.assertIsNotNone(elf_symbols, "ELF symbol section not found")

        matching = elf_symbols[symbol_name]
        self.assertEqual(1, len(matching),
                         f"Number of symbols found for {symbol_name}")
        return matching[0]

    def get_elf_symbol_value_from_symbol(self, symbol: Symbol) -> int:
        """Get the value of the given symbol
        """
        return symbol.entry.st_value

    def get_elf_symbol_value_from_name(self, symbol_name: str) -> int:
        """Get the value of the given symbol_name

        Asserts that there is exactly one symbol with the given name
        """
        matching = self.get_elf_symbol(symbol_name)
        return self.get_elf_symbol_value_from_symbol(matching)

    def get_elf_num_symbols_with_match(self, symbol_name: str) -> int:
        """Get the number of symbols with the given symbol_name

        Buffer the symbols to avoid searching each time
        """
        elf_symbols = fwut.FirmwareUnderTest.get_elf_symbols()
        self.assertIsNotNone(elf_symbols, "ELF symbol section not found")

        matching = elf_symbols[symbol_name]
        return len(matching)

    def get_elf_num_symbols_with_substring(self, substring: str) -> int:
        """Get the number of symbols that contain the given substring

        The caller should assert the expected number of symbols.
        """
        elf_symbols = fwut.FirmwareUnderTest.get_elf_symbols()
        self.assertIsNotNone(elf_symbols, "ELF symbol section not found")

        matching = []
        for k, v in elf_symbols.items():
            if substring in k:
                matching += v
        return len(matching)

    def get_elf_symbol_data(self, symbol_name: str, section_name: str) -> bytes:
        """Get the data related to the given symbol

        Asserts that the symbol exists and is in the given section (e.g. .data, .rodata)
        """
        sym = self.get_elf_symbol(symbol_name)
        sect = self.get_elf_file().get_section_by_name(section_name)
        self.assertIsNotNone(sect, f"Section {section_name} not found")

        data_start = sect.header.sh_addr
        data_end = data_start + sect.header.sh_size
        self.assertTrue(data_start <= sym.entry.st_value <
                        data_end, f"Symbol {symbol_name} not in {section_name}")

        offset = sym.entry.st_value - data_start
        size = sym.entry.st_size

        return sect.data()[offset: offset + size]

    def get_elf_section_by_name(self, section_name: str) -> Section:
        """Get a particular section from the elf file

        Assert that it exists
        """
        section = self.get_elf_file().get_section_by_name(section_name)

        self.assertIsNotNone(section, f"ELF {section_name} section not found")

        return section

    def get_elf_text_section(self) -> Section:
        """Get the text section from the elf file

        Asserts that it exists
        """
        return self.get_elf_section_by_name(".text")

    def get_elf_data_section(self) -> Section:
        """Get the data section from the elf file

        Asserts that it exists
        """
        return self.get_elf_section_by_name(".data")

    def get_elf_section_addr(self, section: Section) -> int:
        """Get the address of a particular section"""

        return section.header.sh_addr

    def get_elf_section_size(self, section: Section) -> int:
        """Get the size of a particular section"""

        return section.header.sh_size

    def get_elf_section_data(self, section: Section) -> bytes:
        """Get the contents of a particular section"""

        return section.data()

    def get_elf_data_from_addr_range(self, start_addr: int, end_addr: int, fill_bytes: bytes = b'\xff') -> bytes:
        """Get the data from start_addr to end_addr even if it spans multiple segments

        Gaps in-between segments will be filled with fill_bytes
        """

        elf = fwut.FirmwareUnderTest.get_elf()

        found_segments = []
        for segment in elf.iter_segments():
            segment_start_addr = segment.header.p_paddr
            segment_end_addr = segment_start_addr + segment.header.p_memsz

            if start_addr < segment_end_addr and end_addr > segment_start_addr:
                # This section has some data in the address range
                found_segments.append(segment)

        found_segments.sort(key=lambda s: s.header.p_paddr)

        prev_end = found_segments[0].header.p_paddr
        data = b''

        for segment in found_segments:
            if segment.header.p_paddr < prev_end:
                continue  # overlapping segment, skip it!
            if segment.header.p_paddr > prev_end:
                # There is a gap between segments
                data += fill_bytes * (segment.header.p_paddr - prev_end)
            data += segment.data()
            prev_end = segment.header.p_paddr + segment.header.p_memsz

        data += fill_bytes * (end_addr - prev_end)

        return data

    def get_macros(self, name: bytes, limit: Optional[int] = None) -> list:
        """Get the macro bodies that starts with 'name'

        Performs assertions while searching for the macro in the debug_str dwarf section.

        Note: This is NOT the same as the debug_macro section, which may not be generated or parsed.
        Instead, we search for strings starting with 'name' and return the stripped contents that follow.
        """
        dwarf = self.get_dwarf_section()

        self.assertIsNotNone(dwarf.debug_str_sec, "No debug string section")
        self.assertGreater(dwarf.debug_str_sec.size, 0,
                           "No debug strings in firmware")

        # Stream state is cached. Need to reset for every read.
        dwarf.debug_str_sec.stream.seek(0)
        debug_strs = dwarf.debug_str_sec.stream.read()
        self.assertGreater(len(debug_strs), 0, "No debug strings in firmware")

        # Find the debug string that starts with 'name'. Note: Strings are split by a NULL byte.
        matching_strs = list(
            filter(lambda s: s.startswith(name), debug_strs.split(b"\x00")))

        if limit is not None:
            self.assertLessEqual(len(matching_strs), limit,
                                 f"{name} defined too many times")

        # Return the body (after name)
        return [body[len(name):].strip() for body in matching_strs]

    def verify_signature(self, key_fmt: str, data: bytes, signature: bytes):
        '''Verify a signature using a key and data.

        OK: if the signature is valid
        WARNING: if production firmware is signed with dev key, but don't fail
        FAIL: if dev firmware is signed with prod key
        FAIL if signed firmware is not signed with either key
        '''

        key = get_key(key_fmt)
        h = SHA256.new(data)
        verifier = DSS.new(key, 'fips-186-3')

        try:
            verifier.verify(h, signature)
            return
        except ValueError:
            pass

        # if we have hit this point, then the main key verification failed
        # check all keys of the associated security level
        # if that fails, then check the opposite security keys
        security = fwut.FirmwareUnderTest.security
        keys = get_all_keys(key_fmt, security)
        for key in keys:
            verifier = DSS.new(key, 'fips-186-3')
            try:
                verifier.verify(h, signature)
                raise Warning(
                    "Warning: Firmware signed with incorrect key type")
            except ValueError:
                pass

        # check the latest opposite security key
        opposite_security = fwut.FirmwareUnderTest.get_opposite_security()
        opposite_keys = get_all_keys(key_fmt, opposite_security)

        for key in opposite_keys:
            verifier = DSS.new(key, 'fips-186-3')
            try:
                verifier.verify(h, signature)
                if fwut.FirmwareUnderTest.is_production():
                    raise Warning(
                        "Warning: Prod fw is dev signed")
                else:
                    self.fail("Dev firmware is production signed")
            except ValueError:
                pass

        self.fail("Key signature could not be validated")