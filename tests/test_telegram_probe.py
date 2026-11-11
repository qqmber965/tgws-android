"""Local MTProto fixture: ensures probe needs a valid resPQ, not just an open port.

No external network is required. Monkeypatch the local proxy port to an
in-process mock MTProto server which understands the obfuscated transport.
"""
import hashlib
import os
import socket
import struct
import sys
import threading
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
import mobile_runner
from proxy._aes import Cipher, algorithms, modes

SECRET = '00112233445566778899aabbccddeeff'


def run_server(should_respond=True, bad_nonce=False):
    server = socket.socket()
    server.bind(('127.0.0.1', 0))
    server.listen(1)
    port = server.getsockname()[1]
    errors = []

    def recv_exact(conn, n):
        out = bytearray()
        while len(out) < n:
            chunk = conn.recv(n - len(out))
            if not chunk:
                raise ConnectionError('client disconnected')
            out.extend(chunk)
        return bytes(out)

    def task():
        try:
            with server:
                conn, _ = server.accept()
                with conn:
                    conn.settimeout(2)
                    handshake = recv_exact(conn, 64)
                    prekey = handshake[8:40]
                    key = hashlib.sha256(prekey + bytes.fromhex(SECRET)).digest()
                    decryptor = Cipher(algorithms.AES(key), modes.CTR(handshake[40:56])).encryptor()
                    decoded = decryptor.update(handshake)
                    assert decoded[56:60] == b'\xee' * 4
                    assert int.from_bytes(decoded[60:62], 'little') == 2
                    length = int.from_bytes(decryptor.update(recv_exact(conn, 4)), 'little')
                    request = decryptor.update(recv_exact(conn, length))
                    assert request[:8] == b'\x00' * 8
                    assert request[20:24] == struct.pack('<I', 0xbe7e8ef1)
                    nonce = request[24:40]
                    if not should_respond:
                        return
                    response_body = struct.pack('<I', 0x05162463) + (os.urandom(16) if bad_nonce else nonce) + os.urandom(16) + b'\x00'*8
                    packet = b'\x00'*8 + os.urandom(8) + struct.pack('<I', len(response_body)) + response_body
                    rev = handshake[8:56][::-1]
                    answer_key = hashlib.sha256(rev[:32] + bytes.fromhex(SECRET)).digest()
                    enc = Cipher(algorithms.AES(answer_key), modes.CTR(rev[32:])).encryptor()
                    response = struct.pack('<I', len(packet)) + packet
                    conn.sendall(enc.update(response))
        except Exception as exc:
            errors.append(exc)
    thread = threading.Thread(target=task)
    thread.start()
    return port, thread, errors


def test_telegram_probe_confirmed():
    port, t, errors = run_server()
    assert mobile_runner.probe_telegram(SECRET, port, 2, 2) is True
    t.join(timeout=4)
    assert not errors


def test_telegram_probe_rejects_wrong_nonce():
    port, t, errors = run_server(bad_nonce=True)
    assert mobile_runner.probe_telegram(SECRET, port, 2, 2) is False
    t.join(timeout=4)
    assert not errors


def test_telegram_probe_does_not_treat_open_socket_as_success():
    port, t, errors = run_server(should_respond=False)
    assert mobile_runner.probe_telegram(SECRET, port, 2, 2) is False
    t.join(timeout=4)
    assert not errors


if __name__ == '__main__':
    test_telegram_probe_confirmed()
    test_telegram_probe_rejects_wrong_nonce()
    test_telegram_probe_does_not_treat_open_socket_as_success()
    print('PASS: genuine resPQ nonce validation')
