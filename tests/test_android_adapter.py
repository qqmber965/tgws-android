"""Local smoke test for the original Python engine via the Android adapter.

Run with `python tests/test_android_adapter.py` on Python with httpx and cryptography.
Only tests localhost listener and lifecycle, not Telegram network connectivity.
"""
import socket
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
import mobile_runner


def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


def test_start_stop():
    port = free_port()
    errors = []
    mobile_runner.reset()
    def run():
        try:
            mobile_runner.start('00112233445566778899aabbccddeeff', port, 0,
                                False, False, '', '149.154.167.220',
                                '149.154.167.220')
        except Exception as exc:
            errors.append(repr(exc))
    worker = threading.Thread(target=run, name='smoke-proxy', daemon=True)
    worker.start()
    try:
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline and not mobile_runner.is_listening() and worker.is_alive():
            time.sleep(0.1)
        assert mobile_runner.is_listening(), 'local listener did not start: ' + repr(errors)
        with socket.create_connection(('127.0.0.1', port), timeout=3):
            pass
    finally:
        mobile_runner.stop()
        worker.join(8)
    assert not worker.is_alive(), 'proxy did not shut down'
    assert not errors, 'proxy raised exception: ' + repr(errors)
    assert not mobile_runner.is_listening(), 'listener left open'


if __name__ == '__main__':
    test_start_stop()
    print('PASS: localhost proxy starts, accepts connection and shuts down')
