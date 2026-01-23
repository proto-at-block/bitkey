import logging
import os
import shutil
import subprocess
import tempfile
from abc import ABC, abstractmethod
from binascii import hexlify
from pathlib import Path

import yaml
from bitkey.key_manager import KeyManager
from bitkey.signer_utils import AssetInfo, ElfSymbol
from Crypto.Hash import SHA256
from elftools.elf.elffile import ELFFile

logging.basicConfig(level=logging.WARN, format="[%(levelname)s] %(message)s ")
logger = logging.getLogger("signer")


class ElfSigner(ABC):
    """Abstract base class for ELF firmware signing.

    Provides common ELF operations (symbol resolution, file I/O, etc.) and
    default implementations for signing workflows. Subclasses only need to
    implement version setting; certificate injection has a default implementation.

    Abstract Methods (must be implemented by subclasses):
        _set_version: Set firmware version and any related metadata (e.g., build ID)
        _set_build_id: Set the build ID for the given slot (memfault)

    Concrete Methods (default implementations provided):
        codesign: Sign the firmware ELF (standard workflow)
        gen_presign_hash: Generate hash for signing (standard workflow)
        verify_signature: Verify a signed ELF
        stitch_and_finalize: Inject signature and create output files

    Optional Override Methods:
        _validate_image_type: Validate image type support (default: allows all)
    """

    ECC_P256_SIG_SIZE = 64
    FLASH_ERASED_VALUE = 0xFF

    def _inject_cert(self, cert: bytes, image_type: str):
        """Inject certificate into the appropriate symbol."""
        sym_name = f"{image_type}_certificate"
        self._write_symbol_data(sym_name, cert)
        assert self._read_symbol_data(sym_name) == cert

    @abstractmethod
    def _set_version(self, image_type: str, app_version: str):
        """Set firmware version in properties structure. Must be implemented by subclasses."""
        raise NotImplementedError("Subclasses must implement _set_version")

    def _validate_image_type(self, image_type: str):
        """Validate that image_type is supported by this platform. Override if needed."""
        pass

    def codesign(self, key_manager: KeyManager, asset_info: AssetInfo):
        """Sign the firmware ELF file. Standard implementation used by all platforms."""
        # Compute the presigned hash
        digest = self.gen_presign_hash(key_manager, asset_info)

        # Pass the pre-signed artifact to key manager to sign
        signature = key_manager.generate_signature(digest)

        # Stitch it together and finalize
        self.stitch_and_finalize(key_manager, asset_info, signature, digest)

    def gen_presign_hash(self, key_manager: KeyManager, asset_info: AssetInfo) -> SHA256.SHA256Hash:
        """Generate hash for signing. Standard implementation used by all platforms."""
        image_type = asset_info.get_image_type()
        slot = asset_info.get_slot()
        app_version = asset_info.get_app_version()

        # Validate image type support
        self._validate_image_type(image_type)

        # Validate slot requirement for app images
        if image_type == "app" and not slot:
            raise ValueError("must set slot when signing app")

        # Inject certificate and set version before signing
        with open(key_manager.get_signing_cert_path(), "rb") as f:
            cert = f.read()
        self._inject_cert(cert, image_type)

        if app_version is not None:
            self._set_version(image_type, app_version)

        # Update the Memfault build ID.
        if image_type == "app":
            self._set_build_id(slot)

        # Check signature is in the right place for signing
        signature_data = self._read_symbol_data(
            self._image_to_sig_sym_name(image_type))
        assert signature_data == b"\xca\xfe" * (
            self.ECC_P256_SIG_SIZE // 2
        ), f"signature in wrong location, or image was already signed. was {hexlify(signature_data)}"

        # Generate hash
        digest = self.gen_hash()

        logger.debug(f"sha256: {digest.hexdigest()}")
        return digest

    def __init__(self, unsigned_elf_path: Path, partitions_config_path: str = None):
        self.elf_path = f"{unsigned_elf_path.parent}/{unsigned_elf_path.stem}.signed.elf"
        logging.log(logging.INFO, f"unsigned elf path: {unsigned_elf_path}")
        logging.log(logging.INFO, f"elf path: {self.elf_path}")
        shutil.copyfile(unsigned_elf_path, self.elf_path)
        self.elf_file = open(self.elf_path, "rb+")
        self.elf = ELFFile(self.elf_file)
        self.symtab = self.elf.get_section_by_name(".symtab")
        self.partitions_config_path = partitions_config_path
        assert self.symtab

    def get_elf_path(self) -> str:
        return self.elf_path

    def _resolve_symbol(self, sym):
        """Find the file offset in the ELF where the symbol's data resides."""

        # Based on: https://github.com/eliben/pyelftools/issues/227
        # Find the segment where the symbol is loaded to, as the symbol table points to
        # the loaded address, not the offset in the file
        file_offset = None
        for seg in self.elf.iter_segments():
            if seg.header["p_type"] != "PT_LOAD":
                continue
            # If the symbol is inside the range of a LOADed segment, calculate the file
            # offset by subtracting the virtual start address and adding the file offset
            # of the loaded section(s)
            if sym.addr() >= seg["p_vaddr"] and sym.addr() < seg["p_vaddr"] + seg["p_filesz"]:
                file_offset = sym.addr() - seg["p_vaddr"] + seg["p_offset"]
                break
        logger.debug(f"{sym.name()} @ {hex(sym.addr())}")
        assert file_offset, "Error getting file offset from ELF data"
        return file_offset

    def _image_to_sig_sym_name(self, image_type: str) -> str:
        """Get the signature symbol name for the given image type.

        Default implementation returns {image_type}_codesigning_signature.
        Slot differentiation happens at the section level, not the symbol name.
        Override in subclasses if different naming is needed.
        """
        return f"{image_type}_codesigning_signature"

    def _image_to_sig_section(self, image_type: str, slot: str = None) -> str:
        if image_type == "bl":
            return f".{image_type}_codesigning_signature_section"
        else:
            if slot is None:
                raise ValueError("must set slot when signing app")
            return f".{image_type}_{slot}_codesigning_signature_section"

    def _read_symbol_data(self, sym_name: str) -> bytes:
        sym = ElfSymbol(self.symtab, sym_name)
        off = self._resolve_symbol(sym)
        self.elf.stream.seek(off)
        size = sym.size()
        data = self.elf.stream.read(size)
        self.elf.stream.seek(0)
        return data

    def _write_symbol_data(self, sym_name: str, new_data: bytes):
        off = self._resolve_symbol(ElfSymbol(self.symtab, sym_name))
        self.elf.stream.seek(off)
        self.elf.stream.write(new_data)
        self.elf.stream.seek(0)

    def _prepare_for_signing(self) -> str:
        """Convert the ELF to binary for signing (excluding the signature)."""
        with tempfile.NamedTemporaryFile() as unsigned_bin:
            unsigned_bin.name += ".bin"
            # This command is the most critical part of the signer
            # 1) the conversion to binary strips out the elf headers, symbols, etc.
            # 2) It only includes non-zero sized loadable sections.
            # 3) Gap-fill fills in the gaps with the FLASH_ERASED_VALUE, matching the firmware's default pages.
            # 4) This is the input to the hash computation.

            # Hash generation between firmware and this signer will not match if sections are missing or zero sized.
            # Sections missing at the start and end of the binary are not gapfilled, see (#2).

            # The meson files determine the sections and their addresses.
            subprocess.run(
                [
                    "arm-none-eabi-objcopy",
                    "-O",
                    "binary",
                    self.elf_path,
                    unsigned_bin.name,
                    "--gap-fill",
                    str(hex(self.FLASH_ERASED_VALUE)),
                ]
            )
            return unsigned_bin.name

    def _get_application_size(self) -> int:
        with open(self.partitions_config_path, "r") as f:
            partitions_config = yaml.safe_load(f)

        app_size = None
        for partition in partitions_config["flash"]["partitions"]:
            if "application" in partition["name"]:
                size = partition["size"]
                assert "K" in size
                size = int(size[:-1])  # Remove trailing K
                size = size * 1024
                if app_size is not None:
                    assert size == app_size  # Application partitions should all have the same size
                app_size = size

        return app_size

    def _inject_signature(self, sig: bytes, image_type: str):
        sym_name = self._image_to_sig_sym_name(image_type)
        self._write_symbol_data(sym_name, sig)
        assert self._read_symbol_data(sym_name) == sig

    @abstractmethod
    def _set_build_id(self, slot: str):
        """Set the build ID for the given slot. Must be implemented by subclasses."""
        raise NotImplementedError("Subclasses must implement _set_build_id")

    def verify_signature(self, key_manager: KeyManager, asset_info: AssetInfo) -> bool:
        # NOTE: This only works if the elf file is still opened.
        # The codesign function closes the file/stream/etc
        if self.elf_file.closed:
            self.elf_file = open(self.elf_path, "rb+")
            self.elf = ELFFile(self.elf_file)
            self.symtab = self.elf.get_section_by_name(".symtab")

        digest = self.gen_hash()
        signature = self._read_symbol_data(
            self._image_to_sig_sym_name(asset_info.get_image_type()))

        try:
            key_manager.verify_signature(digest, signature)
            return True
        except ValueError:
            return False

    def gen_hash(self) -> SHA256.SHA256Hash:
        # Convert the ELF to binary to compute the hash over.
        with open(self._prepare_for_signing(), "rb") as f:
            signing_input = f.read()

        off = len(signing_input) - self.ECC_P256_SIG_SIZE
        signing_input = signing_input[:off]

        # Compute the hash
        digest = SHA256.new(signing_input)
        return digest

    def _gen_padding(self, meta_size: int) -> bytes:
        # Pad metadata size to the nearest multiple of 4.
        return b"\xff" * (((meta_size + 3) & ~0x03) - meta_size)

    def stitch_and_finalize(self, key_manager: KeyManager, asset_info: AssetInfo, signature: bytes, digest: SHA256.SHA256Hash):
        image_type = asset_info.get_image_type()
        slot = asset_info.get_slot()

        if image_type == "app" and not slot:
            raise AssertionError("must set slot when signing app")

        # Update the ELF with the signature.
        self._inject_signature(signature, image_type)

        logger.debug(f"sig: {hexlify(signature)}")

        # Verify after signing.
        key_manager.verify_signature(digest, signature)

        self.elf_file.close()

        bin_path = Path(self.elf_path).with_suffix(".bin")

        sig_path = Path(self.elf_path).with_suffix(
            "").with_suffix(".detached_signature")

        # Create a signed .bin that we can flash, which excludes
        # everything past the last bit of code, including the signature
        command = [
            "arm-none-eabi-objcopy",
            "-O",
            "binary",
            str(self.elf_path),
            bin_path,
            "--gap-fill",
            str(hex(self.FLASH_ERASED_VALUE)),
            "--remove-section",
            self._image_to_sig_section(image_type, slot),
            "--remove-section",
            ".fill",
        ]

        if image_type == "bl":
            command += ["--remove-section", ".bl_metadata_section"]

        subprocess.run(command)

        # Detach the signature as a separate bin
        subprocess.run(
            [
                "arm-none-eabi-objcopy",
                "-O",
                "binary",
                self.elf_path,
                sig_path,
                "--gap-fill",
                str(hex(self.FLASH_ERASED_VALUE)),
                "--only-section",
                self._image_to_sig_section(image_type, slot),
            ]
        )

        # Detach the metadata as well, for the bootloader. This is necessary because the BL
        # metadata is at the *end*, not the start.
        if image_type == "bl":
            detached_meta = Path(self.elf_path).with_suffix(
                "").with_suffix(".detached_metadata")
            subprocess.run(
                [
                    "arm-none-eabi-objcopy",
                    "-O",
                    "binary",
                    self.elf_path,
                    detached_meta,
                    "--only-section",
                    ".bl_metadata_section",
                ]
            )

            # Pad it out to the nearest multiple of 4.
            meta_size = os.path.getsize(detached_meta)
            with open(detached_meta, "ab") as f:
                f.write(self._gen_padding(meta_size))
