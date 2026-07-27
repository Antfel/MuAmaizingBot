#!/usr/bin/env python3
"""Encrypt Telegram bot token for TelegramEndpoint.kt (AES-128-CBC, same as LicenseEndpoint)."""
from __future__ import annotations

import base64
import hashlib
import os
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/example/muamaizingbot/telegram/TelegramEndpoint.kt"


def derive_key() -> bytes:
    a = "muamaizing"
    b = "|telegram|v1|"
    c = "com.example.muamaizingbot"
    md = hashlib.sha256()
    md.update(a.encode())
    md.update(b.encode())
    md.update(c.encode())
    return md.digest()[:16]


def pkcs7_pad(data: bytes, block_size: int = 16) -> bytes:
    pad = block_size - (len(data) % block_size)
    return data + bytes([pad] * pad)


def aes_cbc_encrypt(key: bytes, iv: bytes, plain: bytes) -> bytes:
    try:
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    except ImportError as e:
        raise SystemExit(
            "Install cryptography: pip install cryptography\n"
            "Or run from a venv that has it."
        ) from e

    encryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    return encryptor.update(pkcs7_pad(plain)) + encryptor.finalize()


def encrypt(plain: str) -> tuple[str, str]:
    key = derive_key()
    iv = os.urandom(16)
    enc = aes_cbc_encrypt(key, iv, plain.encode("utf-8"))
    return base64.b64encode(iv).decode(), base64.b64encode(enc).decode()


def patch_kotlin(wrap_a: str, wrap_b: str) -> None:
    text = TARGET.read_text(encoding="utf-8")
    text = re.sub(
        r'private val WRAP_A = "[^"]*"',
        f'private val WRAP_A = "{wrap_a}"',
        text,
        count=1,
    )
    text = re.sub(
        r'private val WRAP_B = "[^"]*"',
        f'private val WRAP_B = "{wrap_b}"',
        text,
        count=1,
    )
    TARGET.write_text(text, encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <telegram_bot_token>", file=sys.stderr)
        sys.exit(1)
    token = sys.argv[1].strip()
    if not token or ":" not in token:
        print("Invalid token format", file=sys.stderr)
        sys.exit(1)
    if not TARGET.exists():
        print(f"Missing {TARGET}", file=sys.stderr)
        sys.exit(1)
    wrap_a, wrap_b = encrypt(token)
    patch_kotlin(wrap_a, wrap_b)
    print(f"Updated {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
