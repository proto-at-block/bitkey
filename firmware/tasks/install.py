import sys
import shutil
import pathlib
import tarfile
import tempfile
import requests
import functools

from zipfile import ZipFile
from invoke import task
from pathlib import Path
from tqdm.auto import tqdm

from .lib.paths import (ROOT_DIR, CONFIG_DIR)


@task
def python(c):
    """Updates python dependencies with pip"""
    c.run(f"pip install Cython")
    c.run(f"pip install -r requirements.txt")


@task
def test_deps(c):
    """Installs dependencies for testing"""
    _install_criterion(c, "2.4.2")
    _install_openssl(c)


@task
def tools(c):
    """Installs tools not installed with Hermit or pip"""
    if "darwin" in sys.platform:
        c.run(f"brew update")
        c.run(f"brew install clang-format screen silabs-commander")


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
    version = "4.1.1"
    dfp_url = f"https://www.silabs.com/documents/public/cmsis-packs/SiliconLabs.GeckoPlatform_EFR32MG24_DFP.{version}.pack"

    print(f"Downloading: {dfp_url}")
    with tempfile.NamedTemporaryFile("wb", suffix=".zip") as file:
        _download(dfp_url, file.name)

        with ZipFile(file.name, 'r') as zip_ref:
            # Find the SVD file in the zip sub-directories
            for zip_file in zip_ref.filelist:
                if zip_file.filename.endswith(svd_file):
                    with open(CONFIG_DIR.joinpath(svd_file), 'wb') as f:
                        f.write(zip_ref.read(zip_file.filename))

        if Path(svd_file).exists:
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


def _install_criterion(c, version):
    """Install Criterion. For MacOS, use brew. For Ubuntu, download it from GH Releases.
    Criterion does not have a PPA for Ubuntu Focal, which is the latest available on GH Actions."""
    if sys.platform == "darwin":
        c.run(f"brew install criterion")
    else:
        file = f"criterion-{version}-linux-x86_64.tar.xz"
        url = f"https://github.com/Snaipe/Criterion/releases/download/v{version}/{file}"
        response = requests.get(url, stream=True)
        third_party = ROOT_DIR.joinpath("third-party")

        criterion_dest = third_party.joinpath("criterion")
        if criterion_dest.exists():
            criterion_dest.rmdir()

        response = requests.get(url, stream=True)
        if response.status_code == 200:
            with tempfile.TemporaryDirectory() as tmpdir:
                tmppath = Path(tmpdir)
                tar = tmppath.joinpath(file)
                with open(tar, "wb") as f:
                    f.write(response.raw.read())
                f = tarfile.open(tar)
                f.extractall(third_party)
                f.close()
                third_party.joinpath(
                    f"criterion-{version}").rename(criterion_dest)
                print(f"Downloaded Criterion to {criterion_dest.absolute()}")
        else:
            print(
                f"Failed to download Criterion, got {response.status_code} '{response.reason}'")


def _install_openssl(c):
    """Install OpenSSL 1."""
    if sys.platform == "darwin":
        c.run(f"brew install openssl")
    elif "linux" in sys.platform:
        c.run(f"sudo apt-get install --yes libssl-dev")
