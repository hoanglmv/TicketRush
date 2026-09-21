package app.service;

import app.dto.DynamicQRResponse;
import app.entity.Ticket;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Service sinh và xác thực mã QR cho vé:
 * 1. Static QR: Dùng cho email và vé lưu trữ cơ bản.
 * 2. Dynamic TOTP QR: Sinh mã QR biến thiên mỗi 30 giây sử dụng thuật toán HMAC-SHA256
 *    để chống chụp màn hình bán lại (Anti-Screenshot Ticket Scalping Shield).
 */
@Slf4j
@Service
public class QRCodeService {

    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;
    private static final int TOTP_WINDOW_SECONDS = 30;

    @Value("${app.ticket.qr-secret:TicketRushAntiScalpingKey2026!@#$%^}")
    private String secretKey;

    /**
     * Sinh mã QR tĩnh truyền thống (tương thích ngược).
     */
    public String generate(Ticket ticket) {
        String content = String.format("TICKETRUSH|TID:%d|EID:%d|SID:%d|USER:%d|%s",
                ticket.getId(),
                ticket.getEvent().getId(),
                ticket.getSeat().getId(),
                ticket.getUser().getId(),
                ticket.getSeat().getLabel());
        return renderQrImageBase64(content);
    }

    /**
     * Sinh mã QR động (Dynamic TOTP QR) có thời hạn sống 30 giây kèm chữ ký số HMAC-SHA256.
     */
    public DynamicQRResponse generateDynamicQR(Ticket ticket) {
        long currentMillis = System.currentTimeMillis();
        long epochWindow = currentMillis / (TOTP_WINDOW_SECONDS * 1000L);
        int ttlSeconds = (int) (TOTP_WINDOW_SECONDS - ((currentMillis / 1000L) % TOTP_WINDOW_SECONDS));

        String payload = String.format("TID:%d|EID:%d|SID:%d|USER:%d|EP:%d",
                ticket.getId(),
                ticket.getEvent().getId(),
                ticket.getSeat().getId(),
                ticket.getUser().getId(),
                epochWindow);

        String signature = computeHmacSha256(payload, secretKey);
        String dynamicCode = String.format("TICKETRUSH-TOTP|%s|SIG:%s", payload, signature);
        String imageBase64 = renderQrImageBase64(dynamicCode);

        return DynamicQRResponse.builder()
                .ticketId(ticket.getId())
                .qrCodeBase64(imageBase64)
                .ttlSeconds(ttlSeconds)
                .rawCode(dynamicCode)
                .epochWindow(epochWindow)
                .build();
    }

    /**
     * Xác thực mã QR quét tại cổng soát vé:
     * - Kiểm tra cấu trúc mã TOTP.
     * - Kiểm tra khung thời gian hợp lệ (chấp nhận window hiện tại và window liền kề: ±30s).
     * - Kiểm tra chữ ký HMAC-SHA256 để chống giả mạo mã.
     *
     * @return ticketId nếu hợp lệ.
     */
    public Long verifyDynamicQR(String rawCode) {
        if (rawCode == null || !rawCode.startsWith("TICKETRUSH-TOTP|")) {
            // Hỗ trợ fallback kiểm tra định dạng tĩnh cũ nếu có
            if (rawCode != null && rawCode.startsWith("TICKETRUSH|TID:")) {
                return parseLegacyTicketId(rawCode);
            }
            throw new IllegalArgumentException("Mã QR không đúng định dạng của TicketRush.");
        }

        String[] parts = rawCode.split("\\|SIG:");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Mã QR thiếu chữ ký bảo mật xác thực.");
        }

        String payloadWithPrefix = parts[0];
        String expectedSignature = parts[1];
        String payload = payloadWithPrefix.substring("TICKETRUSH-TOTP|".length());

        // Phân tích payload
        Long ticketId = null;
        Long epochWindow = null;

        for (String field : payload.split("\\|")) {
            if (field.startsWith("TID:")) {
                ticketId = Long.parseLong(field.substring(4));
            } else if (field.startsWith("EP:")) {
                epochWindow = Long.parseLong(field.substring(3));
            }
        }

        if (ticketId == null || epochWindow == null) {
            throw new IllegalArgumentException("Dữ liệu trong mã QR không đầy đủ thông tin vé.");
        }

        // Kiểm tra thời hạn sống (Window tolerance: ±1 epoch tức ±30 giây)
        long currentEpoch = System.currentTimeMillis() / (TOTP_WINDOW_SECONDS * 1000L);
        if (Math.abs(currentEpoch - epochWindow) > 1) {
            throw new IllegalArgumentException("Mã QR đã hết hiệu lực (chống chụp màn hình). Vui lòng quét mã trực tiếp trên ứng dụng.");
        }

        // Xác minh chữ ký số HMAC
        String actualSignature = computeHmacSha256(payload, secretKey);
        if (!actualSignature.equalsIgnoreCase(expectedSignature)) {
            throw new IllegalArgumentException("Chữ ký bảo mật của vé không hợp lệ hoặc đã bị chỉnh sửa.");
        }

        return ticketId;
    }

    private Long parseLegacyTicketId(String rawCode) {
        for (String part : rawCode.split("\\|")) {
            if (part.startsWith("TID:")) {
                return Long.parseLong(part.substring(4));
            }
        }
        throw new IllegalArgumentException("Không tìm thấy ID vé trong mã QR.");
    }

    private String renderQrImageBase64(String content) {
        try {
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
            log.error("Failed to render QR Code image: {}", e.getMessage());
            throw new RuntimeException("Lỗi sinh ảnh mã QR", e);
        }
    }

    private String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tính toán chữ ký số HMAC-SHA256", e);
        }
    }
}
