#!/usr/bin/env python3
"""Loopback-only Alertmanager webhook used by the local alerting drill."""

from __future__ import annotations

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class State:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.events: list[dict[str, object]] = []
        self.metrics = ""


STATE = State()


class Handler(BaseHTTPRequestHandler):
    server_version = "LeetModelAlertDrill/1.0"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/healthz":
            self._send(200, b'{"status":"UP"}\n', "application/json")
            return
        if self.path == "/events":
            with STATE.lock:
                body = json.dumps(STATE.events, ensure_ascii=False).encode("utf-8")
            self._send(200, body + b"\n", "application/json")
            return
        if self.path == "/actuator/prometheus":
            with STATE.lock:
                body = STATE.metrics.encode("utf-8")
            self._send(200, body, "text/plain; version=0.0.4; charset=utf-8")
            return
        self._send(404, b'{"error":"not_found"}\n', "application/json")

    def do_POST(self) -> None:  # noqa: N802
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.path == "/alerts":
            try:
                event = json.loads(body)
            except json.JSONDecodeError:
                self._send(400, b'{"error":"invalid_json"}\n', "application/json")
                return
            with STATE.lock:
                STATE.events.append(event)
            self._send(200, b'{"accepted":true}\n', "application/json")
            return
        if self.path == "/test/metrics":
            with STATE.lock:
                STATE.metrics = body.decode("utf-8")
            self._send(200, b'{"accepted":true}\n', "application/json")
            return
        self._send(404, b'{"error":"not_found"}\n', "application/json")

    def do_DELETE(self) -> None:  # noqa: N802
        if self.path == "/events":
            with STATE.lock:
                STATE.events.clear()
            self._send(200, b'{"cleared":true}\n', "application/json")
            return
        self._send(404, b'{"error":"not_found"}\n', "application/json")

    def log_message(self, format_string: str, *args: object) -> None:
        return

    def _send(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=19094)
    args = parser.parse_args()
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
