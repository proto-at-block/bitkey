import sys
import shutil
import pathlib
import tempfile
import requests
import functools
import hashlib

from zipfile import ZipFile
from invoke import task
from tqdm.auto import tqdm

from .lib.paths import CONFIG_DIR


SILABS_DFP_VERSION = "4.1.1"
SILABS_DFP_SHA256 = (
    "348b6fe22b6645193da283f2887a809f9f1d34b556a0d94a7a4a4a5561bd2b38"
)


@task
def python(c):
    """Updates python dependencies with pip"""
    c.run(f"pip install -r requirements.txt")


@task
def test_deps(c):
    """Installs dependencies for testing"""
    _install_criterion(c)
    _install_openssl(c)


@task
def tools(c):
    """Installs tools not installed with Hermit or pip"""
    if "darwin" in sys.platform:
        c.run(f"brew update")
        c.run("brew install screen silabs-commander")


@task
def jlink(c):
    """Installs the segger jlink drivers and tools"""
    if "darwin" in sys.platform:
        c.run(f"brew update")
        c.run(f"brew install segger-jlink")


@task
def svd(c):
    """Installs the EFR32MG24 svd file from SiLabs"""
    svd_file = "EFR32MG24B010F1536IM48.svd"
    dfp_url = (
        "https://www.silabs.com/documents/public/cmsis-packs/"
        f"SiliconLabs.GeckoPlatform_EFR32MG24_DFP.{SILABS_DFP_VERSION}.pack"
    )

    print(f"Downloading: {dfp_url}")
    with tempfile.NamedTemporaryFile("wb", suffix=".zip") as file:
        _download(dfp_url, file.name)
        _verify_sha256(file.name, SILABS_DFP_SHA256)

        with ZipFile(file.name, 'r') as zip_ref:
            # Find the SVD file in the zip sub-directories
            for zip_file in zip_ref.filelist:
                if zip_file.filename.endswith(svd_file):
                    with open(CONFIG_DIR.joinpath(svd_file), 'wb') as f:
                        f.write(zip_ref.read(zip_file.filename))

        if CONFIG_DIR.joinpath(svd_file).exists():
            print("SVD file download complete")
        else:
            print("SVD file download failed")


def _download(url: str, filename: str) -> str:
    """Downloads a file while showing a progress bar"""
    # Source: https://stackoverflow.com/a/63831344
    r = requests.get(url, stream=True, allow_redirects=True)
    if r.status_code != 200:
        r.raise_for_status()
        raise RuntimeError(
            f"Request to {url} returned status code {r.status_code}")
    file_size = int(r.headers.get('Content-Length', 0))

    path = pathlib.Path(filename).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)

    desc = "(Unknown total file size)" if file_size == 0 else ""
    r.raw.read = functools.partial(r.raw.read, decode_content=True)
    with tqdm.wrapattr(r.raw, "read", total=file_size, desc=desc) as r_raw:
        with path.open("wb") as f:
            shutil.copyfileobj(r_raw, f)

    return path


def _verify_sha256(filename: str, expected_sha256: str) -> None:
    """Raise if a file does not match its expected SHA-256 digest."""
    path = pathlib.Path(filename).expanduser().resolve()
    with path.open("rb") as file:
        actual_sha256 = hashlib.file_digest(file, "sha256").hexdigest()

    if actual_sha256 != expected_sha256:
        raise RuntimeError(
            f"SHA-256 mismatch for {path}: expected {expected_sha256}, "
            f"got {actual_sha256}"
        )


def _install_criterion(c):
    """Install Criterion with Homebrew on macOS or Hermit on Linux."""
    if sys.platform == "darwin":
        c.run(f"brew install criterion")
    elif "linux" in sys.platform:
        c.run("bin/criterion-root")


def _install_openssl(c):
    """Install OpenSSL 1."""
    if sys.platform == "darwin":
        c.run(f"brew install openssl")
    elif "linux" in sys.platform:
        c.run(f"sudo apt-get install --yes libssl-dev")
