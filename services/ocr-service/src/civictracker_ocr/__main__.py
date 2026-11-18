"""Run one configured OCR instance."""

import argparse

import uvicorn

from .app import create_app

parser = argparse.ArgumentParser()
parser.add_argument("--host", default="127.0.0.1")
parser.add_argument("--port", type=int, default=8085)
args = parser.parse_args()
uvicorn.run(create_app(), host=args.host, port=args.port, workers=1, access_log=False)
