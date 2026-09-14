package com.ticketrush.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ticketrush.entity.Ticket;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Service
public class QRCodeService {

    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;

    /**
     * Sinh mã QR cho vé.
     * Thông tin trong mã QR chứa các định danh (ID vé, ID sự kiện, ID ghế, ID người dùng).
     * Trả về chuỗi Base64 của ảnh QR để hiển thị trực tiếp trên Frontend.
     */
    public String generate(Ticket ticket) {
        try {
            String content = String.format("TICKETRUSH|TID:%d|EID:%d|SID:%d|USER:%d|%s",
                    ticket.getId(),
                    ticket.getEvent().getId(),
                    ticket.getSeat().getId(),
                    ticket.getUser().getId(),
                    ticket.getSeat().getLabel());

            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.MARGIN, 2
            );

            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
