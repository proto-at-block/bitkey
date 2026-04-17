from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional, Dict, NamedTuple

from bitkey_proto import wallet_pb2 as wallet_pb


class CertificateManagerCert(NamedTuple):
    cert_type: int
    cert_id: str
    name: str


class CertificateManager(ABC):
    @abstractmethod
    def get_cert_peers(self: CertificateManager) -> List[CertificateManagerCert]:
        pass

    def fetch(self, wallet) -> Dict[str, bytes]:
        certs = {}
        for cert_peer in self.get_cert_peers():
            kind = wallet_pb.cert_get_cmd.cert_type.DEVICE_SECURE_CHANNEL_CERT
            rsp = wallet.cert_get(
                kind, cert_id=cert_peer.cert_id, cert_source=cert_peer.cert_type
            )
            if (
                rsp.cert_get_rsp.rsp_status
                == wallet_pb.cert_get_rsp.cert_get_rsp_status.SUCCESS
            ):
                certs[cert_peer.name] = bytes(rsp.cert_get_rsp.cert)
        return certs

    @abstractmethod
    def verify(self, certs: Dict[str, bytes]) -> bool: ...

    @staticmethod
    def from_product(product: str) -> Optional[CertificateManager]:
        if product.lower() == "w3":
            return W3CertificateManager()
        return None


class W3CertificateManager(CertificateManager):
    def get_cert_peers(self: W3CertificateManager) -> List[CertificateManagerCert]:
        return [
            CertificateManagerCert(
                wallet_pb.cert_get_cmd.cert_origin.CERT_ORIGIN_LOCAL,
                "w3_core_id",
                "core_identity",
            ),
            CertificateManagerCert(
                wallet_pb.cert_get_cmd.cert_origin.CERT_ORIGIN_LOCAL,
                "w3_uxc_id",
                "core_pinned_uxc",
            ),
            CertificateManagerCert(
                wallet_pb.cert_get_cmd.cert_origin.CERT_ORIGIN_PEER,
                "w3_core_id",
                "uxc_pinned_core",
            ),
            CertificateManagerCert(
                wallet_pb.cert_get_cmd.cert_origin.CERT_ORIGIN_PEER,
                "w3_uxc_id",
                "uxc_identity",
            ),
        ]

    def verify(self, certs: Dict[str, bytes]) -> bool:
        expected_names = {peer.name for peer in self.get_cert_peers()}
        if not expected_names.issubset(certs.keys()):
            missing = expected_names - certs.keys()
            raise ValueError(f"Missing certs: {missing}")

        if certs["uxc_pinned_core"] != certs["core_identity"]:
            raise ValueError("uxc_pinned_core does not match core_identity")

        if certs["core_pinned_uxc"] != certs["uxc_identity"]:
            raise ValueError("core_pinned_uxc does not match uxc_identity")

        return True
