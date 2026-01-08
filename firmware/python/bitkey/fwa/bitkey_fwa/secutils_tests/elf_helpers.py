from collections import defaultdict
from typing import Set

from elftools.elf.elffile import ELFFile


def get_address_of_variable(elf_file_path: str, variable_name: str) -> int:
    """
    Find the address of a given variable in an ELF file.

    Args:
        elf_file_path (str): The path to the ELF file.
        variable_name (str): The name of the variable.

    Returns:
        int: The address of the variable if found, None otherwise.
    """
    with open(elf_file_path, 'rb') as file:
        elf_file = ELFFile(file)
        # Iterate over sections to find the symbol table
        symbol_table = None
        for section in elf_file.iter_sections():
            if section.name == '.symtab':
                symbol_table = section
                break
        else:
            raise ValueError("Symbol table (.symtab) not found")

        # Get the base address of the symbol table
        base_address = symbol_table['sh_addr']
        # Iterate over symbols in the symbol table
        variable_address = None
        for symbol in symbol_table.iter_symbols():
            if symbol.name == variable_name:
                variable_address = base_address + symbol['st_value']
                break
        return variable_address


def get_variable_references(elf_file_path: str, variable_name: str) -> list[int]:
    """
    Returns a list of addresses where the variable is referenced in the ELF file.

    Args:
        elf_file_path (str): The path to the ELF file.
        variable_name (str): The name of the variable.

    Returns:
        list[int]: A list of addresses where the variable is referred.
    """
    with open(elf_file_path, 'rb') as file:
        elf_file = ELFFile(file)
        variable_address = get_address_of_variable(elf_file_path, variable_name)
        # Check if the variable was found in the symbol table
        if variable_address is None:
            raise ValueError(f"Variable '{variable_name}' not found in the symbol table")

        text_section = elf_file.get_section_by_name('.text')
        if text_section is None:
            raise ValueError("Text section (.text) not found")

        text_data = text_section.data()
        offset = 0
        occurrences = []
        while True:
            index = text_data.find(variable_address.to_bytes(4,'little'), offset)
            if index == -1:
                break
            address = text_section['sh_addr'] + index
            occurrences.append(address)
            offset = index + 1
        return occurrences


def get_all_functions(elf_file_path: str) -> list[dict]:
    """
    Returns all functions in the elf along with their high and low pc address

    Args:
        elf_file_path (str): The path to the ELF file.

    Returns:
        list[dict]: A sorted list of all functions in the given ELF file, 
                where each function is represented as a dictionary with keys 'name', 'low_address', and 'high_address'.
    """
    with open(elf_file_path, 'rb') as file:
        elf_file = ELFFile(file)

        # Get the DWARF information
        dwarf_info = elf_file.get_dwarf_info()

        functions = []

        # Iterate over all DIEs in all CUs:
        for cu in dwarf_info.iter_CUs():
            for die_info in cu.iter_DIEs():
                try:
                    if die_info.tag == "DW_TAG_subprogram":
                        function_name = die_info.attributes['DW_AT_name'].value
                        function_low_address = die_info.attributes['DW_AT_low_pc'].value
                        function_high_address = function_low_address + die_info.attributes['DW_AT_high_pc'].value
                        functions.append({'name': function_name, 
                                          'low_address': hex(function_low_address), 
                                          'high_address': hex(function_high_address)})
                except KeyError:
                    continue
            # Return a sorted list so that its easier to search
            sorted_functions = sorted(functions, key=lambda x: int(x['low_address'],16))
        return sorted_functions


def _find_addr_in_function(functions: list[dict], address: int) -> str:
    """
    Given an address and a list of functions, determines which function the address belongs to.

    Args:
        functions (list[dict]): A list of functions in the ELF file.
        address (int): The address to search for.

    Returns:
        str: The name of the function to which the address belongs.
        None: Returns None if the address is not found within any function.
    """
    for function in functions:
        if int(function['low_address'], 16) <= address < int(function['high_address'], 16):
            return str(function['name'].decode())
        if int(function['low_address'], 16) > address:
            return None
    return None


def get_functions_with_variable(elf_file_path: str, variable_name: str) -> dict[str, list]:
    """
    Find all functions that reference a variable and the addresses where the variable is used within each function.
    Takes the path to the ELF file and the name of the variable as input.

    Args:
        elf_file_path (str): The path to the ELF file.
        variable_name (str): The name of the variable.

    Returns:
        dict: A dictionary that maps function names to a list of addresses.
            Each address represents a location where the variable is used within the corresponding function.
    """

    ret_func_dict = defaultdict(list)
    references = get_variable_references(elf_file_path, variable_name)
    functions = get_all_functions(elf_file_path)

    # Find which functions include the reference addresses:
    for ref in references:
        func = _find_addr_in_function(functions, ref)
        if func is not None:
            ret_func_dict[func].append(hex(ref))
    return ret_func_dict


def get_sources(elf_file_path: str) -> Set[str]:
    """
    Returns a set of source files that were compiled into the given ELF file.

    Args:
        elf_file_path (str): Path to the ELF file.

    Returns:
        Set of source file paths from the root directory.
    """
    with open(elf_file_path, "rb") as f:
        elf_file = ELFFile(f)
        dwarf_info = elf_file.get_dwarf_info()

        source_files = set()
        for compile_unit in dwarf_info.iter_CUs():
            # Get the line program for the current compile unit.
            line_program = dwarf_info.line_program_for_CU(compile_unit)

            # Iterate through the file entries in the line program.
            for entry in line_program.header.file_entry:
                dir_index = entry.dir_index
                fname = entry.name.decode("utf-8")

                # Resolve the directory if it's a relative path
                if dir_index > 0:
                    # Directory entries are 1-indexed in DWARF.
                    directory = line_program.header.include_directory[dir_index - 1].decode("utf-8")
                    fpath = f"{directory}/{fname}"
                else:
                    # If dir_index is 0, the path might be absolute or relative to the
                    # compilation directory. For simplicity, we'll just use the file name
                    # as is, assuming it's either absolute or the compilation directory is
                    # implicit. More robust handling might involve DW_AT_comp_dir.
                    fpath = fname
                source_files.add(fpath)
    return source_files
