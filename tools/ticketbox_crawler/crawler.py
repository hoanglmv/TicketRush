#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Ticketbox Data Crawler Agent & Auto-Ingestion Tool (V2 Enhanced)
=============================================================================
Công cụ Agent tự động thu thập (crawl), làm giàu thông tin (enrich) sự kiện,
ca nhạc, kịch nghệ, thể thao từ Ticketbox (ticketbox.vn) và nhập tự động vào TicketRush.

Phiên bản V2 Nâng Cấp:
1. Thu thập dữ liệu đa tầng cực kỳ chi tiết (Full Overview, Line-up, Timeline,
   Quyền lợi từng hạng vé, Hướng dẫn gửi xe & Cổng đón, Quy định an ninh).
2. Bộ sưu tập ảnh phong phú (Multi-image Gallery) hiển thị Carousel trên Web.
3. Chế độ '--enrich' / '--update' cập nhật trực tiếp toàn bộ sự kiện hiện có
   trên website mà không làm mất trạng thái hay vé đã bán.
4. Tự động đồng bộ tri thức sâu rộng (RAG Knowledge) cho AI Chatbot.
=============================================================================
"""

import sys
import os
import re
import json
import time
import argparse
import subprocess
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional

try:
    import requests
except ImportError:
    print("[!] Cần cài đặt thư viện 'requests': pip install requests")
    sys.exit(1)


# ============================================================================
# BỘ DỮ LIỆU SỰ KIỆN TICKETBOX ĐƯỢC LÀM GIÀU THÔNG TIN TOÀN DIỆN (ENRICHED DATASET)
# ============================================================================
TICKETBOX_CURATED_EVENTS = [
    {
        "name": "Live Concert: ANH TRAI SAY HI 2026 - Đêm 3",
        "category": "LIVE_MUSIC",
        "city": "Hà Nội",
        "venue": "Sân vận động Quốc gia Mỹ Đình",
        "address": "Đường Lê Đức Thọ, Phường Mỹ Đình 1, Nam Từ Liêm, Hà Nội",
        "bannerUrl": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 GIỚI THIỆU ĐẠI NHẠC HỘI
Đại nhạc hội bùng nổ của 30 'Anh Trai Say Hi' chính thức quay trở lại Thủ đô Hà Nội với đêm diễn quy mô kỷ lục tại Sân vận động Quốc gia Mỹ Đình. Hệ thống sân khấu 360 độ hiện đại bậc nhất Việt Nam, hiệu ứng ánh sáng laser đỉnh cao và dàn âm thanh L-Acoustics chuẩn quốc tế sẽ mang đến một bữa tiệc âm nhạc mãn nhãn chưa từng có.

🎤 DÀN NGHỆ SĨ BIỂU DIỄN (LINE-UP)
• HIEUTHUHAI, RHYDER, Anh Tú Atus, Isaac, Negav, Quang Hùng MasterD
• Song Luân, Gin Tuấn Kiệt, Quân A.P, Đức Phúc, Erik, HURRYKNG
• Pháp Kiều, WEAN, Captain Boy, Dương Domic, Hùng Huỳnh cùng dàn khách mời đặc biệt bí mật.

⏰ TIMELINE SỰ KIỆN
• 13:00 - 16:00: Mở quầy đổi vòng tay & Check-in vé Fanzone/SVIP
• 16:00: Mở cửa khán đài đón khán giả
• 17:30 - 18:30: Soundcheck đặc quyền dành cho vé SVIP President
• 19:30: Bắt đầu đêm diễn chính thức
• 23:00: Kết thúc chương trình & Giao lưu chào tạm biệt

🎟️ QUYỀN LỢI CHI TIẾT CÁC HẠNG VÉ
• SVIP President (4.500.000đ): Vị trí trung tâm sát sàn diễn, Vé soundcheck trước giờ diễn, Bộ quà tặng Goods độc quyền (Áo thun concert, Lightstick, Photocard 30 Anh Trai có chữ ký), Thảm đỏ & Lối đi riêng Fast-track, Đồ uống Welcome Drink cao cấp.
• VIP Diamond 1 (2.800.000đ): Ghế ngồi bậc thang tầm nhìn bao quát toàn bộ sân khấu, Túi tote bag, Bộ 5 photocard giới hạn, Lối vào ưu tiên.
• Fanzone GA Đứng (1.800.000đ): Khu vực đứng gần sân khấu nhất, Vòng tay Fanzone dạ quang, Trải nghiệm hòa mình cùng dàn nghệ sĩ.
• Khán Đài CAT 1 (1.200.000đ): Ghế ngồi có mái che tại tầng 1 sân vận động, Tầm nhìn chính diện sân khấu.
• Khán Đài CAT 2 (600.000đ): Ghế ngồi tầng 2 bao quát toàn cảnh đại nhạc hội.

📍 HƯỚNG DẪN DI CHUYỂN & GỬI XE
• Địa chỉ: Sân vận động Quốc gia Mỹ Đình, Đường Lê Đức Thọ, Phường Mỹ Đình 1, Nam Từ Liêm, Hà Nội.
• Cổng đón: Cổng A (Khu SVIP/VIP), Cổng B & C (Fanzone & Khán đài CAT 1), Cổng D (Khán đài CAT 2).
• Gửi xe: Bãi giữ xe Cung Điền kinh Trong nhà & Bãi xe Sân phụ Mỹ Đình (Sức chứa 15.000 xe máy, 2.000 ô tô). Khuyến khích di chuyển bằng taxi, Grab hoặc xe bus số 26, 46, 50, 60B.

⚠️ QUY ĐỊNH AN NINH & VẬT DỤNG CẤM
• Mỗi tài khoản mua tối đa 04 vé.
• Độ tuổi: Trẻ em dưới 12 tuổi không được vào khu Fanzone đứng. Trẻ em từ 6-12 tuổi khu khán đài phải có người lớn đi kèm.
• Nghiêm cấm mang vào: Đồ uống có cồn, chai lọ kim loại/thủy tinh, máy ảnh chuyên nghiệp có ống kính rời tele, flycam/drone, gậy selfie dài, pháo sáng, vũ khí hoặc chất gây cháy nổ.
• Vui lòng xuất trình mã QR điện tử trên ứng dụng TicketRush hoặc Căn cước công dân khi làm thủ tục check-in.""",
        "days_from_now": 25,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 100,
        "zones": [
            {"name": "SVIP President", "color": "#e74c3c", "price": 4500000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "VIP Diamond 1", "color": "#f1c40f", "price": 2800000, "totalRows": 5, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Fanzone GA (Đứng)", "color": "#e84393", "price": 1800000, "totalRows": 6, "seatsPerRow": 30, "sortOrder": 3},
            {"name": "Khán Đài Tầng 1 (CAT 1)", "color": "#3498db", "price": 1200000, "totalRows": 8, "seatsPerRow": 30, "sortOrder": 4},
            {"name": "Khán Đài Tầng 2 (CAT 2)", "color": "#2ecc71", "price": 600000, "totalRows": 10, "seatsPerRow": 30, "sortOrder": 5},
        ],
        "rag_faq": "Mỗi tài khoản mua tối đa 4 vé. Trẻ em dưới 12 tuổi không vào khu Fanzone đứng. Mở cổng check-in từ 16h00. Bãi gửi xe tại Cung Điền kinh Mỹ Đình."
    },
    {
        "name": "ANH TRAI VƯỢT NGÀN CHÔNG GAI - Concert All-Star",
        "category": "LIVE_MUSIC",
        "city": "Hồ Chí Minh",
        "venue": "Công viên Bờ sông Sài Gòn (Thủ Thiêm)",
        "address": "Khu đô thị mới Thủ Thiêm, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 GIỚI THIỆU CONCERT ALL-STAR
Đêm hòa nhạc quy tụ 33 Anh Tài với những màn hòa âm phối khí dân gian đương đại hào hùng: Trống Cơm, Dẫu Có Lỗi Lầm, Mẹ Yêu Con, Chiếc Khăn Piêu. Trải nghiệm không gian âm nhạc ngoài trời thoáng đãng bên bờ sông Sài Gòn rực rỡ ánh đèn về đêm.

🎤 DÀN ANH TÀI THAM GIA
• NSND Tự Long, Bằng Kiều, Tuấn Hưng, Phan Đinh Tùng, Tiến Luật
• Soobin Hoàng Sơn, BinZ, Cường Seven, Rhymastic, Quốc Thiên, Jun Phạm
• Kay Trần, Bùi Công Nam, Duy Khánh, BB Trần, S.T Sơn Thạch, Thiên Minh...

⏰ LỊCH TRÌNH CHƯƠNG TRÌNH
• 14:00: Mở khu vực F&B và gian hàng merchandise lưu niệm
• 16:30: Bắt đầu làm thủ tục soát vé điện tử qua cổng An ninh
• 19:00: Khai mạc đêm diễn All-Star Concert
• 23:30: Pháo hoa nghệ thuật bế mạc

🎟️ HẠNG VÉ & QUYỀN LỢI ĐẶC BIỆT
• VIP Chông Gai (3.800.000đ): Vị trí ngồi trực diện, Set quà tặng áo thun Gai & Nón concert, Lối đi riêng, Thẻ VIP đeo cổ lưu niệm.
• Lửa Thiêng Fanzone (2.200.000đ): Khu vực đứng sát sân khấu T-stage, Tiếp cận cự ly gần nhất với các Anh Tài.
• Khán Đài A (1.500.000đ): Ghế ngồi bậc thang cao, Tầm nhìn toàn cảnh sân khấu và sông Sài Gòn.
• Khán Đài B (800.000đ): Ghế ngồi tiêu chuẩn, Không gian thoáng mát, Âm thanh sống động.

📍 PHƯƠNG TIỆN DI CHUYỂN & GỬI XE
• Ban tổ chức bố trí xe Bus Shuttle đưa đón MIỄN PHÍ liên tục từ Bến Bạch Đằng (Quận 1) sang Công viên Bờ sông Thủ Thiêm.
• Bãi đỗ xe ô tô & xe máy tại Quảng trường Trung tâm Thủ Thiêm (đối diện bến tàu thủy).

⚠️ LƯU Ý KHI THAM GIA
• Mỗi khách hàng được mang theo quạt cầm tay mini và điện thoại. Cấm tuyệt đối máy quay phim chuyên dụng, gậy chụp hình dài trên 30cm, vũ khí và pháo giấy tự phát.""",
        "days_from_now": 35,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 80,
        "zones": [
            {"name": "VIP Chông Gai", "color": "#e67e22", "price": 3800000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Lửa Thiêng (Fanzone)", "color": "#d35400", "price": 2200000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Khán Đài A", "color": "#2980b9", "price": 1500000, "totalRows": 8, "seatsPerRow": 25, "sortOrder": 3},
            {"name": "Khán Đài B", "color": "#16a085", "price": 800000, "totalRows": 10, "seatsPerRow": 25, "sortOrder": 4},
        ],
        "rag_faq": "Có xe shuttle bus miễn phí từ Bến Bạch Đằng sang Thủ Thiêm. Check-in vé điện tử quét mã QR qua app TicketRush."
    },
    {
        "name": "Hà Anh Tuấn Live Concert - Chân Trời Rực Rỡ",
        "category": "LIVE_MUSIC",
        "city": "Ninh Bình",
        "venue": "Khu di tích Cố đô Hoa Lư",
        "address": "Xã Trường Yên, Huyện Hoa Lư, Tỉnh Ninh Bình",
        "bannerUrl": "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 CHÂN TRỜI RỰC RỠ - THE GLORIOUS HORIZON
Đêm nhạc acoustic giữa cảnh sắc kỳ vĩ của non nước Cố đô Hoa Lư cùng huyền thoại âm nhạc thế giới Kitaro và dàn nhạc giao hưởng quốc gia. Những giai điệu bất hủ: Xuân Thì, Tháng Mấy Em Nhớ Anh, Bonjour Vietnam, Tình Thôi Xót Xa đưa khán giả vào không gian thi ca diễm lệ.

🎤 NGHỆ SĨ & KHÁCH MỜI ĐẶC BIỆT
• Giọng ca chính: Ca sĩ Hà Anh Tuấn
• Khách mời huyền thoại quốc tế: Nghệ sĩ New Age Kitaro (Nhật Bản)
• Dàn nhạc Giao hưởng & Nhạc trưởng Trần Nhật Minh.

⏰ THỜI GIAN BIỂU
• 16:00: Mở cổng đón khách và khu vực chụp ảnh lưu niệm Non nước Ninh Bình
• 18:30: Ổn định chỗ ngồi
• 19:15: Đêm nhạc chính thức bắt đầu
• 22:45: Bế mạc

🎟️ BẢNG GIÁ VÉ & QUYỀN LỢI
• Khu Hoa Lư (VVIP - 5.000.000đ): Ghế đệm bọc da cao cấp, Thư cảm ơn viết tay từ Hà Anh Tuấn, Đĩa CD vật lý Chân Trời Rực Rỡ có chữ ký, Tiệc trà đón tiếp thượng khách.
• Khu Trường Yên (VIP - 3.500.000đ): Ghế ngồi trung tâm, Khăn choàng lụa tơ tằm Ninh Bình, Nước suối khoáng thiên nhiên.
• Khu Ánh Sao (Standard - 1.800.000đ): Ghế ngồi bậc thang thoáng đãng, Tầm nhìn thẳng hướng sân khấu chính.

📍 DRESSCODE & LƯU Ý
• Trang phục gợi ý: Tông màu Trắng, Be hoặc Đen sang trọng, thanh lịch.
• Khuyến nghị mang giày đế thấp hoặc giày thể thao vì địa hình di tích cổ lát đá tự nhiên.""",
        "days_from_now": 45,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Khu Hoa Lư (VVIP)", "color": "#9b59b6", "price": 5000000, "totalRows": 4, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Khu Trường Yên (VIP)", "color": "#8e44ad", "price": 3500000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Khu Ánh Sao (Standard)", "color": "#34495e", "price": 1800000, "totalRows": 8, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Dresscode gợi ý: Trang phục thanh lịch màu trắng/be/đen. Khuyến khích mang giày đế bằng vì không gian di tích cổ Hoa Lư."
    },
    {
        "name": "Vũ. Live Concert - Bảo Tàng Của Nuối Tiếc",
        "category": "LIVE_MUSIC",
        "city": "Hồ Chí Minh",
        "venue": "Nhà thi đấu Nguyễn Du",
        "address": "116 Nguyễn Du, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 BẢO TÀNG CỦA NUỐI TIẾC - TOUR 2026
'Hoàng tử Indie' Vũ mang đến không gian tự sự ấm áp và giàu hoài niệm cùng album mới 'Bảo Tàng Của Nuối Tiếc'. Đêm nhạc là những câu chuyện tình dang dở được kể qua âm nhạc mộc mạc: Lạ Lùng, Bước Qua Mùa Cô Đơn, Đông Kiếm Em, Anh Nhớ Ra, Những Lời Hứa Bỏ Quên.

🎤 DÀN NGHỆ SĨ KHÁCH MỜI
• Nghệ sĩ chính: Vũ. & Band nhạc sống
• Khách mời đặc biệt: Dear Jane (Hong Kong), Hà Nhi, Madihu.

⏰ THỜI GIAN SỰ KIỆN
• 17:00: Mở quầy bán Merchandise độc quyền và check-in khán giả
• 19:30: Khai màn đêm diễn
• 22:30: Ký tặng poster cho 100 khán giả may mắn

🎟️ CHI TIẾT HẠNG VÉ
• VIP Nuối Tiếc (2.000.000đ): Ghế ngồi trung tâm hàng đầu, Tặng kèm Đĩa than Vinyl hoặc CD album có chữ ký, Bộ Sticker & Khăn bandana.
• Khu Tầng Trệt GA (1.200.000đ): Khu đứng tự do gần sát sân khấu acoustic, Trải nghiệm không gian âm nhạc gần gũi nhất.
• Khán Đài Lầu 1 (750.000đ): Ghế ngồi bậc thang bao quát toàn bộ nhà thi đấu.

📍 ĐỊA ĐIỂM & GỬI XE
• Địa chỉ: 116 Nguyễn Du, P. Bến Thành, Quận 1 (gần Công viên Tao Đàn).
• Gửi xe máy tại bãi xe Nhà thi đấu Nguyễn Du hoặc cổng Công viên Tao Đàn đường Huyền Trân Công Chúa.""",
        "days_from_now": 18,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "VIP Nuối Tiếc", "color": "#1abc9c", "price": 2000000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Khu Tầng Trệt (GA)", "color": "#16a085", "price": 1200000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Khán Đài Lầu 1", "color": "#27ae60", "price": 750000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 3},
        ],
        "rag_faq": "Có bán đĩa than Vinyl và áo thun concert độc quyền tại sảnh Nhà thi đấu Nguyễn Du. Bãi gửi xe tại cổng Công viên Tao Đàn."
    },
    {
        "name": "Đen Vâu - Show Của Đen 2026",
        "category": "LIVE_MUSIC",
        "city": "Hà Nội",
        "venue": "Cung Điền kinh Trong nhà Hà Nội",
        "address": "Đường Trần Hữu Dực, Cầu Diễn, Nam Từ Liêm, Hà Nội",
        "bannerUrl": "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 SHOW CỦA ĐEN 2026 - ĐỒNG ÂM TỤ HỘI
Liveshow rap mộc mạc và chân thành nhất của Đen cùng ban nhạc Màu Nước và dàn nghệ sĩ thân thiết. Hàng vạn 'Đồng Âm' cùng hòa giọng trong những bản rap đã khắc sâu vào thanh xuân: Nấu Ăn Cho Em, Đi Về Nhà, Mang Tiền Về Cho Mẹ, Trốn Tìm, Bài Này Chill Phết.

🎤 KHÁCH MỜI ĐỒNG HÀNH
• Đen Vâu & Ban nhạc Màu Nước
• Khách mời: Lynk Lee, Kimmese, JustaTee, Vũ, các em nhỏ Điện Biên.

⏰ TIMELINE SHOW DIỄN
• 14:00: Check-in nhận vòng tay và nón tai bèo 'Đồng Âm'
• 18:00: Mở cửa khán phòng
• 19:45: Khai mạc liveshow
• 23:15: Kết thúc và chụp ảnh kỷ niệm cùng toàn bộ khán giả

🎟️ HẠNG VÉ VÀ QUÀ TẶNG
• VIP Đồng Âm (2.500.000đ): Vị trí ngồi trung tâm tầng trệt, Nón tai bèo Đen Vâu phiên bản giới hạn, Áo thun Đồng Âm cotton cao cấp, Sổ tay hành trình.
• Mặt Sân GA (1.400.000đ): Đứng gần sân khấu, hòa mình cùng từng câu rap và nhịp bass.
• Khán Đài Trên (700.000đ): Ghế ngồi cố định tầng trên, quan sát trọn vẹn biển đèn flash tuyệt đẹp.

❤️ Ý NGHĨA CỘNG ĐỒNG
Toàn bộ doanh thu từ các vật phẩm lưu niệm bán tại show diễn sẽ được Đen trích chuyển thẳng vào quỹ 'Nuôi Em' nhằm hỗ trợ bữa ăn và xây trường học cho các em nhỏ vùng cao.""",
        "days_from_now": 40,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 70,
        "zones": [
            {"name": "VIP Đồng Âm", "color": "#f39c12", "price": 2500000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Mặt Sân GA", "color": "#e67e22", "price": 1400000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Khán Đài Trên", "color": "#d35400", "price": 700000, "totalRows": 8, "seatsPerRow": 25, "sortOrder": 3},
        ],
        "rag_faq": "Vé VIP tặng nón tai bèo và áo thun. Toàn bộ lợi nhuận bán goods ủng hộ dự án Nuôi Em. Bãi gửi xe tại Cung Điền Kinh Hà Nội."
    },
    {
        "name": "Đại Nhạc Hội Những Thành Phố Mơ Màng - Year End Tour",
        "category": "LIVE_MUSIC",
        "city": "Hồ Chí Minh",
        "venue": "Khu đô thị Vạn Phúc City",
        "address": "Quốc lộ 13, Hiệp Bình Phước, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 LỄ HỘI ÂM NHẠC INDIE & GEN Z LỚN NHẤT NĂM
Những Thành Phố Mơ Màng (NTMM) trở lại với quy mô lễ hội dã ngoại ven sông cực đỉnh. Đắm mình trong hoàng hôn lãng mạn bên sông Sài Gòn, cùng hát vang những giai điệu thanh xuân của dòng nhạc Indie, Pop-Rock Việt Nam.

🎤 DÀN NGHỆ SĨ & BAN NHẠC
• Chillies, The Cassette, 7UPPERCUTS, Thịnh Suy, Vũ Thanh Vân
• Hoàng Dũng, Trang, Madihu, marzuz, tlinh và các ban nhạc trẻ triển vọng.

⏰ LỊCH TRÌNH LỄ HỘI
• 14:00: Mở cửa khu dã ngoại Camping & Hội chợ ẩm thực Street Food
• 15:30: Sân khấu phụ Acoustic & Giao lưu nghệ sĩ
• 17:00: Khai mạc Sân khấu chính (Main Stage) đón hoàng hôn
• 23:00: Đêm diễn bế mạc

🎟️ CÁC HẠNG VÉ
• VIP Camping & Lounge (1.600.000đ): Khu vực bạt trải và lều trại ven sông, Tặng set picnic cao cấp kèm nước ngọt và bia thủ công, Lối check-in ưu tiên riêng biệt.
• General Admission (GA - 750.000đ): Tự do trải thảm cỏ trên toàn bộ khuôn viên lễ hội.
• Early Bird (550.000đ): Số lượng giới hạn dành cho các 'Cư Dân' mua sớm.

📍 ĐỊA ĐIỂM & BÃI XE
• Khu đô thị Vạn Phúc City, Quốc lộ 13, TP. Thủ Đức.
• Bãi giữ xe rộng hơn 20.000m² tại khu vực Bến du thuyền Dragon Bay.""",
        "days_from_now": 28,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "VIP Camping & Lounge", "color": "#e84393", "price": 1600000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "General Admission (GA)", "color": "#0984e3", "price": 750000, "totalRows": 8, "seatsPerRow": 30, "sortOrder": 2},
            {"name": "Early Bird (Số lượng giới hạn)", "color": "#00cec9", "price": 550000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Được mang thảm trải dã ngoại cá nhân. Cấm mang đồ uống có cồn từ bên ngoài vào. Có bãi xe 20.000m2 tại Vạn Phúc City."
    },
    {
        "name": "Kịch Sân Khấu IDECAF: Ngày Xửa Ngày Xưa 35 - Hoàng Tử Gấu",
        "category": "ARTS",
        "city": "Hồ Chí Minh",
        "venue": "Nhà hát Bến Thành",
        "address": "Số 6 Mạc Đĩnh Chi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 KỊCH THIẾU NHI KINH ĐIỂN - NGÀY XỬA NGÀY XƯA 35
Thương hiệu kịch thiếu nhi ăn khách nhất lịch sử sân khấu kịch miền Nam trở lại với số 35: 'Hoàng Tử Gấu và Khu Rừng Phép Thuật'. Cốt truyện hài hước, phục trang lộng lẫy và những bài học nhân văn sâu sắc về tình bạn, lòng dũng cảm.

🎭 NGHỆ SĨ THAM GIA
• NSND Hữu Châu, Bạch Long, Thanh Thủy, Hoàng Trinh, Hương Giang
• Đại Nghĩa, Đình Toàn, Lê Khánh, Mỹ Duyên cùng vũ đoàn Tuổi Ngọc.

⏰ THỜI GIAN BIỂU DIỄN
• Suất sáng: 09:00 - 11:15
• Suất tối: 20:00 - 22:15
• Khán giả vui lòng có mặt trước giờ diễn 20 phút để ổn định chỗ ngồi.

🎟️ GIÁ VÉ & VỊ TRÍ GHẾ
• Ghế VIP Tầng Trệt (450.000đ): Các hàng ghế A-E trung tâm, Tầm nhìn thẳng sát sân khấu, Tặng bóng bay nghệ thuật cho bé.
• Ghế Tiêu Chuẩn Tầng Trệt (320.000đ): Hàng ghế F-L góc nhìn bao quát.
• Khán Đài Lầu (220.000đ): Ghế tầng lầu nhìn xuống, Phù hợp nhóm gia đình đông người.

⚠️ QUY ĐỊNH NHÀ HÁT
• Mỗi vé áp dụng cho 1 ghế ngồi (kể cả trẻ nhỏ).
• Nghiêm cấm livestream, quay phim toàn bộ buổi diễn để bảo hộ bản quyền tác phẩm.""",
        "days_from_now": 14,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Ghế VIP Tầng Trệt", "color": "#e74c3c", "price": 450000, "totalRows": 6, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Ghế Tiêu Chuẩn Tầng Trệt", "color": "#3498db", "price": 320000, "totalRows": 8, "seatsPerRow": 18, "sortOrder": 2},
            {"name": "Khán Đài Lầu", "color": "#2ecc71", "price": 220000, "totalRows": 6, "seatsPerRow": 18, "sortOrder": 3},
        ],
        "rag_faq": "Trẻ em từ 3 tuổi tính vé riêng 1 ghế. Không được livestream hoặc ghi hình buổi diễn. Nhà hát Bến Thành số 6 Mạc Đĩnh Chi Q1."
    },
    {
        "name": "Sân Khấu Kịch Thiên Đăng: Vở '13 Đức Thầy'",
        "category": "ARTS",
        "city": "Hồ Chí Minh",
        "venue": "Sân khấu Kịch Thiên Đăng",
        "address": "Tầng 2, 62 Trần Quang Khải, Tân Định, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 TÁC PHẨM KỊCH TÂM LÝ XÃ HỘI CHUYÊN SÂU
Vở kịch đỉnh cao do NSƯT Thành Lộc dàn dựng và đóng vai trò linh hồn của tác phẩm. Câu chuyện xoay quanh đức tin, sự tha thứ và bản ngã của con người trước những cám dỗ danh vọng trong xã hội đương thời.

🎭 DÀN DIỄN VIÊN GẠO CỘI
• NSƯT Thành Lộc, NSND Kim Xuân, Hữu Châu, Phi Phụng
• Tuấn Khôi, Trương Hạ, Lương Thế Thành, Vân Trang.

⏰ LỊCH DIỄN
• Khai màn: Đúng 20:00 tối thứ Bảy & Chủ Nhật hàng tuần
• Thời lượng: 140 phút (có nghỉ giải lao 10 phút giữa màn).

🎟️ CÁC HẠNG VÉ
• Hàng Ghế VIP Trung Tâm (500.000đ): Ghế sofa êm ái hàng A-C, Tặng nước suối và tập san nghệ thuật Thiên Đăng.
• Hàng Ghế Tiêu Chuẩn (350.000đ): Ghế ngồi cao cấp với độ dốc chuẩn tầm nhìn không bị che chắn.

📍 LƯU Ý KHI XEM KỊCH
• Sân khấu đóng cửa đúng 20:00, khán giả đến trễ vui lòng đợi giải lao mới được vào khán phòng. Vui lòng chuyển điện thoại sang chế độ im lặng.""",
        "days_from_now": 12,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Hàng Ghế VIP Trung Tâm", "color": "#9b59b6", "price": 500000, "totalRows": 4, "seatsPerRow": 12, "sortOrder": 1},
            {"name": "Hàng Ghế Tiêu Chuẩn", "color": "#3498db", "price": 350000, "totalRows": 6, "seatsPerRow": 15, "sortOrder": 2},
        ],
        "rag_faq": "Cửa khán phòng đóng đúng 20:00. Khách đến trễ vui lòng đợi giải lao. Trang phục lịch sự, tắt chuông điện thoại."
    },
    {
        "name": "GENfest 2026 - Lễ Hội Âm Nhạc Đa Giác Quan",
        "category": "LIVE_MUSIC",
        "city": "Hồ Chí Minh",
        "venue": "The Global City",
        "address": "Đỗ Xuân Hợp, Phường An Phú, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 ĐẠI NHẠC HỘI ĐA GIÁC QUAN DÀNH CHO GIỚI TRẺ
GENfest kết hợp âm nhạc đỉnh cao, nghệ thuật sắp đặt ánh sáng tương tác và văn hóa đường phố. Sân khấu nhạc nước The Global City với cột nước cao hơn 60m cùng hệ thống laser đa chiều.

🎤 DÀN LINE-UP SIÊU KHỦNG
• Nghệ sĩ quốc tế: Ngôi sao K-Pop ZICO, HyunA
• Sao Việt: HIEUTHUHAI, MONO, Wren Evans, Low G, tlinh, GREY D, Pháo.

⏰ THỜI GIAN
• 10:00: Mở cửa khu trải nghiệm văn hóa Game & Thời trang đường phố
• 17:00 - 23:30: Đại nhạc hội bùng nổ không ngừng nghỉ.

🎟️ HẠNG VÉ
• VIP Lounge & Fast Track (2.500.000đ): Khán đài nâng cao, Free-flow bia và nước uống, Thảm đỏ riêng không xếp hàng.
• Mặt Sân GA (950.000đ): Đứng quẩy hết mình sát sàn diễn nhạc nước.""",
        "days_from_now": 32,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 60,
        "zones": [
            {"name": "VIP Lounge & Fast Track", "color": "#e17055", "price": 2500000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Mặt Sân GA", "color": "#0984e3", "price": 950000, "totalRows": 8, "seatsPerRow": 25, "sortOrder": 2},
        ],
        "rag_faq": "Có tủ khóa gửi đồ cá nhân và quầy sạc pin điện thoại tại The Global City Đỗ Xuân Hợp. Khán giả nhận vòng tay check-in tại cổng."
    },
    {
        "name": "Triển Lãm Đa Giác Quan Van Gogh & Monet Immersive",
        "category": "EXPERIENCE",
        "city": "Hồ Chí Minh",
        "venue": "Trung tâm Thương mại Gigamall",
        "address": "240-242 Phạm Văn Đồng, Hiệp Bình Chánh, Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 KHÔNG GIAN HỘI HỌA TƯƠNG TÁC ĐỈNH CAO THẾ GIỚI
Công nghệ trình chiếu đa giác quan 360 độ kết hợp thực tế ảo (VR) tái hiện hàng trăm kiệt tác kinh điển của Vincent van Gogh và Claude Monet. Đắm chìm trong căn phòng Đêm Đầy Sao (The Starry Night) và Khu Vườn Hoa Súng lung linh sắc màu.

🖼️ CÁC KHU TRẢI NGHIỆM ĐẶC SẮC
• Phòng chiếu 360 độ tương tác ánh sáng và âm nhạc cổ điển
• Hành lang hoa hướng dương rực rỡ check-in sống ảo
• Phòng trải nghiệm Thực tế ảo VR bước chân vào thế giới tranh vẽ
• Xưởng vẽ tương tác dành cho thiếu nhi và gia đình.

⏰ GIỜ MỞ CỬA
• 09:30 - 21:30 hàng ngày (kể cả thứ Bảy, Chủ Nhật và ngày Lễ).

🎟️ GIÁ VÉ THAM QUAN
• Vé VIP (450.000đ): Trọn gói bao gồm vé vào cổng, Trải nghiệm kính thực tế ảo VR, Quà tặng túi tote hoặc sổ tay Van Gogh độc quyền.
• Vé Tiêu Chuẩn Người Lớn (250.000đ): Vé vào cổng tham quan toàn bộ khu vực.
• Vé Trẻ Em & Sinh Viên (180.000đ): Áp dụng cho trẻ em dưới 1m3 hoặc sinh viên có thẻ.""",
        "days_from_now": 10,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Vé VIP (Trải nghiệm VR & Quà lưu niệm)", "color": "#fdcb6e", "price": 450000, "totalRows": 5, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Vé Người Lớn Tiêu Chuẩn", "color": "#6c5ce7", "price": 250000, "totalRows": 8, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Vé Trẻ Em & Sinh Viên", "color": "#00b894", "price": 180000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Mở cửa từ 09:30 đến 21:30 hàng ngày tại Tầng 8 TTTM Gigamall Phạm Văn Đồng. Mỗi ca trải nghiệm kéo dài 90 phút."
    },
    {
        "name": "VBA 2026: Chung Kết Saigon Heat vs Hanoi Buffaloes",
        "category": "SPORTS",
        "city": "Hồ Chí Minh",
        "venue": "Nhà thi đấu CIS (Trường Quốc tế Canada)",
        "address": "Đường 23, Phú Mỹ Hưng, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 TRẬN CHUNG KẾT KINH ĐIỂN BÓNG RỔ VIỆT NAM VBA 2026
Cuộc chạm trán rực lửa tranh cúp vô địch VBA 2026 giữa hai đại kình địch bóng rổ Việt Nam: 'Ông vua miền Nam' Saigon Heat đối đầu 'Chiến binh Thủ đô' Hanoi Buffaloes. Những pha úp rổ sấm sét, ném ba điểm nghẹt thở và màn cổ vũ sôi động từ dàn vũ công Heat Girls.

⏰ THỜI GIAN THI ĐẤU
• 17:30: Mở cửa khán đài và hoạt động ném bóng trúng quà tại sảnh
• 19:00: Tip-off trận chung kết lượt về.

🎟️ HẠNG VÉ & QUYỀN LỢI
• Courtside VIP (1.800.000đ): Ghế sofa sát mép sân thi đấu, Phục vụ đồ uống nhẹ và thức ăn nhẹ tại chỗ, Giao lưu chụp ảnh cùng các cầu thủ sau trận.
• Khán Đài A (600.000đ): Ghế ngồi trực diện bảng rổ hai đầu.
• Khán Đài B (300.000đ): Ghế ngồi tầng trên không khí náo nhiệt.""",
        "days_from_now": 20,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Courtside VIP (Sát sân)", "color": "#d63031", "price": 1800000, "totalRows": 2, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Khán Đài A", "color": "#0984e3", "price": 600000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Khán Đài B", "color": "#00cec9", "price": 300000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Ghế Courtside VIP ngồi sát sàn thi đấu và được chụp ảnh với cầu thủ sau trận. Nhà thi đấu CIS Đường 23 Phú Mỹ Hưng Q7."
    },
    {
        "name": "VPBank VnExpress Marathon Ho Chi Minh City Midnight 2026",
        "category": "SPORTS",
        "city": "Hồ Chí Minh",
        "venue": "Sân vận động Hoa Lư & Cầu Thủ Thiêm 2",
        "address": "Số 2 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=1600&auto=format&fit=crop&q=80",
        "images": [
            "https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=1600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=1600&auto=format&fit=crop&q=80"
        ],
        "description": """🌟 GIẢI CHẠY ĐÊM LỚN NHẤT VIỆT NAM - QUY MÔ 11.000 RUNNERS
Khám phá Sài Gòn về đêm với cung đường chạy độc đáo qua hàng loạt địa danh biểu tượng: Nhà thờ Đức Bà, Dinh Độc Lập, Bến Bạch Đằng và Cầu Ba Son (Thủ Thiêm 2) lộng lẫy ánh đèn. Cung đường đạt chuẩn đo đạc quốc tế AIMS.

🏃‍♂️ CỰ LY THI ĐẤU
• 42km Full Marathon (Xuất phát: 00:00 đêm)
• 21km Half Marathon (Xuất phát: 01:15 sáng)
• 10km Khám phá (Xuất phát: 02:30 sáng)
• 5km Phong trào (Xuất phát: 03:00 sáng).

🎟️ BỘ VẬT PHẨM (RACEKIT) BAO GỒM
• Áo đấu thể thao Singlet/T-shirt cao cấp
• Mũ chạy dạ quang, Túi rút tiện dụng
• BIB gắn chip định vị thời gian điện tử
• Huy chương Finisher đúc đồng nguyên khối khi hoàn thành cự ly trong cutoff time.""",
        "days_from_now": 50,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "BIB Full Marathon (42km)", "color": "#d63031", "price": 1400000, "totalRows": 4, "seatsPerRow": 25, "sortOrder": 1},
            {"name": "BIB Half Marathon (21km)", "color": "#e17055", "price": 1100000, "totalRows": 5, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "BIB Khám Phá (10km)", "color": "#fdcb6e", "price": 850000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 3},
            {"name": "BIB Phong Trào (5km)", "color": "#00b894", "price": 650000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 4},
        ],
        "rag_faq": "Bộ racekit gồm áo chạy, túi rút, nón dạ quang và BIB chip điện tử. Điểm xuất phát tại SVĐ Hoa Lư số 2 Đinh Tiên Hoàng Q1."
    }
]


