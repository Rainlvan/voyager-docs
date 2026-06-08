from __future__ import annotations

import base64
import hashlib

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def decrypt_api_key(ciphertext: str | None, secret: str) -> str | None:
    if not ciphertext:
        return None
    payload = base64.b64decode(ciphertext)
    nonce = payload[:12]
    encrypted = payload[12:]
    key = hashlib.sha256(secret.encode("utf-8")).digest()
    return AESGCM(key).decrypt(nonce, encrypted, None).decode("utf-8")
