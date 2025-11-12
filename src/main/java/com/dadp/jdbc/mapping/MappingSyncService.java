package com.dadp.jdbc.mapping;

import com.dadp.jdbc.policy.PolicyResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 매핑 동기화 서비스
 * 
 * Proxy에서 Hub로부터 정책 매핑 정보를 가져와서 PolicyResolver에 저장합니다.
 * JDK 내장 HttpClient를 사용하여 Spring 의존성 없이 동작합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class MappingSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(MappingSyncService.class);
    
    private final String hubUrl;
    private final String proxyInstanceId;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper objectMapper;
    private final PolicyResolver policyResolver;
    
    public MappingSyncService(String hubUrl, String proxyInstanceId, PolicyResolver policyResolver) {
        this.hubUrl = hubUrl;
        this.proxyInstanceId = proxyInstanceId;
        this.connectTimeout = 5000; // 5초
        this.readTimeout = 10000; // 10초
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.policyResolver = policyResolver;
    }
    
    /**
     * Hub에서 매핑 변경 여부 확인 (경량 요청)
     * 
     * @return 변경사항이 있으면 true, 없으면 false
     */
    public boolean checkMappingChange() {
        try {
            String checkUrl = hubUrl + "/hub/api/v1/proxy/mappings/check?proxyInstanceId=" + proxyInstanceId;
            log.trace("🔗 Hub 매핑 변경 확인 URL: {}", checkUrl);
            
            // HttpURLConnection 사용 (Java 8 호환)
            URL url = new URL(checkUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
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
                
                // ApiResponse<Boolean> 형태로 파싱
                CheckMappingChangeResponse checkResponse = objectMapper.readValue(responseBody.toString(), CheckMappingChangeResponse.class);
                if (checkResponse != null && checkResponse.isSuccess() && checkResponse.getData() != null) {
                    conn.disconnect();
                    return checkResponse.getData();
                }
            }
            conn.disconnect();
            return false;
        } catch (Exception e) {
            log.warn("⚠️ 매핑 변경 확인 실패: {}", e.getMessage());
            return false; // 실패 시 false 반환 (다음 확인 시 재시도)
        }
    }
    
    /**
     * Hub에서 정책 매핑 정보를 가져와서 PolicyResolver에 저장
     * 
     * @return 로드된 매핑 개수
     */
    public int loadMappingsFromHub() {
        try {
            log.trace("🔄 Hub에서 정책 매핑 정보 로드 시작: proxyInstanceId={}", proxyInstanceId);
            
            String mappingsUrl = hubUrl + "/hub/api/v1/proxy/mappings?proxyInstanceId=" + proxyInstanceId;
            log.trace("🔗 Hub 매핑 조회 URL: {}", mappingsUrl);
            
            // HttpURLConnection 사용 (Java 8 호환)
            URL url = new URL(mappingsUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
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
                
                MappingListResponse mappingResponse = objectMapper.readValue(responseBody.toString(), MappingListResponse.class);
                
                if (mappingResponse != null && mappingResponse.isSuccess() && mappingResponse.getData() != null) {
                    List<EncryptionMapping> mappings = mappingResponse.getData();
                    
                    // PolicyResolver 형식으로 변환 (테이블.컬럼 → 정책명)
                    Map<String, String> policyMap = new HashMap<>();
                    for (EncryptionMapping mapping : mappings) {
                        // enabled가 true인 경우만 추가
                        if (mapping.isEnabled()) {
                            String key = mapping.getTableName() + "." + mapping.getColumnName();
                            policyMap.put(key, mapping.getPolicyName());
                            log.trace("📋 매핑 로드: {} → {}", key, mapping.getPolicyName());
                        }
                    }
                    
                    // PolicyResolver에 반영
                    policyResolver.refreshMappings(policyMap);
                    
                    log.trace("✅ Hub에서 정책 매핑 정보 로드 완료: {}개 매핑", policyMap.size());
                    conn.disconnect();
                    return policyMap.size();
                } else {
                    log.warn("⚠️ Hub에서 정책 매핑 정보 로드 실패: 응답 없음 또는 실패");
                    conn.disconnect();
                    return 0;
                }
            } else {
                log.warn("⚠️ Hub에서 정책 매핑 정보 로드 실패: HTTP {}", statusCode);
                conn.disconnect();
                return 0;
            }
            
        } catch (Exception e) {
            log.error("❌ Hub에서 정책 매핑 정보 로드 실패: {}", e.getMessage());
            // 로드 실패해도 계속 진행 (Fail-open)
            return 0;
        }
    }
    
    /**
     * 매핑 변경 확인 응답 DTO
     */
    public static class CheckMappingChangeResponse {
        private boolean success;
        private Boolean data;
        private String message;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public Boolean getData() {
            return data;
        }
        
        public void setData(Boolean data) {
            this.data = data;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    /**
     * 매핑 목록 응답 DTO
     */
    public static class MappingListResponse {
        private boolean success;
        private List<EncryptionMapping> data;
        private String message;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public List<EncryptionMapping> getData() {
            return data;
        }
        
        public void setData(List<EncryptionMapping> data) {
            this.data = data;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    /**
     * 암호화 매핑 DTO
     */
    public static class EncryptionMapping {
        private String proxyInstanceId;
        private String databaseName;
        private String tableName;
        private String columnName;
        private String policyName;
        private boolean enabled;
        
        public String getProxyInstanceId() {
            return proxyInstanceId;
        }
        
        public void setProxyInstanceId(String proxyInstanceId) {
            this.proxyInstanceId = proxyInstanceId;
        }
        
        public String getDatabaseName() {
            return databaseName;
        }
        
        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        
        public String getColumnName() {
            return columnName;
        }
        
        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }
        
        public String getPolicyName() {
            return policyName;
        }
        
        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
