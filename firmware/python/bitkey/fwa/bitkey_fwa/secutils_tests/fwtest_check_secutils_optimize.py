import argparse
import itertools
import logging
import os
import sys
import subprocess
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Tuple, Optional

from cscope_helpers import get_function_macro_counts, run_cscope
from elf_helpers import get_functions_with_variable, get_sources
from macro_constants import SECUTILS_SYMBOL, macro_weight, exception_functions

logger: logging.Logger = logging.getLogger(__name__)
root_fw_dir: Path = Path(__file__).resolve().parents[5]


def check_with_cscope(disassembly_count: Dict[str, int], sources: Optional[List[str]] = None) -> bool:
    """
    Compare the list of disassembly_count with the list obtained from cscope.

    Args:
        disassembly_count (dict): A dictionary of function names and the count of SECUTILS_SYMBOL in each function.
        sources (List[str]): Optional list of source files belonging to the target.

    Returns:
        bool: True if the count matches, False otherwise.
    """
    macros = macro_weight.keys()
    cscope_list = get_function_macro_counts(root_fw_dir, macros, sources=sources)
    unknown_count = 0
    anomalous_functions = {}
    for function, expected_count in disassembly_count.items():
        if function in exception_functions:
            continue
        elif function in cscope_list:
            actual_count = cscope_list[function]
            if expected_count == actual_count:
                # If the count matches, continue.
                continue
        else:
            actual_count = 0
        anomalous_functions[function] = (expected_count, actual_count)
        unknown_count += expected_count

    """
    cscope does not consider compile time macros and categorizes some functions as UNUSED.
    For all the functions that were not found in cscope list, add up the count and see if it matches the UNUSED.
    """
    if unknown_count == cscope_list.get("UNUSED", 0):
        return True

    print("Mismatch in secutils_fixed_true usage.")
    print("UNUSED count in cscope: ", cscope_list.get("UNUSED", 0))
    print("Unknown count in disassembly: ", unknown_count)
    for function, (expected_count, actual_count) in anomalous_functions.items():
        print(f"    {function}: {expected_count=}, {actual_count=}")
    return False


def get_total_dis_count(elf_list: List[str]) -> Dict[str, int]:
    """
    Get the count of SECUTILS_SYMBOL occurrences in each function.

    Args:
        elf_list (list): A list of ELF file paths.

    Returns:
        dict: A dictionary of function names and the count of SECUTILS_SYMBOL in each function.
    """
    result = defaultdict(int)
    for elf_file_path in elf_list:
        logger.info(f"Analyzing ELF file: {elf_file_path}")
        functions_with_variable = get_functions_with_variable(elf_file_path, SECUTILS_SYMBOL)
        file_dis_count = defaultdict(int)
        for func, addresses in functions_with_variable.items():
            for var_addr in addresses:
                disassemble_cmd = '--disassemble='+func
                objdump_res = subprocess.run(['arm-none-eabi-objdump', disassemble_cmd, elf_file_path], capture_output=True, text=True, check=True)
                disassembly = objdump_res.stdout.strip()
                file_dis_count[func] += disassembly.count(var_addr[2:])-1
        # Check if this is the first file we are checking the disassembly for. If so, add file_dis_count to result.
        if not bool(result):
            result = file_dis_count
        else:
            """
            We are iterating through a pair of ELF files (application and loader.elf).
            Some functions are found in both the ELF files. For example, we have main()in both application.elf
            and loader.elf. But the count of secutils_fixed_true is different in both the ELF files.
            So we need to add the count of secutils_fixed_true in both the ELF files.
            If the count is the same in both the files, then it is a repeat of the same function
            in a different ELF - do not add it. Since cscope only counts it once. This excludes
            'main', which cannot be duplicated.
            """
            for func, _ in functions_with_variable.items():
                if result[func] != file_dis_count[func] or func == "main":
                    result[func] += file_dis_count[func]
    return result


def get_elf_pairs() -> List[Tuple[str, str]]:
    """
    Get matching pairs of *.elf files from the application and loader directories.

    Returns:
        list: A list of tuples containing pairs of (application.elf, loader.elf) files.
    """
    build_dir: Path = root_fw_dir / "build/firmware"
    elf_pairs: List[Tuple[str, str]] = []
    for product in os.listdir(build_dir):
        product_dir: Path = build_dir / product
        if not product_dir.is_dir():
            logger.warning(f"Skipping non-directory in build_dir: {product_dir}")
            continue
        build_path: str = f"build/firmware/{product}/app/{product}"
        app_dir: Path = root_fw_dir / build_path / "application"
        loader_dir: Path = root_fw_dir / build_path / "loader"
        if not app_dir.is_dir():
            logger.warning(f"Application directory does not exist: {app_dir}")
            continue
        if not loader_dir.is_dir():
            logger.warning(f"Loader directory does not exist: {loader_dir}")
            continue
        app_elf_files: List[str] = [file for file in os.listdir(app_dir) if file.endswith(".elf")]
        for app_file in app_elf_files:
            loader_file = app_file.replace("app-a", "loader")
            if (loader_dir / loader_file).exists():
                elf_pairs.append((app_dir / app_file, loader_dir / loader_file))
    return elf_pairs


def main(args: Optional[List[str]] = None) -> None:
    """
    Run the secutils optimization test.

    Returns:
        True or False result of the check_with_cscope test.
    """
    if args is None:
        args = sys.argv[1:]

    parser = argparse.ArgumentParser(prog="fwtest-check-secutils-optimize")
    parser.add_argument("-v", "--verbose", action="store_true", help="increase program verbosity")

    parsed = parser.parse_args(args)
    if parsed.verbose:
        logging.basicConfig(level=logging.DEBUG)

    run_cscope(root_fw_dir)
    elf_pairs = get_elf_pairs()
    for pair in elf_pairs:
        # For every pair of files, run the tests.
        print(f"Testing: Application={pair[0]}, Bootloader={pair[1]}")
        disassembly_count = get_total_dis_count([pair[0], pair[1]])

        # Compute the source paths in order to filter out un-related files.
        relative_sources: List[str] = list(
            set(itertools.chain.from_iterable(get_sources(elf) for elf in pair))
        )
        sources: List[str] = []

        # The source paths are relative to the build directory, so we have to
        # trim out the prefix.
        for relative_source in relative_sources:
            source = relative_source.replace("../../../", "")
            if source.startswith("/") and not os.path.exists(source):
                # Path is absolute from the project directory, but does not
                # appear as such in the source file list, so trim it.
                source = source[1:]
            sources.append(source)

        if not check_with_cscope(disassembly_count, sources):
            raise RuntimeError("Mismatch in secutils_fixed_true. Check for unexpected optimizations.")


if __name__ == "__main__":
    main()
