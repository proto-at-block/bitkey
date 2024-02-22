"""A collection of utility functions to aid in navigating pyelftools dwarf structures

Some useful acronyms:
* CU: Compile Unit
* DIE: Debugging Information Entry
"""

import fnmatch
import operator
from typing import Optional, Callable
from collections.abc import Generator

from elftools.dwarf.compileunit import CompileUnit
from elftools.dwarf.die import DIE
from elftools.dwarf.dwarfinfo import DWARFInfo


def die_has_tag_value(die: DIE, tag: str, value: bytes, op: Callable = operator.eq) -> bool:
    """Check that the die has a tag with the given value.

    Performs a lot of checks that get really redundant really fast.

    Args:
        die (DIE): Debugging Information Entry object
        tag (unicode): Tag to look for
        value (bytes): Value to match on
        op (callable): Operator function that is given the die's attribute and the given 'value' argument

    Returns:
        (bool): True if the die has the given tag with the given value (verified via 'op')
    """
    if not die.attributes:
        return False

    attrib = die.attributes.get(tag, None)

    if not attrib:
        return False

    if not attrib.value:
        return False

    return op(attrib.value, value)


def get_cu_containing_offset(dwarf: DWARFInfo, offset: int) -> Optional[CompileUnit]:
    """Get a CU that covers a given offset

    Args:
        dwarf (DWARFInfo): Dwarf info object
        offset (int): Offset into the debug info stream

    Returns:
        (CompileUnit|None): CU object that covers the given offset, or None if not found
    """
    last_cu = None
    for cu in dwarf.iter_CUs():
        if cu.cu_offset > offset:
            return last_cu
        last_cu = cu

    # Not found
    return None


def get_die_at_offset(dwarf: DWARFInfo, offset: int, cu: Optional[CompileUnit] = None) -> DIE:
    """Get a DIE for a given offset

    The currently released pyelftools version (0.26) lacks this functionality. However, the master branch does. Once
    a new release is available, we can remove this function and use the elftools method, or not.

    Args:
        dwarf (DWARFInfo): Dwarf info object
        offset (int): Offset into the debug info stream
        cu (CompileUnit|None): the reference CU object for the DIE

    Returns:
        (DIE): DIE object at the given offset
    """
    # Need a reference CU for the DIE
    if cu is None:
        cu = get_cu_containing_offset(dwarf, offset)

    # Construct a DIE directly from the stream
    return DIE(cu, dwarf.debug_info_sec.stream, offset)


def gen_producers(dwarf: DWARFInfo, filename_pattern: bytes) -> Generator[bytes, bytes]:
    """Generate tuples of (producer, filename) strings from the dwarf CU info

    Useful to examine how each file was compiled.

    Args:
        dwarf (DWARFInfo): Dwarf info object
        filename_pattern (bytes): Optional fnmatch to filter file names by

    Yields:
        (bytes, bytes): Producer string, filename string for every DW_AT_producer found
    """
    for cu in dwarf.iter_CUs():
        die = cu.get_top_DIE()
        if "DW_AT_producer" in die.attributes:
            producer = die.attributes["DW_AT_producer"].value
            filename = die.attributes["DW_AT_name"].value

            if filename_pattern and not fnmatch.fnmatch(filename, filename_pattern):
                continue

            yield producer, filename


def get_function_from_file(dwarf: DWARFInfo, function: bytes, file_fnmatch: bytes) -> Optional[DIE]:
    """Get the dwarf subprogram (DW_TAG_subprogram) for the given function in a file

    TODO: Maybe make this a generator and support regex on all arguments?

    Args:
        dwarf (DWARFInfo): Dwarf info object
        function (bytes): Function name
        file_fnmatch (bytes): File name, supporting fnmatch syntax

    Returns:
        (DIE|None): DW_TAG_subprogram DIE, or None if not found
    """
    for cu in dwarf.iter_CUs():
        die = cu.get_top_DIE()

        # Find the parent CU that is for the given file
        if not die_has_tag_value(die, "DW_AT_name", file_fnmatch, fnmatch.fnmatch):
            continue

        # Look for the subprogram within the matching file's CU
        for die in die.iter_children():
            if die.tag != "DW_TAG_subprogram":
                continue
            if die_has_tag_value(die, "DW_AT_name", function):
                return die

    # Not found
    return None


def die_search(dwarf: DWARFInfo, die: DIE, accepted_tags: tuple, recurse_tags: tuple) -> DIE:
    """Function that supports recursive searching through a given DIE for particular tags

    If a DW_AT_abstract_origin is found, perform the DIE lookup

    Args:
        dwarf (DWARFInfo): Dwarf info object
        die (DIE): The DIe that may or may not contain function calls
        accepted_tags: Sequence of unicode tags that we will yield from
        recurse_tags: Sequence of unicode tags that will trigger a recursive search

    Yields:
        DIE: die element that matches accepted_tags
    """
    for child in die.iter_children():
        # Recurse?
        if child.tag in recurse_tags:
            for f in die_search(dwarf, child, accepted_tags, recurse_tags):
                yield f
            continue

        # Skip if this is not the tag we're looking for
        if child.tag not in accepted_tags:
            continue

        # Handle abstract origin redirection shenanigans
        if "DW_AT_abstract_origin" in child.attributes:
            origin = child.attributes["DW_AT_abstract_origin"]
            yield get_die_at_offset(dwarf, origin.value + die.cu.cu_offset, die.cu)
            continue

        yield child


def gen_function_call_names(dwarf: DWARFInfo, die: DIE) -> Generator[bytes]:
    """Generate a list of function calls the given die makes.

    Args:
        dwarf (DWARFInfo): Dwarf info object
        die (DIE): The DIE that may or may not contain function calls

    This will recursively look for function calls within DW_TAG_lexical_block's

    This supports two possible tags:
        DW_TAG_GNU_call_site: Version 4 non-standard extension (but most tools support it, it seems)
        DW_TAG_call_site: Version 5 standard

    Yields:
        (unicode): Name of every call found within the subprogram
    """
    for f in die_search(dwarf, die, ("DW_TAG_GNU_call_site", "DW_TAG_call_site"), ("DW_TAG_lexical_block",)):
        if "DW_AT_name" in f.attributes:
            yield f.attributes["DW_AT_name"].value


def gen_variable_names(dwarf: DWARFInfo, die: DIE) -> Generator[bytes]:
    """Generate a list of variables within the given die.

    Args:
        dwarf (DWARFInfo): Dwarf info object
        die (DIE): The DIE that may or may not contain variables

    This will recursively look for variables within DW_TAG_lexical_block's as well

    This matches on the DW_TAG_variable tag

    Yields:
        (unicode): Name of every variable found within the subprogram
    """
    for f in die_search(dwarf, die, ("DW_TAG_variable",), ("DW_TAG_lexical_block",)):
        yield f.attributes["DW_AT_name"].value
