package com.dadp.jdbc.schema;

import com.dadp.jdbc.policy.SchemaRecognizer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 스키마 동기화 서비스
 * 
 * Proxy에서 Hub로 스키마 메타데이터를 전송합니다.
 * JDK 내장 HttpClient를 사용하여 Spring 의존성 없이 동작합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class SchemaSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(SchemaSyncService.class);
    
    private final String hubUrl;
    private final String proxyInstanceId;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper objectMapper;
    private final SchemaRecognizer schemaRecognizer;
    
    // Proxy Instance별 마지막 동기화된 스키마 해시 (중복 동기화 방지)
    private static final ConcurrentHashMap<String, String> lastSchemaHash = new ConcurrentHashMap<>();
    
    public SchemaSyncService(String hubUrl, String proxyInstanceId) {
        this.hubUrl = hubUrl;
        this.proxyInstanceId = proxyInstanceId;
        this.connectTimeout = 5000; // 5초
        this.readTimeout = 10000; // 10초
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.schemaRecognizer = new SchemaRecognizer();
    }
    
    /**
     * 스키마 메타데이터를 Hub로 동기화
     * 
     * 스키마가 변경되지 않았으면 동기화를 건너뜁니다 (중복 동기화 방지).
     * 
     * @param connection DB 연결
     */
    public void syncSchemaToHub(Connection connection) {
        try {
            log.trace("🔄 Hub로 스키마 메타데이터 동기화 시작: proxyInstanceId={}", proxyInstanceId);
            
            // 스키마 메타데이터 수집
            List<SchemaRecognizer.SchemaMetadata> schemas = schemaRecognizer.collectSchemaMetadata(connection);
            
            // 스키마 해시 계산 (변경 감지용)
            String currentHash = calculateSchemaHash(schemas);
            String lastHash = lastSchemaHash.get(proxyInstanceId);
            
            // 스키마가 변경되지 않았으면 동기화 건너뛰기
            if (lastHash != null && currentHash.equals(lastHash)) {
                log.trace("⏭️ 스키마 변경 없음, 동기화 건너뜀: proxyInstanceId={} (해시: {})", 
                        proxyInstanceId, currentHash.substring(0, 8) + "...");
                return;
            }
            
            log.info("📤 스키마 변경 감지, Hub로 동기화 전송: {}개 컬럼", schemas.size());
            
            // Hub API로 전송
            String syncUrl = hubUrl + "/hub/api/v1/proxy/schema/sync";
            log.debug("🔗 Hub 스키마 동기화 URL: {}", syncUrl);
            
            SchemaSyncRequest request = new SchemaSyncRequest();
            request.setProxyInstanceId(proxyInstanceId);
            request.setSchemas(schemas);
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            // HttpURLConnection 사용 (Java 8 호환)
            URL url = new URL(syncUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);
            
            // 요청 본문 전송
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                writer.write(requestBody);
                writer.flush();
            }
            
            int statusCode = conn.getResponseCode();
            
            if (statusCode >= 200 && statusCode < 300) {
                // 응답 읽기
                StringBuilder responseBody = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                }
                
                SchemaSyncResponse syncResponse = objectMapper.readValue(responseBody.toString(), SchemaSyncResponse.class);
                if (syncResponse != null && syncResponse.isSuccess()) {
                    // 동기화 성공 시 해시 저장
                    lastSchemaHash.put(proxyInstanceId, currentHash);
                    log.info("✅ Hub로 스키마 메타데이터 동기화 완료: {}개 컬럼 (해시: {})", 
                            schemas.size(), currentHash.substring(0, 8) + "...");
                } else {
                    log.warn("⚠️ Hub로 스키마 메타데이터 동기화 실패: 응답 없음");
                }
            } else {
                log.warn("⚠️ Hub로 스키마 메타데이터 동기화 실패: HTTP {}", statusCode);
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            log.error("❌ Hub로 스키마 메타데이터 동기화 실패: {}", e.getMessage());
            // 동기화 실패해도 계속 진행 (Fail-open)
        }
    }
    
    /**
     * 스키마 메타데이터의 해시값 계산
     * 
     * 스키마 변경 감지를 위해 사용합니다.
     * 
     * @param schemas 스키마 메타데이터 목록
     * @return 해시값 (SHA-256)
     */
    private String calculateSchemaHash(List<SchemaRecognizer.SchemaMetadata> schemas) {
        try {
            // 스키마를 문자열로 직렬화
            StringBuilder sb = new StringBuilder();
            for (SchemaRecognizer.SchemaMetadata schema : schemas) {
                sb.append(schema.getDatabaseName()).append("|");
                sb.append(schema.getTableName()).append("|");
                sb.append(schema.getColumnName()).append("|");
                sb.append(schema.getColumnType()).append("|");
                sb.append(schema.getIsNullable()).append("|");
                sb.append(schema.getColumnDefault()).append("\n");
            }
            
            // SHA-256 해시 계산
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes("UTF-8"));
            
            // 16진수 문자열로 변환
            StringBuilder hashString = new StringBuilder();
            for (byte b : hashBytes) {
                hashString.append(String.format("%02x", b));
            }
            
            return hashString.toString();
        } catch (Exception e) {
            log.warn("⚠️ 스키마 해시 계산 실패, 기본값 사용: {}", e.getMessage());
            // 해시 계산 실패 시 타임스탬프 사용 (항상 변경된 것으로 간주)
            return String.valueOf(System.currentTimeMillis());
        }
    }
    
    /**
     * 스키마 해시 캐시 초기화 (강제 동기화 시 사용)
     */
    public void clearSchemaHash() {
        lastSchemaHash.remove(proxyInstanceId);
        log.info("🧹 스키마 해시 캐시 초기화: proxyInstanceId={}", proxyInstanceId);
    }
    
    /**
     * 스키마 동기화 요청 DTO
     */
    public static class SchemaSyncRequest {
        private String proxyInstanceId;
        private List<SchemaRecognizer.SchemaMetadata> schemas;
        
        public String getProxyInstanceId() {
            return proxyInstanceId;
        }
        
        public void setProxyInstanceId(String proxyInstanceId) {
            this.proxyInstanceId = proxyInstanceId;
        }
        
        public List<SchemaRecognizer.SchemaMetadata> getSchemas() {
            return schemas;
        }
        
        public void setSchemas(List<SchemaRecognizer.SchemaMetadata> schemas) {
            this.schemas = schemas;
        }
    }
    
    /**
     * 스키마 동기화 응답 DTO
     */
    public static class SchemaSyncResponse {
        private boolean success;
        private String message;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
