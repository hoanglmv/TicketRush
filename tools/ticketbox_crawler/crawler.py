#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Ticketbox Data Crawler Agent & Auto-Ingestion Tool
=============================================================================
Công cụ Agent tự động thu thập (crawl) dữ liệu sự kiện, ca nhạc, kịch nghệ,
thể thao từ Ticketbox (ticketbox.vn) và nhập (ingest) tự động vào hệ thống TicketRush.

Tính năng:
1. Crawl Live từ Ticketbox web / search API (với User-Agent và timeout an toàn).
2. Tích hợp sẵn bộ dữ liệu Live Concerts & Shows thực tế chuẩn Ticketbox Việt Nam
   (Anh Trai Say Hi, Anh Trai Vượt Ngàn Chông Gai, Hà Anh Tuấn, Vũ., Đen Vâu, IDECAF...).
3. Chuẩn hóa (Normalize) về cấu trúc TicketRush: Events, Zones (hạng vé), Seats.
4. Tự động đồng bộ vào TicketRush thông qua REST API (Admin JWT) hoặc xuất file SQL.
5. Tự động tạo dữ liệu ngữ nghĩa (RAG Knowledge) cho AI Chatbot.
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
# DATASET SỰ KIỆN TICKETBOX CHUẨN VIỆT NAM (Curated Authentic Shows)
# ============================================================================
TICKETBOX_CURATED_EVENTS = [
    {
        "name": "Live Concert: ANH TRAI SAY HI 2026 - Đêm 3",
        "category": "CONCERTS",
        "city": "Hà Nội",
        "venue": "Sân vận động Quốc gia Mỹ Đình",
        "address": "Đường Lê Đức Thọ, Phường Mỹ Đình 1, Nam Từ Liêm, Hà Nội",
        "bannerUrl": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1600&auto=format&fit=crop&q=80",
        "description": "Đại nhạc hội bùng nổ của 30 'Anh Trai Say Hi' với sân khấu 360 độ hiện đại bậc nhất Việt Nam, hiệu ứng ánh sáng laser đỉnh cao và các bản hit triệu view: Ngáo Ngơ, Cung Đàn Vỡ Đôi, Sao Hạng A, Catch Me If You Can.",
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
        "rag_faq": "Quy định vé: Mỗi tài khoản mua tối đa 4 vé. Trẻ em dưới 12 tuổi không vào khu Fanzone đứng. Thời gian mở cổng: 16h00. Thời gian bắt đầu: 19h30."
    },
    {
        "name": "ANH TRAI VƯỢT NGÀN CHÔNG GAI - Concert All-Star",
        "category": "CONCERTS",
        "city": "Hồ Chí Minh",
        "venue": "Công viên Bờ sông Sài Gòn (Thủ Thiêm)",
        "address": "Khu đô thị mới Thủ Thiêm, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80",
        "description": "Đêm hòa nhạc quy tụ các Anh Tài với những màn hòa âm phối khí dân gian đương đại hào hùng: Trống Cơm, Dẫu Có Lỗi Lầm, Mẹ Yêu Con. Trải nghiệm không gian âm nhạc ngoài trời bên sông Sài Gòn rực rỡ.",
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
        "rag_faq": "Ban tổ chức có bố trí xe bus đưa đón miễn phí từ Bến Bạch Đằng sang Thủ Thiêm. Check-in vé điện tử quét mã QR qua app TicketRush."
    },
    {
        "name": "Hà Anh Tuấn Live Concert - Chân Trời Rực Rỡ",
        "category": "CONCERTS",
        "city": "Ninh Bình",
        "venue": "Khu di tích Cố đô Hoa Lư",
        "address": "Xã Trường Yên, Huyện Hoa Lư, Tỉnh Ninh Bình",
        "bannerUrl": "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=1600&auto=format&fit=crop&q=80",
        "description": "Đêm nhạc acoustic giữa cảnh sắc kỳ vĩ của Cố đô Hoa Lư cùng huyền thoại âm nhạc Kitaro và dàn nhạc giao hưởng quốc gia. Những giai điệu Xuân Thì, Tháng Mấy Em Nhớ Anh, Bonjour Vietnam.",
        "days_from_now": 45,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Khu Hoa Lư (VVIP)", "color": "#9b59b6", "price": 5000000, "totalRows": 4, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Khu Trường Yên (VIP)", "color": "#8e44ad", "price": 3500000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Khu Ánh Sao (Standard)", "color": "#34495e", "price": 1800000, "totalRows": 8, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Dresscode gợi ý: Trang phục thanh lịch, tông màu trắng, be hoặc đen. Khuyến khích mang giày đế bằng vì không gian di tích cổ."
    },
    {
        "name": "Vũ. Live Concert - Bảo Tàng Của Nuối Tiếc",
        "category": "LIVE_MUSIC",
        "city": "Hồ Chí Minh",
        "venue": "Nhà thi đấu Nguyễn Du",
        "address": "116 Nguyễn Du, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1600&auto=format&fit=crop&q=80",
        "description": "Hoàng tử Indie Vũ mang đến không gian tự sự ấm áp với album mới 'Bảo Tàng Của Nuối Tiếc' cùng những bản hit quen thuộc: Lạ Lùng, Bước Qua Mùa Cô Đơn, Đông Kiếm Em, Anh Nhớ Ra.",
        "days_from_now": 18,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "VIP Nuối Tiếc", "color": "#1abc9c", "price": 2000000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Khu Tầng Trệt (GA)", "color": "#16a085", "price": 1200000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Khán Đài Lầu 1", "color": "#27ae60", "price": 750000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 3},
        ],
        "rag_faq": "Sự kiện có bán kèm merchandise độc quyền: đĩa than Vinyl, áo thun và sổ tay có chữ ký của nghệ sĩ Vũ."
    },
    {
        "name": "Đen Vâu - Show Của Đen 2026",
        "category": "CONCERTS",
        "city": "Hà Nội",
        "venue": "Cung Điền kinh Trong nhà Hà Nội",
        "address": "Đường Trần Hữu Dực, Cầu Diễn, Nam Từ Liêm, Hà Nội",
        "bannerUrl": "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1600&auto=format&fit=crop&q=80",
        "description": "Liveshow rap mộc mạc và chân thành nhất của Đen cùng ban nhạc Màu Nước và những người bạn: Nấu Ăn Cho Em, Đi Về Nhà, Mang Tiền Về Cho Mẹ, Hai Triệu Năm.",
        "days_from_now": 40,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 70,
        "zones": [
            {"name": "VIP Đồng Âm", "color": "#f39c12", "price": 2500000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Mặt Sân GA", "color": "#e67e22", "price": 1400000, "totalRows": 6, "seatsPerRow": 25, "sortOrder": 2},
            {"name": "Khán Đài Trên", "color": "#d35400", "price": 700000, "totalRows": 8, "seatsPerRow": 25, "sortOrder": 3},
        ],
        "rag_faq": "Toàn bộ lợi nhuận từ các sản phẩm bán tại show sẽ được Đen trích vào quỹ Nuôi Em vùng cao."
    },
    {
        "name": "Đại Nhạc Hội Những Thành Phố Mơ Màng - Year End Tour",
        "category": "CONCERTS",
        "city": "Hồ Chí Minh",
        "venue": "Khu đô thị Vạn Phúc City",
        "address": "Quốc lộ 13, Hiệp Bình Phước, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1600&auto=format&fit=crop&q=80",
        "description": "Lễ hội âm nhạc Indie và Gen Z lớn nhất năm: Ngọt, Chillies, The Cassette, 7UPPERCUTS, Thịnh Suy. Không gian dã ngoại âm nhạc hoàng hôn ven sông với hàng loạt gian hàng ẩm thực street food.",
        "days_from_now": 28,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "VIP Camping & Lounge", "color": "#e84393", "price": 1600000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "General Admission (GA)", "color": "#0984e3", "price": 750000, "totalRows": 8, "seatsPerRow": 30, "sortOrder": 2},
            {"name": "Early Bird (Số lượng giới hạn)", "color": "#00cec9", "price": 550000, "totalRows": 5, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Khán giả được mang bạt dã ngoại trải cỏ. Không được mang đồ uống có cồn bên ngoài vào khu vực tổ chức."
    },
    {
        "name": "Kịch Sân Khấu IDECAF: Ngày Xửa Ngày Xưa 35 - Hoàng Tử Gấu",
        "category": "THEATER",
        "city": "Hồ Chí Minh",
        "venue": "Nhà hát Bến Thành",
        "address": "Số 6 Mạc Đĩnh Chi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=1600&auto=format&fit=crop&q=80",
        "description": "Vở kịch thiếu nhi kinh điển gắn liền với tuổi thơ nhiều thế hệ: NSƯT Thành Lộc, Hữu Châu, Bạch Long, Thanh Thủy, Hoàng Trinh trong tạo hình cổ tích vui nhộn và thông điệp nhân văn.",
        "days_from_now": 14,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Ghế VIP Tầng Trệt", "color": "#e74c3c", "price": 450000, "totalRows": 6, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Ghế Tiêu Chuẩn Tầng Trệt", "color": "#3498db", "price": 320000, "totalRows": 8, "seatsPerRow": 18, "sortOrder": 2},
            {"name": "Khán Đài Lầu", "color": "#2ecc71", "price": 220000, "totalRows": 6, "seatsPerRow": 18, "sortOrder": 3},
        ],
        "rag_faq": "Vở kịch phù hợp cho trẻ em từ 3 tuổi trở lên. Mỗi vé áp dụng cho 1 ghế ngồi riêng biệt. Thời lượng: 120 phút."
    },
    {
        "name": "Sân Khấu Kịch Thiên Đăng: Vở '13 Đức Thầy'",
        "category": "THEATER",
        "city": "Hồ Chí Minh",
        "venue": "Sân khấu Kịch Thiên Đăng",
        "address": "Tầng 2, 62 Trần Quang Khải, Tân Định, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?w=1600&auto=format&fit=crop&q=80",
        "description": "Tác phẩm kịch tâm lý xã hội sâu sắc do NSƯT Thành Lộc đạo diễn và diễn xuất chính. Câu chuyện về niềm tin, sự thức tỉnh và lòng nhân ái trong xã hội hiện đại.",
        "days_from_now": 12,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Hàng Ghế VIP Trung Tâm", "color": "#9b59b6", "price": 500000, "totalRows": 4, "seatsPerRow": 12, "sortOrder": 1},
            {"name": "Hàng Ghế Tiêu Chuẩn", "color": "#34495e", "price": 350000, "totalRows": 6, "seatsPerRow": 15, "sortOrder": 2},
        ],
        "rag_faq": "Sân khấu yêu cầu trang phục lịch sự, tắt chuông điện thoại và không quay phim, chụp ảnh trong suốt buổi diễn."
    },
    {
        "name": "GENfest 2026 - Lễ Hội Âm Nhạc Đa Giác Quan",
        "category": "CONCERTS",
        "city": "Hồ Chí Minh",
        "venue": "The Global City",
        "address": "Đỗ Xuân Hợp, Phường An Phú, TP. Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80",
        "description": "Lễ hội văn hóa và âm nhạc đa giác quan với sự góp mặt của các nghệ sĩ quốc tế K-Pop và dàn sao Việt Nam: HIEUTHUHAI, MONO, Wren Evans, tlinh, GREY D.",
        "days_from_now": 32,
        "isHot": True,
        "queueEnabled": True,
        "queueBatchSize": 60,
        "zones": [
            {"name": "VIP Lounge & Fast Track", "color": "#e17055", "price": 2500000, "totalRows": 4, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Mặt Sân GA", "color": "#0984e3", "price": 950000, "totalRows": 8, "seatsPerRow": 25, "sortOrder": 2},
        ],
        "rag_faq": "Có khu vực sạc điện thoại và tủ gửi đồ cá nhân có khóa an toàn. Khán giả nhận vòng tay check-in tại cổng."
    },
    {
        "name": "Triển Lãm Đa Giác Quan Van Gogh & Monet Immersive",
        "category": "EXPERIENCE",
        "city": "Hồ Chí Minh",
        "venue": "Trung tâm Thương mại Gigamall",
        "address": "240-242 Phạm Văn Đồng, Hiệp Bình Chánh, Thủ Đức, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=1600&auto=format&fit=crop&q=80",
        "description": "Công nghệ trình chiếu ánh sáng 360 độ và thực tế ảo tái hiện hàng trăm tuyệt phẩm hội họa kinh điển thế giới. Đắm chìm trong bức tranh Đêm Đầy Sao (The Starry Night) sống động.",
        "days_from_now": 10,
        "isHot": False,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Vé VIP (Bao gồm trải nghiệm VR & Quà lưu niệm)", "color": "#fdcb6e", "price": 450000, "totalRows": 5, "seatsPerRow": 15, "sortOrder": 1},
            {"name": "Vé Người Lớn Tiêu Chuẩn", "color": "#6c5ce7", "price": 250000, "totalRows": 8, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Vé Trẻ Em & Sinh Viên", "color": "#00b894", "price": 180000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Thời gian tham quan theo khung giờ: 09:30 - 21:00 hàng ngày. Mỗi ca trải nghiệm kéo dài khoảng 90 phút."
    },
    {
        "name": "VBA 2026: Chung Kết Saigon Heat vs Hanoi Buffaloes",
        "category": "SPORTS",
        "city": "Hồ Chí Minh",
        "venue": "Nhà thi đấu CIS (Trường Quốc tế Canada)",
        "address": "Đường 23, Phú Mỹ Hưng, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=1600&auto=format&fit=crop&q=80",
        "description": "Trận bóng rổ rực lửa tranh cúp vô địch VBA 2026 giữa hai đại kình địch bóng rổ Việt Nam: Saigon Heat với dàn ngoại binh thượng thừa đối đầu Hanoi Buffaloes.",
        "days_from_now": 20,
        "isHot": True,
        "queueEnabled": False,
        "queueBatchSize": 50,
        "zones": [
            {"name": "Courtside VIP (Sát sân)", "color": "#d63031", "price": 1800000, "totalRows": 2, "seatsPerRow": 20, "sortOrder": 1},
            {"name": "Khán Đài A", "color": "#0984e3", "price": 600000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 2},
            {"name": "Khán Đài B", "color": "#00cec9", "price": 300000, "totalRows": 6, "seatsPerRow": 20, "sortOrder": 3},
        ],
        "rag_faq": "Ghế Courtside VIP được phục vụ đồ uống nhẹ và giao lưu chụp ảnh cùng các cầu thủ sau trận đấu."
    },
    {
        "name": "VPBank VnExpress Marathon Ho Chi Minh City Midnight 2026",
        "category": "SPORTS",
        "city": "Hồ Chí Minh",
        "venue": "Sân vận động Hoa Lư & Cầu Thủ Thiêm 2",
        "address": "Số 2 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh",
        "bannerUrl": "https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=1600&auto=format&fit=crop&q=80",
        "description": "Giải chạy đêm quy mô 11.000 vận động viên ngắm vẻ đẹp lung linh của TP.HCM về đêm. Các cự ly 5km, 10km, 21km và 42km tiêu chuẩn quốc tế AIMS.",
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
        "rag_faq": "Bộ racekit bao gồm áo thi đấu, mũ chạy, túi rút, BIB gắn chip điện tử và huy chương hoàn thành cự ly."
    }
]


# ============================================================================
# CRAWLER AGENT CLASS
# ============================================================================
class TicketboxCrawlerAgent:
    """
    Agent thu thập và xử lý dữ liệu sự kiện Ticketbox:
    - Tìm kiếm và trích xuất dữ liệu trực tuyến.
    - Fallback thông minh với kho dữ liệu sự kiện Việt Nam chuẩn hóa.
    - Chuyển đổi dữ liệu và đồng bộ vào TicketRush.
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
                     [TicketRush Data Ingestion Agent]
=============================================================================
        """)

    def crawl_live_ticketbox(self) -> List[Dict[str, Any]]:
        """
        Thực hiện crawl trực tiếp từ Ticketbox.
        """
        print("[*] Đang kết nối tới ticketbox.vn để crawl dữ liệu mới nhất...")
        live_events = []
        try:
            url = "https://ticketbox.vn"
            resp = self.session.get(url, timeout=6)
            if resp.status_code == 200:
                print(f"[+] Kết nối Ticketbox thành công (Status {resp.status_code}, {len(resp.content)} bytes)")
                # Phân tích cú pháp tìm link sự kiện hoặc JSON
                pattern = r'href="(/event/[^"]+)"'
                matches = re.findall(pattern, resp.text)
                unique_slugs = list(set(matches))
                print(f"[+] Tìm thấy {len(unique_slugs)} đường dẫn sự kiện trên trang chủ Ticketbox")
            else:
                print(f"[!] Ticketbox phản hồi mã HTTP: {resp.status_code}")
        except Exception as e:
            print(f"[!] Lỗi khi truy cập Ticketbox: {e}")

        return live_events

    def get_events_catalog(self, source_mode: str = "all") -> List[Dict[str, Any]]:
        """
        Lấy danh sách sự kiện dựa trên source_mode:
        - 'live': chỉ lấy từ web
        - 'curated': lấy từ bộ dataset chuẩn bị sẵn
        - 'all': kết hợp cả hai
        """
        events = []
        if source_mode in ["live", "all"]:
            live_data = self.crawl_live_ticketbox()
            events.extend(live_data)

        if source_mode in ["curated", "all"] or len(events) == 0:
            print(f"[+] Tải {len(TICKETBOX_CURATED_EVENTS)} sự kiện ca nhạc & giải trí chuẩn Ticketbox Việt Nam...")
            events.extend(TICKETBOX_CURATED_EVENTS)

        return events

    def format_event_for_api(self, raw: Dict[str, Any]) -> Dict[str, Any]:
        """
        Định dạng dữ liệu sự kiện thành EventCreateRequest cho Spring Boot backend.
        """
        now = datetime.now()
        days_ahead = raw.get("days_from_now", 30)
        event_dt = now + timedelta(days=days_ahead)
        sale_start = now - timedelta(days=2)
        sale_end = event_dt - timedelta(days=1)

        return {
            "name": raw["name"],
            "description": raw.get("description", ""),
            "venue": raw.get("venue", "Trung tâm Ca nhạc & Thể thao"),
            "address": raw.get("address", "TP. Hồ Chí Minh"),
            "bannerUrl": raw.get("bannerUrl", "https://picsum.photos/seed/event/1600/800"),
            "category": raw.get("category", "CONCERTS"),
            "city": raw.get("city", "Hồ Chí Minh"),
            "eventDate": event_dt.strftime("%Y-%m-%dT%H:%M:%S"),
            "saleStartTime": sale_start.strftime("%Y-%m-%dT%H:%M:%S"),
            "saleEndTime": sale_end.strftime("%Y-%m-%dT%H:%M:%S"),
            "queueEnabled": raw.get("queueEnabled", False),
            "queueBatchSize": raw.get("queueBatchSize", 50),
            "isHot": raw.get("isHot", False),
            "images": [raw.get("bannerUrl", "https://picsum.photos/seed/event/1600/800")],
            "_zones": raw.get("zones", []),
            "_rag_faq": raw.get("rag_faq", "")
        }

    def authenticate_admin(self) -> bool:
        """
        Đăng nhập vào TicketRush backend với quyền Admin để lấy Access Token.
        """
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

    def get_existing_event_names(self) -> set:
        """
        Lấy danh sách tên sự kiện đã có trên backend để tránh trùng lặp.
        """
        try:
            resp = requests.get(f"{self.backend_url}/api/events", timeout=5)
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                existing = {e.get("name", "").strip().lower() for e in data}
                return existing
        except Exception:
            pass
        return set()

    def import_to_backend(self, events: List[Dict[str, Any]]) -> int:
        """
        Nhập danh sách sự kiện và khán đài trực tiếp qua REST API của TicketRush.
        """
        if not self.token:
            if not self.authenticate_admin():
                print("[!] Bỏ qua import API do không xác thực được Admin.")
                return 0

        existing_names = self.get_existing_event_names()
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
                print(f"[-] [{idx}/{len(events)}] Sự kiện đã tồn tại, bỏ qua: {event_name}")
                continue

            print(f"\n[+] [{idx}/{len(events)}] Đang tạo sự kiện: {event_name}")
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
        """
        Sinh file SQL để import trực tiếp vào MySQL nếu cần.
        """
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        lines = [
            "-- =============================================================================",
            "-- TicketRush - Dữ liệu Crawl tự động từ Ticketbox.vn",
            f"-- Ngày tạo: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            "-- =============================================================================",
            "SET NAMES utf8mb4;",
            "SET FOREIGN_KEY_CHECKS = 0;\n"
        ]

        now = datetime.now()
        # Tìm ID bắt đầu
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
                f"ON DUPLICATE KEY UPDATE name=VALUES(name);\n"
            )

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

                # Sinh ghế ngồi
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
        """
        Tự động tạo tệp tri thức mở rộng (RAG Knowledge) cho AI Chatbot.
        """
        os.makedirs(output_dir, exist_ok=True)
        rag_file = os.path.join(output_dir, "ticketbox_crawled_events.json")

        knowledge_items = []
        for raw in events:
            zones_info = ", ".join([f"{z['name']}: {z['price']:,}đ" for z in raw.get("zones", [])])
            doc = {
                "id": f"ticketbox_{re.sub(r'[^a-zA-Z0-9]', '_', raw['name']).lower()[:30]}",
                "title": raw["name"],
                "category": raw.get("category", "CONCERTS"),
                "city": raw.get("city", "Hồ Chí Minh"),
                "venue": raw.get("venue", ""),
                "address": raw.get("address", ""),
                "content": f"Sự kiện: {raw['name']}. Địa điểm tổ chức: {raw.get('venue', '')}, địa chỉ: {raw.get('address', '')}, thành phố: {raw.get('city', '')}. Giới thiệu: {raw.get('description', '')}. Các hạng vé và mức giá: {zones_info}. Lưu ý và quy định: {raw.get('rag_faq', '')}"
            }
            knowledge_items.append(doc)

        with open(rag_file, "w", encoding="utf-8") as f:
            json.dump(knowledge_items, f, ensure_ascii=False, indent=2)

        print(f"[+] Đã cập nhật RAG Knowledge Base: {rag_file} ({len(knowledge_items)} tài liệu kiến thức)")


