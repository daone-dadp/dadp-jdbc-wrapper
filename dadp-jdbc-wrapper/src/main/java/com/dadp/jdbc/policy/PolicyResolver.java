package com.dadp.jdbc.policy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 정책 리졸버
 * 
 * 테이블.컬럼 → 정책명 자동 매핑을 수행합니다.
 * 규칙 기반, 카탈로그 기반, 허용리스트 기반 매핑을 지원합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class PolicyResolver {
    
    private static final Logger log = LoggerFactory.getLogger(PolicyResolver.class);
    
    // 캐시: 테이블.컬럼 → 정책명
    private final Map<String, String> policyCache = new ConcurrentHashMap<>();
    
    /**
     * 정책명 조회
     * 
     * @param tableName 테이블명
     * @param columnName 컬럼명
     * @return 정책명 (없으면 null)
     */
    public String resolvePolicy(String tableName, String columnName) {
        String key = tableName + "." + columnName;
        
        // Hub에서 로드한 매핑 정보만 사용 (캐시에서 조회)
        String policy = policyCache.get(key);
        
        if (policy != null) {
            log.trace("✅ 정책 캐시 적중: {} → {}", key, policy);
            return policy;
        }
        
        // Hub 매핑이 없으면 null 반환 (규칙 기반 매핑 제거)
        log.trace("❌ 정책 매핑 없음: {} (Hub 매핑에 등록되지 않음)", key);
        return null;
    }
    
    /**
     * 규칙 기반 정책 매핑
     * 컬럼명 패턴으로 매핑 (email, phone 등)
     */
    private String resolveByRules(String tableName, String columnName) {
        String columnLower = columnName.toLowerCase();
        
        // 이메일 패턴
        if (columnLower.contains("email") || columnLower.contains("mail")) {
            return "dadp";
        }
        
        // 전화번호 패턴
        if (columnLower.contains("phone") || columnLower.contains("tel") || columnLower.contains("mobile")) {
            return "dadp";
        }
        
        // 주민등록번호/주민번호 패턴
        if (columnLower.contains("ssn") || columnLower.contains("rrn") || columnLower.contains("resident")) {
            return "pii";
        }
        
        // 이름 패턴
        if (columnLower.contains("name") && !columnLower.contains("username")) {
            return "dadp";
        }
        
        // 주소 패턴
        if (columnLower.contains("address") || columnLower.contains("addr")) {
            return "dadp";
        }
        
        return null;
    }
    
    /**
     * 정책 매핑 캐시 갱신
     * Hub API로부터 최신 매핑 정보를 받아 캐시를 갱신합니다.
     * 
     * @param mappings 정책 매핑 맵 (테이블.컬럼 → 정책명)
     */
    public void refreshMappings(Map<String, String> mappings) {
        log.trace("🔄 정책 매핑 캐시 갱신 시작: {}개 매핑", mappings.size());
        policyCache.clear();
        policyCache.putAll(mappings);
        log.trace("✅ 정책 매핑 캐시 갱신 완료");
    }
    
    /**
     * 정책 매핑 캐시에 추가
     */
    public void addMapping(String tableName, String columnName, String policyName) {
        String key = tableName + "." + columnName;
        policyCache.put(key, policyName);
        log.trace("➕ 정책 매핑 추가: {} → {}", key, policyName);
    }
    
    /**
     * 정책 매핑 캐시에서 제거
     */
    public void removeMapping(String tableName, String columnName) {
        String key = tableName + "." + columnName;
        policyCache.remove(key);
        log.trace("➖ 정책 매핑 제거: {}", key);
    }
    
    /**
     * 정책 매핑 캐시 초기화
     */
    public void clearCache() {
        policyCache.clear();
        log.trace("🧹 정책 매핑 캐시 초기화");
    }
}