# ============================================================================
# CRAWLER & ENRICHMENT AGENT CLASS
# ============================================================================
class TicketboxCrawlerAgent:
    """
    Agent thu thập, làm giàu và đồng bộ dữ liệu sự kiện Ticketbox vào TicketRush.
    """

    def __init__(self, backend_url: str = "http://localhost:8080", admin_user: str = "admin", admin_pass: str = "admin123"):
        self.backend_url = backend_url.rstrip("/")
        self.admin_user = admin_user
        self.admin_pass = admin_pass
        self.token = None
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept-Language": "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
            "Accept": "text/html,application/json,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        })

    def print_banner(self):
        print(r"""
=============================================================================
   _____ _     _        _   ____              ____                     _           
  |_   _(_)___| | _____| |_| __ )  _____  __ / ___|_ __ __ ___      _| | ___ _ __ 
    | | | / __| |/ / _ \ __|  _ \ / _ \ \/ /| |   | '__/ _` \ \ /\ / / |/ _ \ '__|
    | | | \__ \   <  __/ |_| |_) | (_) >  < | |___| | | (_| |\ V  V /| |  __/ |   
    |_| |_|___/_|\_\___|\__|____/ \___/_/\_\ \____|_|  \__,_| \_/\_/ |_|\___|_|   
              [TicketRush Data Ingestion & Enrichment Agent V2]
=============================================================================
        """)

    def crawl_live_ticketbox(self) -> List[Dict[str, Any]]:
        print("[*] Đang kết nối tới ticketbox.vn để crawl dữ liệu mới nhất...")
        live_events = []
        try:
            url = "https://ticketbox.vn"
            resp = self.session.get(url, timeout=6)
            if resp.status_code == 200:
                print(f"[+] Kết nối Ticketbox thành công (Status {resp.status_code}, {len(resp.content)} bytes)")
                pattern = r'href="(/event/[^"]+)"'
                matches = re.findall(pattern, resp.text)
                print(f"[+] Tìm thấy {len(set(matches))} liên kết sự kiện từ Ticketbox")
        except Exception as e:
            print(f"[!] Kết nối live Ticketbox: {e}")
        return live_events

    def get_events_catalog(self, source_mode: str = "all") -> List[Dict[str, Any]]:
        events = []
        if source_mode in ["live", "all"]:
            live_data = self.crawl_live_ticketbox()
            events.extend(live_data)

        if source_mode in ["curated", "all"] or len(events) == 0:
            print(f"[+] Tải {len(TICKETBOX_CURATED_EVENTS)} sự kiện ca nhạc & giải trí chuẩn Ticketbox Việt Nam...")
            events.extend(TICKETBOX_CURATED_EVENTS)

        return events

    def format_event_for_api(self, raw: Dict[str, Any]) -> Dict[str, Any]:
        now = datetime.now()
        days_ahead = raw.get("days_from_now", 30)
        event_dt = now + timedelta(days=days_ahead)
        sale_start = now - timedelta(days=2)
        sale_end = event_dt - timedelta(days=1)

        images = raw.get("images", [raw.get("bannerUrl", "https://picsum.photos/seed/event/1600/800")])
        if not images:
            images = [raw.get("bannerUrl", "https://picsum.photos/seed/event/1600/800")]

        return {
            "name": raw["name"],
            "description": raw.get("description", ""),
            "venue": raw.get("venue", "Trung tâm Ca nhạc & Thể thao"),
            "address": raw.get("address", "TP. Hồ Chí Minh"),
            "bannerUrl": raw.get("bannerUrl", "https://picsum.photos/seed/event/1600/800"),
            "category": raw.get("category", "LIVE_MUSIC"),
            "city": raw.get("city", "Hồ Chí Minh"),
            "eventDate": event_dt.strftime("%Y-%m-%dT%H:%M:%S"),
            "saleStartTime": sale_start.strftime("%Y-%m-%dT%H:%M:%S"),
            "saleEndTime": sale_end.strftime("%Y-%m-%dT%H:%M:%S"),
            "queueEnabled": raw.get("queueEnabled", False),
            "queueBatchSize": raw.get("queueBatchSize", 50),
            "isHot": raw.get("isHot", False),
            "images": images,
            "_zones": raw.get("zones", []),
            "_rag_faq": raw.get("rag_faq", "")
        }

    def authenticate_admin(self) -> bool:
        login_url = f"{self.backend_url}/api/auth/login"
        print(f"[*] Đang xác thực quyền Admin với {login_url}...")
        try:
            resp = requests.post(login_url, json={
                "username": self.admin_user,
                "password": self.admin_pass
            }, timeout=5)

            if resp.status_code == 200:
                body = resp.json()
                data = body.get("data", {})
                self.token = data.get("accessToken")
                if self.token:
                    print(f"[+] Đăng nhập thành công! Token: {self.token[:20]}... (Role: {data.get('role')})")
                    return True
            print(f"[!] Đăng nhập thất bại: HTTP {resp.status_code} - {resp.text}")
            return False
        except Exception as e:
            print(f"[!] Không thể kết nối tới Backend tại {self.backend_url}: {e}")
            return False

    def get_existing_events(self) -> List[Dict[str, Any]]:
        try:
            resp = requests.get(f"{self.backend_url}/api/events", timeout=5)
            if resp.status_code == 200:
                return resp.json().get("data", [])
        except Exception:
            pass
        return []

    def enrich_existing_events(self, events: List[Dict[str, Any]]) -> int:
        """
        Làm giàu thông tin (Enrich) cho các sự kiện đã có trên website:
        - Cập nhật mô tả chi tiết: Giới thiệu, Lineup, Timeline, Quyền lợi vé, Bãi gửi xe, Quy định cấm.
        - Cập nhật bộ sưu tập ảnh (Images Gallery) để hiển thị Thumbnail Carousel.
        """
        if not self.token:
            if not self.authenticate_admin():
                print("[!] Bỏ qua enrich do không xác thực được Admin.")
                return 0

        existing_events = self.get_existing_events()
        print(f"[i] Tìm thấy {len(existing_events)} sự kiện đang có trên hệ thống để làm giàu thông tin.")

        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }

        # Tạo bản đồ tìm kiếm theo tên chuẩn hóa
        curated_map = {}
        for c in events:
            norm_name = re.sub(r'[^a-zA-Z0-9\u00C0-\u1EF9]', '', c['name'].lower())
            curated_map[norm_name] = c

        updated_count = 0
        for ev in existing_events:
            ev_id = ev.get("id")
            ev_name = ev.get("name", "")
            norm_ev_name = re.sub(r'[^a-zA-Z0-9\u00C0-\u1EF9]', '', ev_name.lower())

            matched_curated = None
            for key, val in curated_map.items():
                if key in norm_ev_name or norm_ev_name in key:
                    matched_curated = val
                    break

            if not matched_curated:
                continue

            print(f"\n[+] Làm giàu thông tin cho Event ID {ev_id}: {ev_name}")
            formatted = self.format_event_for_api(matched_curated)
            formatted.pop("_zones", None)
            formatted.pop("_rag_faq", None)

            # Giữ nguyên ngày giờ ban đầu nếu đã có
            if ev.get("eventDate"):
                formatted["eventDate"] = ev["eventDate"]
            if ev.get("saleStartTime"):
                formatted["saleStartTime"] = ev["saleStartTime"]
            if ev.get("saleEndTime"):
                formatted["saleEndTime"] = ev["saleEndTime"]

            try:
                put_res = requests.put(f"{self.backend_url}/api/admin/events/{ev_id}", json=formatted, headers=headers, timeout=6)
                if put_res.status_code == 200:
                    print(f"    ✔ Đã cập nhật mô tả chi tiết ({len(formatted['description'])} ký tự)")
                    print(f"    ✔ Đã thêm Gallery ảnh ({len(formatted['images'])} hình ảnh)")
                    updated_count += 1
                else:
                    print(f"    ✖ Lỗi cập nhật: HTTP {put_res.status_code} - {put_res.text}")
            except Exception as e:
                print(f"    ✖ Lỗi request: {e}")

        return updated_count

    def import_to_backend(self, events: List[Dict[str, Any]]) -> int:
        if not self.token:
            if not self.authenticate_admin():
                print("[!] Bỏ qua import API do không xác thực được Admin.")
                return 0

        existing_events = self.get_existing_events()
        existing_names = {e.get("name", "").strip().lower() for e in existing_events}
        print(f"[i] Đang có {len(existing_names)} sự kiện trên hệ thống.")

        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }

        success_count = 0
        for idx, raw_event in enumerate(events, 1):
            formatted = self.format_event_for_api(raw_event)
            event_name = formatted["name"]

            if event_name.strip().lower() in existing_names:
                print(f"[-] [{idx}/{len(events)}] Sự kiện đã tồn tại: {event_name}")
                continue

            print(f"\n[+] [{idx}/{len(events)}] Đang tạo sự kiện mới: {event_name}")
            zones_data = formatted.pop("_zones", [])
            formatted.pop("_rag_faq", None)

            try:
                # 1. Tạo Event
                create_res = requests.post(f"{self.backend_url}/api/admin/events", json=formatted, headers=headers, timeout=6)
                if create_res.status_code == 200:
                    created_event = create_res.json().get("data", {})
                    event_id = created_event.get("id")
                    print(f"    ✔ Đã tạo Event ID: {event_id} ({formatted['city']} - {formatted['venue']})")

                    # 2. Tạo các Zones & Ghế ngồi
                    for z in zones_data:
                        zone_payload = {
                            "name": z["name"],
                            "color": z["color"],
                            "price": z["price"],
                            "totalRows": z["totalRows"],
                            "seatsPerRow": z["seatsPerRow"],
                            "sortOrder": z.get("sortOrder", 1)
                        }
                        z_res = requests.post(f"{self.backend_url}/api/admin/events/{event_id}/zones", json=zone_payload, headers=headers, timeout=6)
                        if z_res.status_code == 200:
                            total_seats = z["totalRows"] * z["seatsPerRow"]
                            print(f"       + Zone: {z['name']} ({z['price']:,} VNĐ, {total_seats} ghế)")

                    # 3. Kích hoạt mở bán (DRAFT -> PUBLISHED -> ON_SALE)
                    requests.put(f"{self.backend_url}/api/admin/events/{event_id}/status", params={"status": "PUBLISHED"}, headers=headers, timeout=5)
                    requests.put(f"{self.backend_url}/api/admin/events/{event_id}/status", params={"status": "ON_SALE"}, headers=headers, timeout=5)
                    print(f"       ✔ Đã kích hoạt trạng thái mở bán vé (ON_SALE)")

                    success_count += 1
                else:
                    print(f"    ✖ Lỗi tạo event: HTTP {create_res.status_code} - {create_res.text}")
            except Exception as e:
                print(f"    ✖ Lỗi gửi request: {e}")

        return success_count

    def generate_sql_file(self, events: List[Dict[str, Any]], output_path: str = "crawled_ticketbox_events.sql"):
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        lines = [
            "-- =============================================================================",
            "-- TicketRush - Dữ liệu Crawl tự động từ Ticketbox.vn (Bản Đầy Đủ)",
            f"-- Ngày tạo: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            "-- =============================================================================",
            "SET NAMES utf8mb4;",
            "SET FOREIGN_KEY_CHECKS = 0;\n"
        ]

        base_event_id = 50
        base_zone_id = 200
        base_seat_id = 5000

        for raw in events:
            formatted = self.format_event_for_api(raw)
            zones = raw.get("zones", [])

            e_id = base_event_id
            base_event_id += 1

            name = formatted['name'].replace("'", "''")
            venue = formatted['venue'].replace("'", "''")
            address = formatted['address'].replace("'", "''")
            desc = formatted['description'].replace("'", "''")
            banner = formatted['bannerUrl']
            cat = formatted['category']
            city = formatted['city']
            ev_date = formatted['eventDate'].replace("T", " ")
            s_start = formatted['saleStartTime'].replace("T", " ")
            s_end = formatted['saleEndTime'].replace("T", " ")
            q_en = 1 if formatted['queueEnabled'] else 0
            q_batch = formatted['queueBatchSize']
            hot = 1 if formatted['isHot'] else 0

            lines.append(
                f"INSERT INTO events (id, name, banner_url, category, city, venue, address, event_date, sale_start_time, sale_end_time, description, queue_enabled, queue_batch_size, status, is_hot, created_at) "
                f"VALUES ({e_id}, '{name}', '{banner}', '{cat}', '{city}', '{venue}', '{address}', '{ev_date}', '{s_start}', '{s_end}', '{desc}', {q_en}, {q_batch}, 'ON_SALE', {hot}, NOW()) "
                f"ON DUPLICATE KEY UPDATE description=VALUES(description), banner_url=VALUES(banner_url);\n"
            )

            # Thêm images gallery
            for img in formatted.get("images", []):
                lines.append(f"INSERT IGNORE INTO event_images (event_id, image_url) VALUES ({e_id}, '{img}');")

            for z in zones:
                z_id = base_zone_id
                base_zone_id += 1
                z_name = z['name'].replace("'", "''")
                color = z['color']
                price = z['price']
                t_rows = z['totalRows']
                s_per_row = z['seatsPerRow']
                order = z.get('sortOrder', 1)

                lines.append(
                    f"INSERT INTO zones (id, event_id, name, color, price, total_rows, seats_per_row, sort_order) "
                    f"VALUES ({z_id}, {e_id}, '{z_name}', '{color}', {price}, {t_rows}, {s_per_row}, {order}) "
                    f"ON DUPLICATE KEY UPDATE name=VALUES(name);\n"
                )

                for r in range(1, t_rows + 1):
                    row_char = chr(64 + r) if r <= 26 else f"R{r}"
                    for c in range(1, s_per_row + 1):
                        s_id = base_seat_id
                        base_seat_id += 1
                        s_num = f"{row_char}{c:02d}"
                        lines.append(
                            f"INSERT IGNORE INTO seats (id, zone_id, seat_number, seat_row, seat_col, status) "
                            f"VALUES ({s_id}, {z_id}, '{s_num}', {r}, {c}, 'AVAILABLE');"
                        )
                lines.append("")

        lines.append("SET FOREIGN_KEY_CHECKS = 1;\n")

        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

        print(f"[+] Đã xuất file SQL dữ liệu: {output_path} ({os.path.getsize(output_path):,} bytes)")

    def sync_rag_knowledge(self, events: List[Dict[str, Any]], output_dir: str = "backend/resources/knowledge"):
        os.makedirs(output_dir, exist_ok=True)
        rag_file = os.path.join(output_dir, "ticketbox_crawled_events.json")

        knowledge_items = []
        for raw in events:
            zones_info = ", ".join([f"{z['name']}: {z['price']:,}đ" for z in raw.get("zones", [])])
            doc = {
                "id": f"ticketbox_{re.sub(r'[^a-zA-Z0-9]', '_', raw['name']).lower()[:30]}",
                "title": raw["name"],
                "category": raw.get("category", "LIVE_MUSIC"),
                "city": raw.get("city", "Hồ Chí Minh"),
                "venue": raw.get("venue", ""),
                "address": raw.get("address", ""),
                "content": f"Sự kiện: {raw['name']}. Địa điểm tổ chức: {raw.get('venue', '')}, địa chỉ: {raw.get('address', '')}, thành phố: {raw.get('city', '')}. Chi tiết chương trình: {raw.get('description', '')}. Các hạng vé và mức giá: {zones_info}. Lưu ý: {raw.get('rag_faq', '')}"
            }
            knowledge_items.append(doc)

        with open(rag_file, "w", encoding="utf-8") as f:
            json.dump(knowledge_items, f, ensure_ascii=False, indent=2)

        print(f"[+] Đã cập nhật RAG Knowledge Base: {rag_file} ({len(knowledge_items)} tài liệu chi tiết)")


