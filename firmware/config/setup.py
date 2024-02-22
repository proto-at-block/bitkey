import setuptools

setuptools.setup(
    name="wallet-signer",
    author="wallet.build firmware team",
    version="0.0.1",
    description="Offline signing tools",
    packages=setuptools.find_packages(),
    entry_points={
        "console_scripts": [
            "w1-signer = keys.wallet_firmware_signer:cli",
        ]
    },
    python_requires=">=3.10",
    install_requires=[
        "pyelftools>=0.29",
        "semver==2.13.0",
        "pycryptodome==3.19.1",
        "click==8.1.3",
        "PyYAML==6.0",
        "sh==2.0.1",
    ],
)