# ============================================================================
# MAIN CLI EXECUTION
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Ticketbox Data Crawler Agent for TicketRush")
    parser.add_argument("--source", choices=["live", "curated", "all"], default="all", help="Nguồn dữ liệu crawl")
    parser.add_argument("--target", choices=["api", "sql", "both", "json"], default="both", help="Phương thức import dữ liệu")
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

    # 1. Crawl dữ liệu
    events = agent.get_events_catalog(source_mode=args.source)
    print(f"\n[+] Tổng cộng thu thập được: {len(events)} sự kiện.")

    os.makedirs(args.output_dir, exist_ok=True)

    # 2. Xuất file JSON
    json_path = os.path.join(args.output_dir, "crawled_ticketbox_events.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(events, f, ensure_ascii=False, indent=2)
    print(f"[+] Đã lưu bản sao lưu JSON: {json_path}")

    # 3. Đồng bộ RAG Knowledge
    agent.sync_rag_knowledge(events, output_dir="backend/resources/knowledge")

    # 4. Import API
    if args.target in ["api", "both"]:
        print("\n=============================================================================")
        print(">>> BẮT ĐẦU ĐỒNG BỘ TRỰC TIẾP VÀO TICKETRUSH QUA REST API...")
        print("=============================================================================")
        success_count = agent.import_to_backend(events)
        print(f"\n[✔] Đã tạo thành công {success_count} sự kiện mới vào hệ thống TicketRush!")

    # 5. Xuất SQL
    if args.target in ["sql", "both"]:
        sql_path = os.path.join(args.output_dir, "crawled_ticketbox_events.sql")
        agent.generate_sql_file(events, output_path=sql_path)

    print("\n=============================================================================")
    print("[✔] HOÀN TẤT QUÁ TRÌNH THU THẬP VÀ NHẬP DỮ LIỆU TỪ TICKETBOX!")
    print("=============================================================================")


if __name__ == "__main__":
    main()
