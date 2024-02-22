import io

from binascii import hexlify
from collections import defaultdict
from elftools.elf.elffile import ELFFile
from elftools.elf.sections import SymbolTableSection, Symbol
from pathlib import Path
from typing import Optional, DefaultDict

from . import constants


# 4 levels up from firmware/python/bitkey/fwa
root_fw_dir = Path(__file__).resolve().parents[4]

keys_parent_path = root_fw_dir / "config/keys/"
partitions_parent_path = root_fw_dir / "config/partitions/"


class FirmwareUnderTest(object):

    @classmethod
    def reset(cls):
        # artifacts
        cls.product = None
        cls.platform = None
        cls.asset = None
        cls.slot = None
        cls.security = None
        cls.environment = None

        # extensions
        cls.signed = False
        cls.suffix = None

        # other
        cls._elf = None
        cls._elf_symbols = None
        cls.firmware = None
        cls.firmware_hex = None
        cls.filename = None

    @classmethod
    def _extract_tags_from_path(cls, fwup_path: Path):
        """ Parse the firmware filename and populate the class attributes
            fw name has the following format:
                {product}-{platform}-{app|loader}-{slot}?-{mfgtest}?-{prod|dev}.{signed}?.{bin|elf}
        """
        cls.filename = fwup_path.name

        artifacts, extensions = cls.filename.split('.', 1)
        artifacts = artifacts.split('-')
        extensions = extensions.split('.')

        artifacts_iter = iter(artifacts)
        extensions_iter = iter(extensions)

        # parse the artifacts

        cls.product = next(artifacts_iter)
        if cls.product not in constants.PRODUCTS:
            raise ValueError(
                f"Unknown product specified: {cls.product}")

        cls.platform = next(artifacts_iter)
        if cls.platform not in constants.PLATFORMS:
            raise ValueError(
                f"Unknown platform specified: {cls.platform}")

        cls.asset = next(artifacts_iter)
        if cls.asset not in constants.ASSETS:
            raise ValueError(f"Unknown asset specified: {cls.asset}")
        if cls.asset == constants.ASSET_APP:
            cls.slot = next(artifacts_iter)
            if cls.slot not in constants.SLOTS:
                raise ValueError(f"Unknown slot specified: {cls.slot}")

        environment = next(artifacts_iter)
        if environment not in constants.ENVIRONMENTS:
            if environment in constants.SECURITIES:
                # no mfgtest tag, so we should set the environment to non-mfgtest
                cls.environment = constants.ENV_NON_MFGTEST
                cls.security = environment  # the tag was actually the security tag
            else:
                raise ValueError(
                    f"Unknown environment specified: {environment}")
        else:
            cls.environment = constants.ENV_MFGTEST

        if not cls.security:
            cls.security = next(artifacts_iter)

        if cls.security not in constants.SECURITIES:
            raise ValueError(
                f"Unknown security specified: {cls.security}")

        # now parse the extensions

        suffix = next(extensions_iter)
        if suffix == constants.SUFFIX_SIGNED:
            cls.signed = True
            suffix = next(extensions_iter)

        cls.suffix = suffix
        if cls.suffix not in constants.SUFFIXES:
            raise ValueError(f"Unknown suffix specified: {cls.suffix}")
        if cls.suffix == constants.SUFFIX_BIN:
            raise ValueError(
                f"Suffix type {cls.suffix} not supported at this time. Please use .elf")

    @classmethod
    def load(cls, fwut_path: Path):
        cls.reset()
        cls._extract_tags_from_path(fwut_path)

        with open(fwut_path, "rb") as fw:
            cls.firmware = bytes(fw.read())

        cls.firmware_hex = hexlify(cls.firmware)
        cls._elf = None

    @classmethod
    def get_elf(cls) -> Optional[ELFFile]:
        """Get the ELFFile class representing the firmware under test

        :type -> ELFFile
        """
        if cls.firmware is None:
            return None

        if cls._elf:
            return cls._elf

        stream = io.BytesIO(cls.firmware)
        cls._elf = ELFFile(stream)
        return cls._elf

    @classmethod
    def get_elf_symbols(cls) -> Optional[DefaultDict[str, list[Symbol]]]:
        """Return a cache of the symbol table section for fast lookup"""

        if cls._elf_symbols:
            return cls._elf_symbols

        elf = cls.get_elf()
        if elf is None:
            return None

        symbol_section = None
        for section in elf.iter_sections():
            if isinstance(section, SymbolTableSection):
                symbol_section = section
                break

        elf_symbols = defaultdict(list)
        for sym in symbol_section.iter_symbols():
            elf_symbols[sym.name].append(sym)

        cls._elf_symbols = elf_symbols
        return cls._elf_symbols

    @classmethod
    def is_production(cls) -> bool:
        """Helper function to check if the firmware under test is prod

        :type -> bool
        """
        return cls.security == constants.SECURITY_PROD

    @classmethod
    def get_opposite_security(cls) -> bool:
        """Helper function to get the opposite security mode

        :type -> bool
        """
        if cls.security == constants.SECURITY_PROD:
            return constants.SECURITY_DEV
        elif cls.security == constants.SECURITY_DEV:
            return constants.SECURITY_PROD
        else:
            raise ValueError(f"Invalid security mode: {cls.security}")
