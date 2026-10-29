#!/usr/bin/env python3
"""Local stand-in for the Skim AI proxy and one article page.

releaseSmoke calls http://127.0.0.1:8787. This process binds that port on the
host. `adb reverse tcp:8787 tcp:8787` makes the emulator reach it.
"""

from __future__ import annotations

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ARTICLE = """<!doctype html>
<html><head><title>Why heat pumps are winning in cold climates</title></head>
<body><article>
<h1>Why heat pumps are winning in cold climates</h1>
<p>For decades the advice was simple: if your winters drop well below freezing, a heat pump is not for you. That advice is now out of date, and the numbers coming out of Norway, Finland and the northern United States show how quickly it has aged.</p>
<p>Cold-climate models keep delivering useful heat at minus 25 C. A home switching from oil saves 300 to 900 dollars a year, and the same machines qualify for the 2026 rebate in most of the states that still heat with oil.</p>
<p>Installers in the sample compared nameplate capacity with the heat loss of the house, then checked whether the outdoor unit still met that load at the design temperature. The ones that did are the ones the article recommends.</p>
<p>The catch is the electrical service. Several of the homes needed a panel upgrade before the heat pump could be installed, and that cost is easy to leave out of a payback that only counts the equipment.</p>
</article></body></html>
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--sse", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    sse = args.sse.read_text(encoding="utf-8")
    log_path = args.log
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("", encoding="utf-8")

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt: str, *log_args: object) -> None:
            return

        def _note(self, line: str) -> None:
            with log_path.open("a", encoding="utf-8") as handle:
                handle.write(line + "\n")

        def do_GET(self) -> None:  # noqa: N802
            body = ARTICLE.encode("utf-8")
            self._note(f"GET {self.path}")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self) -> None:  # noqa: N802
            length = int(self.headers.get("Content-Length", "0"))
            self.rfile.read(length)
            api_key = self.headers.get("x-api-key")
            install = self.headers.get("X-Skim-Install")
            self._note(
                f"POST {self.path} x-api-key={api_key!r} X-Skim-Install-present={bool(install)}"
            )
            if self.path.startswith("/v1/messages"):
                payload = sse.encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
                return
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"fake backend listening on 127.0.0.1:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
