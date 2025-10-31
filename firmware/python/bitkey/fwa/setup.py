import os

import setuptools

with open("README.md") as readme_file:
    readme = readme_file.read()

requirements = [
    "click==8.1.7",
    "pyelftools==0.32",
    "pytz==2023.3.post1",
    "pycryptodome==3.20.0",
    "PyYAML==6.0.1",
]

setuptools.setup(
    name="bitkey-fwa",
    version="1.0.2",
    description="Bitkey firmware analysis",
    long_description=readme,
    author="HWSEC",
    packages=setuptools.find_packages(),
    include_package_data=True,
    install_requires=requirements,
    tests_require=[],
    entry_points={"console_scripts": ["bitkey-fwa=bitkey_fwa.__main__:cli"]},
    dependency_links=[
        os.environ.get("PYTHON_DIST_DIR", os.getcwd()),
    ],
    zip_safe=False,
)
