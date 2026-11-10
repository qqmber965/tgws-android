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


def start(secret, port, pool_size, cf_enabled, h2_enabled, cf_domains, dc2, dc4):
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
    proxy_config.cfproxy_worker_domains = []
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
