package app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicQRResponse {
    private Long ticketId;
    private String qrCodeBase64;
    private int ttlSeconds;
    private String rawCode;
    private long epochWindow;
}
