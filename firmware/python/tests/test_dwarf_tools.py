from types import SimpleNamespace

import pytest
from bitkey_fwa import decorators, dwarf_tools
from bitkey_fwa.firmware_tests.fwtest_check_symbols import SymbolChecks


EXPECTED_AUTH_CALLS = (
    b"secure_glitch_random_delay",
    b"secure_glitch_detect",
    b"rtos_mutex_lock",
    b"rtos_mutex_unlock",
)


def die(tag, name, *children):
    return SimpleNamespace(
        tag=tag,
        attributes={"DW_AT_name": SimpleNamespace(value=name)},
        iter_children=lambda: iter(children),
    )


def function(name, *calls):
    return die(
        "DW_TAG_subprogram",
        name,
        *(die("DW_TAG_call_site", call) for call in calls),
    )


def compile_unit(filename, *functions):
    top_die = die("DW_TAG_compile_unit", filename, *functions)
    return SimpleNamespace(get_top_DIE=lambda: top_die)


def dwarf(*compile_units):
    return SimpleNamespace(iter_CUs=lambda: iter(compile_units))


def test_returns_unique_match():
    expected = function(b"is_authenticated")
    dwarf_info = dwarf(
        compile_unit(b"/other/auth.c", function(b"another_function")),
        compile_unit(b"/real/auth.c", expected),
    )

    actual = dwarf_tools.get_function_from_file(
        dwarf_info, b"is_authenticated", b"*/auth.c"
    )

    assert actual is expected


def test_ambiguous_primary_glob_does_not_fall_back_to_decoy(monkeypatch):
    dwarf_info = dwarf(
        compile_unit(b"/attacker/auth.c", function(b"is_authenticated")),
        compile_unit(b"/real/auth.c", function(b"is_authenticated")),
        compile_unit(
            b"/attacker/auth_task.c",
            function(b"is_authenticated", *EXPECTED_AUTH_CALLS),
        ),
    )
    check = SymbolChecks("fwtest_elf_check_for_function_regressions")
    monkeypatch.setattr(check, "get_dwarf_section", lambda: dwarf_info)
    monkeypatch.setattr(decorators, "skip_unless_firmware_applies", lambda _: True)

    with pytest.raises(dwarf_tools.AmbiguousFunctionError):
        check.fwtest_elf_check_for_function_regressions()


def test_missing_primary_glob_falls_back(monkeypatch):
    dwarf_info = dwarf(
        compile_unit(b"/real/auth.c", function(b"another_function")),
        compile_unit(
            b"/real/auth_task.c",
            function(b"is_authenticated", *EXPECTED_AUTH_CALLS),
        ),
    )
    check = SymbolChecks("fwtest_elf_check_for_function_regressions")
    monkeypatch.setattr(check, "get_dwarf_section", lambda: dwarf_info)
    monkeypatch.setattr(decorators, "skip_unless_firmware_applies", lambda _: True)

    check.fwtest_elf_check_for_function_regressions()
