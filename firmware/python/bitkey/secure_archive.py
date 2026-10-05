import os
import shutil
import tempfile
from pathlib import Path


def make_zip_archive(source_dir: str | Path) -> Path:
    """Create a ZIP archive without following a destination symlink."""
    source_dir = Path(source_dir)
    archive_path = Path(f"{source_dir}.zip")

    # Build the archive away from its predictable destination, then atomically
    # replace that directory entry. Unlike opening archive_path for writing,
    # os.replace() replaces a symbolic link instead of following it.
    with tempfile.TemporaryDirectory(dir=archive_path.parent) as temporary_dir:
        temporary_archive = shutil.make_archive(
            str(Path(temporary_dir) / source_dir.name),
            "zip",
            str(source_dir),
        )
        os.replace(temporary_archive, archive_path)

    return archive_path
