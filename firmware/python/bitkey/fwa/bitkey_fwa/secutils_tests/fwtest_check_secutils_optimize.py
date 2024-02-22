import sys
import subprocess
from elf_helpers import *
from collections import defaultdict
from cscope_helpers import *
from pathlib import Path
import os
from macro_constants import *
from typing import Dict, List, Tuple


root_fw_dir: Path = Path(__file__).resolve().parents[5]
build_path: str = "build/firmware/app/w1/"


def check_with_cscope(disassembly_count: Dict[str, int]) -> bool:
    """
    Compare the list of disassembly_count with the list obtained from cscope.

    Args:
        disassembly_count (dict): A dictionary of function names and the count of SECUTILS_SYMBOL in each function.

    Returns:
        bool: True if the count matches, False otherwise.
    """
    macros = macro_weight.keys()
    cscope_list = get_function_macro_counts(root_fw_dir, macros)
    unknown_count = 0
    anomalous_functions = defaultdict(int)
    for function, count in disassembly_count.items():
        if (function, count) in cscope_list.items():
            # If the count matches, continue.
            continue
        if function in exception_functions:
            continue
        anomalous_functions[function] = count
        unknown_count += count

    """
    cscope does not consider compile time macros and categorizes some functions as UNUSED.
    For all the functions that were not found in cscope list, add up the count and see if it matches the UNUSED.
    """
    if unknown_count == cscope_list["UNUSED"]:
        return True
    print("Mismatch in secutils_fixed_true usage.")
    print("UNUSED count in cscope: ", cscope_list["UNUSED"])
    for function, count in anomalous_functions.items():
        print(f"{function}: {count}")
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
        print(elf_file_path)
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
            in a different ELF - do not add it. Since cscope only counts it once.
            """
            for func, _ in functions_with_variable.items():
                if result[func] != file_dis_count[func]:
                    result[func] += file_dis_count[func]
    return result


def get_elf_pairs() -> List[Tuple[str, str]]:
    """
    Get matching pairs of *.elf files from the application and loader directories.

    Returns:
        list: A list of tuples containing pairs of (application.elf, loader.elf) files.
    """
    app_dir: Path = root_fw_dir / build_path / "application"
    loader_dir: Path = root_fw_dir / build_path / "loader"
    app_elf_files: List[str] = [file for file in os.listdir(app_dir) if file.endswith(".elf")]
    elf_pairs: List[Tuple[str, str]] = []
    for app_file in app_elf_files:
        loader_file = app_file.replace("app-a", "loader")
        if os.path.exists(loader_dir / loader_file):
            elf_pairs.append((app_dir / app_file, loader_dir / loader_file))
    return elf_pairs


def main():
    """
    Run the secutils optimization test.

    Returns:
        True or False result of the check_with_cscope test. 
    """
    run_cscope(root_fw_dir)
    elf_pairs = get_elf_pairs()
    for pair in elf_pairs:
        # For every pair of files, run the tests.
        print(f"Testing:{pair[0]}, {pair[1]} ")
        disassembly_count = get_total_dis_count([pair[0], pair[1]])
        assert check_with_cscope(disassembly_count), "Mismatch in secutils_fixed_true. Check for unexpected optimizations"


if __name__ == "__main__":
    main()
