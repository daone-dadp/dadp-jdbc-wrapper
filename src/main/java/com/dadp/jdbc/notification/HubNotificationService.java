package com.dadp.jdbc.notification;

import com.dadp.hub.crypto.HubCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Hub 알림 전송 서비스
 * 
 * Wrapper에서 발생한 암호화/복호화 오류를 Hub에 알림으로 전달합니다.
 * HubCryptoService(통합 컴포넌트)를 사용하여 Hub와의 통신을 수행합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.4
 * @since 2025-11-12
 */
public class HubNotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(HubNotificationService.class);
    
    private final HubCryptoService hubCryptoService;
    private final String hubUrl;
    private final String proxyInstanceId;
    private final RestTemplate restTemplate;
    
    public HubNotificationService(HubCryptoService hubCryptoService, String proxyInstanceId) {
        this.hubCryptoService = hubCryptoService;
        this.proxyInstanceId = proxyInstanceId;
        // HubCryptoService에서 hubUrl 추출 (리플렉션 사용)
        this.hubUrl = extractHubUrl(hubCryptoService);
        // HubCryptoService의 RestTemplate 재사용
        this.restTemplate = extractRestTemplate(hubCryptoService);
    }
    
    /**
     * HubCryptoService에서 hubUrl 추출 (리플렉션 사용)
     */
    private String extractHubUrl(HubCryptoService service) {
        try {
            java.lang.reflect.Field field = HubCryptoService.class.getDeclaredField("hubUrl");
            field.setAccessible(true);
            return (String) field.get(service);
        } catch (Exception e) {
            log.warn("⚠️ HubCryptoService에서 hubUrl 추출 실패, 기본값 사용: {}", e.getMessage());
            return "http://localhost:9004";
        }
    }
    
    /**
     * HubCryptoService에서 RestTemplate 추출 (리플렉션 사용)
     */
    private RestTemplate extractRestTemplate(HubCryptoService service) {
        try {
            java.lang.reflect.Field field = HubCryptoService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            return (RestTemplate) field.get(service);
        } catch (Exception e) {
            log.warn("⚠️ HubCryptoService에서 RestTemplate 추출 실패, 새로 생성: {}", e.getMessage());
            return new RestTemplate();
        }
    }
    
    /**
     * 암호화 오류 알림 전송
     * 
     * @param tableName 테이블명
     * @param columnName 컬럼명
     * @param policyName 정책명
     * @param errorMessage 오류 메시지
     */
    public void notifyEncryptionError(String tableName, String columnName, String policyName, String errorMessage) {
        String title = String.format("암호화 실패: %s.%s", tableName, columnName);
        String message = String.format("정책: %s, 오류: %s", policyName, errorMessage);
        sendNotification("CRYPTO_ERROR", "WARNING", title, message, "PROXY", proxyInstanceId, null);
    }
    
    /**
     * 복호화 오류 알림 전송
     * 
     * @param tableName 테이블명
     * @param columnName 컬럼명
     * @param errorMessage 오류 메시지
     */
    public void notifyDecryptionError(String tableName, String columnName, String errorMessage) {
        String title = String.format("복호화 실패: %s.%s", tableName, columnName);
        String message = String.format("오류: %s", errorMessage);
        sendNotification("CRYPTO_ERROR", "WARNING", title, message, "PROXY", proxyInstanceId, null);
    }
    
    /**
     * Hub에 알림 전송
     * 
     * HubCryptoService(통합 컴포넌트)를 사용하여 알림을 전송합니다.
     * 
     * @param type 알림 타입 (CRYPTO_ERROR, SYSTEM_ERROR 등)
     * @param level 알림 레벨 (WARNING, ERROR 등)
     * @param title 알림 제목
     * @param message 알림 메시지
     * @param entityType 엔티티 타입 (PROXY, ENGINE 등)
     * @param entityId 엔티티 ID
     * @param metadata 메타데이터 (JSON 문자열, 선택)
     */
    public void sendNotification(String type, String level, String title, String message, 
                                 String entityType, String entityId, String metadata) {
        try {
            String notificationUrl = hubUrl + "/hub/api/v1/notifications/external";
            
            // 요청 본문 생성
            Map<String, Object> body = new HashMap<>();
            body.put("type", type);
            body.put("level", level);
            body.put("title", title);
            body.put("message", message);
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            if (metadata != null) {
                body.put("metadata", metadata);
            }
            
            // HubCryptoService의 RestTemplate을 사용하여 요청 전송
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                notificationUrl, 
                HttpMethod.POST, 
                entity, 
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            // 응답 확인
            if (response.getStatusCode().value() >= 200 && response.getStatusCode().value() < 300) {
                log.debug("✅ Hub 알림 전송 성공: {} - {}", type, title);
            } else {
                log.warn("⚠️ Hub 알림 전송 실패 (HTTP {}): {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            // 알림 전송 실패는 로그만 출력 (앱 동작에는 영향 없음)
            log.warn("⚠️ Hub 알림 전송 중 오류 발생: {}", e.getMessage());
        }
    }
}

