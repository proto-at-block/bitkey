import os
import subprocess
import tempfile
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional

from macro_constants import macro_weight

# Secutils macros live in first-party source. Keep generated first-party files,
# but avoid indexing multi-gigabyte build, tool, and vendored dependency trees.
CSCOPE_EXCLUDED_DIRECTORIES = {
    ".git",
    ".hermit",
    ".venv",
    "build",
    "node_modules",
    "third-party",
}


def _get_cscope_sources(dir_path: str) -> List[str]:
    base_path = Path(dir_path)
    sources = []
    for root, directories, filenames in os.walk(base_path):
        directories[:] = sorted(
            directory
            for directory in directories
            if directory not in CSCOPE_EXCLUDED_DIRECTORIES
        )
        root_path = Path(root)
        for filename in sorted(filenames):
            if Path(filename).suffix in {".c", ".h"}:
                sources.append((root_path / filename).relative_to(base_path).as_posix())
    return sorted(sources)


def run_cscope(dir_path: str) -> None:
    """
    Build a cscope database for first-party C sources in the given directory.

    Args:
        dir_path (str): The directory path.
    """
    sources = _get_cscope_sources(dir_path)
    if not sources:
        raise RuntimeError("No C sources found for cscope")

    print(f"Running cscope on {len(sources)} source files in {dir_path}")
    with tempfile.TemporaryDirectory(prefix="cscope-") as temporary_directory:
        source_list_path = Path(temporary_directory) / "cscope.files"
        source_list_path.write_text("\n".join(sources), encoding="utf-8")
        result = subprocess.run(
            ["cscope", "-bqk", "-I", ".", "-i", str(source_list_path)],
            cwd=dir_path,
            capture_output=True,
            text=True,
        )
    if result.returncode != 0:
        details = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"cscope database generation failed: {details}")


def get_function_macro_counts(
    dir_path: str, macros: List[str], sources: Optional[List[str]] = None
) -> Dict[str, int]:
    """
    Returns the number of times secutils_fixed_true is used per function.
    This only outputs non zero occurences of secutils_fixed_true.

    Args:
        dir_path (str): The directory path.
        macros (List[str]): The list of macro names.
        sources (List[str]): Optional list of filenames to filter to.

    Returns:
        Dict[str, int]: A dictionary that maps function names to the number of times secutils_fixed_true is used in that function.
    """
    secutils_count = defaultdict(int)

    for macro_name in macros:
        result = subprocess.run(
            ["cscope", "-RLd", "-3", macro_name],
            cwd=dir_path,
            capture_output=True,
            text=True,
            check=True,
        )
        lines = result.stdout.split("\n")
        for line in lines:
            fields = line.split()
            if len(fields) > 1:
                if fields[1] in macros:
                    # Skip if function name matches a macro name.
                    continue
                elif sources and fields[0] not in sources:
                    # File name is not in the source file inclusion list.
                    continue
                function_name = fields[1]
                secutils_count[function_name] += macro_weight[macro_name]
    return secutils_count
