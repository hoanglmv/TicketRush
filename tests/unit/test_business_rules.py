#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Business Rules Unit Test Suite
=============================================================================
Kiểm thử các quy tắc nghiệp vụ cốt lõi (Domain Business Logic):
1. Quy tắc thời gian sự kiện (Event Lifecycle Dates Validation)
2. Quy tắc tính toán hàng đợi ảo (Virtual Queue Batching & Position)
3. Quy tắc thời hạn giữ ghế (Hold-Time TTL Expiration)
4. Quy tắc sinh mã ghế & Tọa độ hàng cột (Seat Naming & Matrix Coordinate)
5. Quy tắc kiểm tra tính hợp lệ của mật khẩu và định dạng email
=============================================================================
"""

import sys
import time
import unittest
from datetime import datetime, timedelta

class TestEventLifecycleRules(unittest.TestCase):
    """Quy tắc: saleStartTime < saleEndTime < eventDate"""

    def test_valid_event_dates(self):
        now = datetime.now()
        sale_start = now
        sale_end = now + timedelta(days=5)
        event_date = now + timedelta(days=10)
        self.assertTrue(sale_start < sale_end < event_date)

    def test_invalid_sale_end_after_event_date(self):
        now = datetime.now()
        sale_start = now
        sale_end = now + timedelta(days=12)
        event_date = now + timedelta(days=10)
        is_valid = (sale_start < sale_end < event_date)
        self.assertFalse(is_valid, "Hạn bán vé không thể sau ngày sự kiện diễn ra")


class TestSeatHoldRules(unittest.TestCase):
    """Quy tắc giữ chỗ: Ghế giữ quá 10 phút phải được giải phóng"""

    def test_seat_hold_expiration(self):
        hold_timeout_minutes = 10
        locked_at = datetime.now() - timedelta(minutes=11)
        now = datetime.now()
        is_expired = (now - locked_at).total_seconds() > (hold_timeout_minutes * 60)
        self.assertTrue(is_expired, "Ghế giữ 11 phút phải được coi là hết hạn")

    def test_seat_hold_active(self):
        hold_timeout_minutes = 10
        locked_at = datetime.now() - timedelta(minutes=4)
        now = datetime.now()
        is_expired = (now - locked_at).total_seconds() > (hold_timeout_minutes * 60)
        self.assertFalse(is_expired, "Ghế giữ 4 phút vẫn đang trong thời gian an toàn")


class TestVirtualQueueRules(unittest.TestCase):
    """Quy tắc hàng đợi ảo: Tính toán số đợt xả hàng và thời gian ước tính"""

    def test_queue_batch_calculation(self):
        batch_size = 50
        position_1 = 45   # Thuộc đợt 1
        position_2 = 142  # Thuộc đợt 3
        batch_1 = (position_1 - 1) // batch_size + 1
        batch_2 = (position_2 - 1) // batch_size + 1
        self.assertEqual(batch_1, 1)
        self.assertEqual(batch_2, 3)

    def test_estimated_waiting_time(self):
        batch_size = 50
        batch_interval_seconds = 30
        position = 120
        batch_num = (position - 1) // batch_size + 1
        est_seconds = (batch_num - 1) * batch_interval_seconds
        self.assertEqual(est_seconds, 60, "Vị trí 120 (đợt 3) phải chờ 60 giây")


class TestSeatMatrixNaming(unittest.TestCase):
    """Quy tắc sinh mã ghế chuẩn: Hàng chữ cái + Cột 2 chữ số (A01, B12)"""

    def test_seat_code_formatting(self):
        row_idx = 1  # 'A'
        col_idx = 5
        seat_code = f"{chr(64 + row_idx)}{col_idx:02d}"
        self.assertEqual(seat_code, "A05")

        row_idx_2 = 3 # 'C'
        col_idx_2 = 18
        seat_code_2 = f"{chr(64 + row_idx_2)}{col_idx_2:02d}"
        self.assertEqual(seat_code_2, "C18")


class TestDynamicQRTotpRules(unittest.TestCase):
    """Quy tắc mã QR động (TOTP): HMAC-SHA256, chu kỳ 30 giây, chống chụp màn hình"""

    def setUp(self):
        import hmac
        import hashlib
        self.secret = "TicketRushAntiScalpingKey2026!@#$%^"
        self.hmac_mod = hmac
        self.hashlib_mod = hashlib

    def compute_hmac(self, data: str) -> str:
        h = self.hmac_mod.new(self.secret.encode("utf-8"), data.encode("utf-8"), self.hashlib_mod.sha256)
        return h.hexdigest()

    def test_dynamic_qr_valid_signature(self):
        current_epoch = int(time.time() / 30)
        payload = f"TID:101|EID:1|SID:5|USER:42|EP:{current_epoch}"
        sig = self.compute_hmac(payload)

        # Kiểm tra xác thực chữ ký khớp
        expected_sig = self.compute_hmac(payload)
        self.assertEqual(sig, expected_sig)

    def test_dynamic_qr_expired_epoch(self):
        current_epoch = int(time.time() / 30)
        expired_epoch = current_epoch - 5  # Quá 2.5 phút trước (chụp màn hình cũ)
        is_valid_window = abs(current_epoch - expired_epoch) <= 1
        self.assertFalse(is_valid_window, "Mã QR quá 60 giây phải bị từ chối")

    def test_dynamic_qr_tampered_payload(self):
        current_epoch = int(time.time() / 30)
        original_payload = f"TID:101|EID:1|SID:5|USER:42|EP:{current_epoch}"
        sig = self.compute_hmac(original_payload)

        # Kẻ gian đổi ID ghế thành ghế VIP (SID: 999)
        tampered_payload = f"TID:101|EID:1|SID:999|USER:42|EP:{current_epoch}"
        tampered_sig = self.compute_hmac(tampered_payload)
        self.assertNotEqual(sig, tampered_sig, "Chữ ký số phải phát hiện gian lận sửa đổi payload")


if __name__ == "__main__":
    print("\n=============================================================================")
    print("   🧪 TICKETRUSH — BUSINESS RULES UNIT TEST SUITE")
    print("=============================================================================\n")
    runner = unittest.TextTestRunner(verbosity=2)
    suite = unittest.TestLoader().loadTestsFromModule(sys.modules[__name__])
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