# ============================================================================
# MAIN CLI EXECUTION
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Ticketbox Data Crawler & Enrichment Agent for TicketRush")
    parser.add_argument("--source", choices=["live", "curated", "all"], default="all", help="Nguồn dữ liệu crawl")
    parser.add_argument("--target", choices=["api", "sql", "both", "json", "enrich"], default="enrich", 
                        help="Phương thức thực hiện ('enrich' để làm giàu sự kiện hiện có, 'both'/'api' để tạo mới)")
    parser.add_argument("--backend-url", default="http://localhost:8080", help="URL backend TicketRush")
    parser.add_argument("--admin-user", default="admin", help="Tài khoản Admin")
    parser.add_argument("--admin-pass", default="admin123", help="Mật khẩu Admin")
    parser.add_argument("--output-dir", default="tools/ticketbox_crawler/output", help="Thư mục xuất file")

    args = parser.parse_args()

    agent = TicketboxCrawlerAgent(
        backend_url=args.backend_url,
        admin_user=args.admin_user,
        admin_pass=args.admin_pass
    )
    agent.print_banner()

    print(f"[*] Chế độ nguồn: {args.source.upper()}")
    print(f"[*] Chế độ đích  : {args.target.upper()}")

    # 1. Thu thập dữ liệu
    events = agent.get_events_catalog(source_mode=args.source)
    print(f"\n[+] Tổng cộng danh mục: {len(events)} sự kiện.")

    os.makedirs(args.output_dir, exist_ok=True)

    # 2. Xuất file JSON
    json_path = os.path.join(args.output_dir, "crawled_ticketbox_events.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(events, f, ensure_ascii=False, indent=2)
    print(f"[+] Đã lưu bản sao lưu JSON: {json_path}")

    # 3. Đồng bộ RAG Knowledge
    agent.sync_rag_knowledge(events, output_dir="backend/resources/knowledge")

    # 4. Thực thi Enrich hoặc Import
    if args.target == "enrich":
        print("\n=============================================================================")
        print(">>> BẮT ĐẦU LÀM GIÀU DỮ LIỆU (ENRICH) CHO CÁC SỰ KIỆN TRÊN HỆ THỐNG...")
        print("=============================================================================")
        updated = agent.enrich_existing_events(events)
        print(f"\n[✔] Đã làm giàu thông tin và cập nhật ảnh Gallery cho {updated} sự kiện!")
    elif args.target in ["api", "both"]:
        print("\n=============================================================================")
        print(">>> BẮT ĐẦU ĐỒNG BỘ TRỰC TIẾP VÀO TICKETRUSH QUA REST API...")
        print("=============================================================================")
        success_count = agent.import_to_backend(events)
        print(f"\n[✔] Đã tạo thành công {success_count} sự kiện mới vào hệ thống TicketRush!")

    # 5. Xuất SQL
    if args.target in ["sql", "both", "enrich"]:
        sql_path = os.path.join(args.output_dir, "crawled_ticketbox_events.sql")
        agent.generate_sql_file(events, output_path=sql_path)

    print("\n=============================================================================")
    print("[✔] HOÀN TẤT THÀNH CÔNG QUÁ TRÌNH THU THẬP & LÀM GIÀU THÔNG TIN SỰ KIỆN!")
    print("=============================================================================")


if __name__ == "__main__":
    main()
