import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException, Request
from main import parse_range_header, require_admin


def request_with_headers(headers=None):
    encoded = [(key.lower().encode(), value.encode()) for key, value in (headers or {}).items()]
    return Request({"type": "http", "method": "POST", "path": "/api/scan", "headers": encoded})


class ByteRangeTests(unittest.TestCase):
    def test_full_file_range_when_header_is_absent(self):
        self.assertEqual(parse_range_header(None, 100), (0, 99))

    def test_explicit_range_and_open_ended_range(self):
        self.assertEqual(parse_range_header("bytes=10-19", 100), (10, 19))
        self.assertEqual(parse_range_header("bytes=90-", 100), (90, 99))

    def test_suffix_range_and_end_clamped_to_file(self):
        self.assertEqual(parse_range_header("bytes=-10", 100), (90, 99))
        self.assertEqual(parse_range_header("bytes=90-120", 100), (90, 99))

    def test_invalid_and_unsatisfiable_ranges_return_416(self):
        for header in ("items=0-2", "bytes=100-", "bytes=5-4", "bytes=0-1,4-5", "bytes=-0"):
            with self.subTest(header=header):
                with self.assertRaises(HTTPException) as caught:
                    parse_range_header(header, 100)
                self.assertEqual(caught.exception.status_code, 416)


class AdminAuthTests(unittest.TestCase):
    def test_scan_auth_requires_configured_key(self):
        with patch.dict(os.environ, {"ADMIN_API_KEY": "test-secret"}):
            with self.assertRaises(HTTPException) as caught:
                require_admin(request_with_headers())
            self.assertEqual(caught.exception.status_code, 401)

    def test_scan_auth_accepts_matching_key(self):
        with patch.dict(os.environ, {"ADMIN_API_KEY": "test-secret"}):
            require_admin(request_with_headers({"X-Admin-Key": "test-secret"}))

    def test_scan_auth_rejects_wrong_key(self):
        with patch.dict(os.environ, {"ADMIN_API_KEY": "test-secret"}):
            with self.assertRaises(HTTPException) as caught:
                require_admin(request_with_headers({"X-Admin-Key": "wrong"}))
            self.assertEqual(caught.exception.status_code, 401)


if __name__ == "__main__":
    unittest.main()
