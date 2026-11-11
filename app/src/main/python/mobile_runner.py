"""Android lifecycle adapter; the original proxy package is unchanged."""
import asyncio
import logging
import threading
from proxy.config import proxy_config, coerce_domain_list
from proxy import tg_ws_proxy

_lock = threading.RLock()
_loop = None
_stop = None
_stop_requested = False


def reset():
    global _stop_requested
    with _lock:
        _stop_requested = False


def start(secret, port, pool_size, cf_enabled, h2_enabled, cf_domains, dc2, dc4,
          worker_enabled=False, worker_domain=""):
    """Blocking entry point: invoke only on a background Java thread."""
    if len(secret) != 32 or any(c not in '0123456789abcdefABCDEF' for c in secret):
        raise ValueError('Secret must contain exactly 32 hexadecimal characters')
    if not (1024 <= int(port) <= 65535):
        raise ValueError('Port must be between 1024 and 65535')
    if not (0 <= int(pool_size) <= 32):
        raise ValueError('Pool size must be between 0 and 32')

    proxy_config.host = '127.0.0.1'
    proxy_config.port = int(port)
    proxy_config.secret = secret.lower()
    proxy_config.pool_size = int(pool_size)
    proxy_config.fallback_cfproxy = bool(cf_enabled)
    proxy_config.cfproxy_h2_media = bool(h2_enabled)
    proxy_config.cfproxy_user_domains = coerce_domain_list(cf_domains)
    proxy_config.dc_redirects = {2: str(dc2), 4: str(dc4)}
    proxy_config.disable_secure = False
    proxy_config.fake_tls_domain = ''
    proxy_config.proxy_protocol = False
    proxy_config.force_test_dc = False
    proxy_config.buffer_size = 256 * 1024
    # Worker fallback operates separately from the ordinary CF proxy and H2.
    # Preserve the original 8-argument call for existing adapter tests.
    worker_domains = coerce_domain_list(worker_domain) if worker_enabled else []
    if worker_enabled and len(worker_domains) != 1:
        raise ValueError('Exactly one Cloudflare Worker domain is required')
    proxy_config.cfproxy_worker_domains = worker_domains
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s %(levelname)s %(name)s: %(message)s')

    async def _main():
        global _loop, _stop
        loop = asyncio.get_running_loop()
        event = asyncio.Event()
        with _lock:
            _loop = loop
            _stop = event
            if _stop_requested:
                event.set()
        try:
            await tg_ws_proxy._run(event)
        finally:
            with _lock:
                _loop = None
                _stop = None

    asyncio.run(_main())


def stop():
    global _stop_requested
    with _lock:
        _stop_requested = True
        if _loop is not None and _stop is not None:
            _loop.call_soon_threadsafe(_stop.set)


def is_listening():
    server = getattr(tg_ws_proxy, '_server_instance', None)
    return bool(server and server.sockets and
                any(s.fileno() >= 0 for s in server.sockets))


def probe_telegram(secret, port, dc=2, timeout=11):
    """Verify a genuine MTProto resPQ response through the *local* proxy.

    An open local TCP port, HTTP /health, or WS 101 alone is not evidence of
    Telegram connectivity.  We use an unauthenticated req_pq_multi exchange:
    Telegram must return resPQ with the nonce we generated. No account or
    personal Telegram data is used. Runs on its own Java background thread.
    """
    import hashlib
    import os
    import socket
    import struct
    import time
    from proxy._aes import Cipher, algorithms, modes
    from proxy.utils import RESERVED_FIRST_BYTES, RESERVED_STARTS, RESERVED_CONTINUE

    secret_bytes = bytes.fromhex(secret)
    dc = int(dc)
    if dc not in (2, 4):
        raise ValueError('Probe DC must be 2 or 4')
    timeout = min(max(int(timeout), 2), 15)

    # MTProto obfuscated2 client initializer with proxy secret added to AES key.
    while True:
        initializer = bytearray(os.urandom(64))
        if (initializer[0] not in RESERVED_FIRST_BYTES and
                bytes(initializer[:4]) not in RESERVED_STARTS and
                initializer[4:8] != RESERVED_CONTINUE):
            break
    forward_key = hashlib.sha256(bytes(initializer[8:40]) + secret_bytes).digest()
    forward_iv = bytes(initializer[40:56])
    outbound = Cipher(algorithms.AES(forward_key), modes.CTR(forward_iv)).encryptor()
    encrypted_init = outbound.update(bytes(initializer))
    header_tail = b'\xee\xee\xee\xee' + struct.pack('<h', dc) + os.urandom(2)
    for index, val in enumerate(header_tail, 56):
        initializer[index] = val ^ encrypted_init[index] ^ initializer[index]

    reverse = bytes(initializer[8:56])[::-1]
    reverse_key = hashlib.sha256(reverse[:32] + secret_bytes).digest()
    inbound = Cipher(algorithms.AES(reverse_key), modes.CTR(reverse[32:])).encryptor()

    nonce = os.urandom(16)
    body = struct.pack('<I', 0xbe7e8ef1) + nonce  # req_pq_multi
    msg_id = int(time.time() * (1 << 32)) & ~3
    payload = (b'\x00' * 8 + struct.pack('<Q', msg_id) +
               struct.pack('<I', len(body)) + body)
    # MTProto intermediate transport uses a little-endian message length.
    frame = struct.pack('<I', len(payload)) + payload

    try:
        with socket.create_connection(('127.0.0.1', int(port)), timeout=timeout) as sock:
            sock.settimeout(timeout)
            sock.sendall(initializer)
            sock.sendall(outbound.update(frame))

            def read_plain_exact(n):
                result = bytearray()
                while len(result) < n:
                    chunk = sock.recv(n - len(result))
                    if not chunk:
                        raise ConnectionError('Telegram proxy closed before reply')
                    result += inbound.update(chunk)
                return bytes(result)

            length = struct.unpack('<I', read_plain_exact(4))[0]
            if not (40 <= length <= 16384):
                return False
            answer = read_plain_exact(length)
            # auth_key_id(8), msg_id(8), msg_data_len(4), resPQ(4), nonce(16)
            if answer[:8] != b'\x00' * 8 or len(answer) < 40:
                return False
            body_len = struct.unpack_from('<I', answer, 16)[0]
            if not (20 <= body_len <= length - 20):
                return False
            response_body = answer[20:20 + body_len]
            return (struct.unpack_from('<I', response_body)[0] == 0x05162463 and
                    response_body[4:20] == nonce)
    except (OSError, TimeoutError, ValueError, ConnectionError) as exc:
        logging.getLogger(__name__).info('Telegram DC%d probe did not receive resPQ: %s',
                                          dc, type(exc).__name__)
        return False
