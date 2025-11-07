package com.dadp.jdbc;

import java.sql.*;
import java.util.Properties;

/**
 * DADP JDBC Wrapper Driver
 * 
 * JDBC URL 형식: jdbc:dadp:mysql://... 또는 jdbc:dadp:postgresql://...
 * 실제 DB URL로 변환하여 실제 Driver로 연결을 위임합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class DadpJdbcDriver implements Driver {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DadpJdbcDriver.class);
    
    private static final String DADP_URL_PREFIX = "jdbc:dadp:";
    private static final int MAJOR_VERSION = 3;
    private static final int MINOR_VERSION = 0;
    
    static {
        try {
            DriverManager.registerDriver(new DadpJdbcDriver());
            log.info("✅ DADP JDBC Driver 등록 완료");
        } catch (SQLException e) {
            log.error("❌ DADP JDBC Driver 등록 실패", e);
            throw new RuntimeException("DADP JDBC Driver 등록 실패", e);
        }
    }
    
    /**
     * JDBC URL이 DADP URL 형식인지 확인
     */
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            return false;
        }
        return url.startsWith(DADP_URL_PREFIX);
    }
    
    /**
     * Connection 생성
     * DADP URL을 실제 DB URL로 변환하여 실제 Driver로 연결
     */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        
        try {
            log.info("🔗 DADP JDBC Driver 연결 요청: {}", url);
            
            // JDBC URL에서 Proxy 설정 파라미터 추출 (hubUrl, instanceId, failOpen)
            java.util.Map<String, String> proxyParams = extractProxyParams(url);
            if (!proxyParams.isEmpty()) {
                log.info("✅ Proxy 설정 파라미터 추출: {}", proxyParams);
            } else {
                log.warn("⚠️ Proxy 설정 파라미터가 없습니다. 시스템 프로퍼티나 환경 변수를 사용합니다.");
            }
            
            // DADP URL을 실제 DB URL로 변환 (Proxy 파라미터 제거)
            String actualUrl = extractActualUrl(url);
            log.info("🔗 실제 DB URL: {}", actualUrl);
            
            // 실제 Driver로 연결
            Connection actualConnection = DriverManager.getConnection(actualUrl, info);
            
            // Proxy Connection으로 래핑 (Proxy 설정 전달)
            return new DadpProxyConnection(actualConnection, url, proxyParams);
            
        } catch (SQLException e) {
            log.error("❌ DADP JDBC Driver 연결 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * JDBC URL에서 Proxy 설정 파라미터 추출
     * 예: jdbc:dadp:mysql://localhost:3306/db?hubUrl=http://localhost:9004/hub&instanceId=sample-app-1
     * → {hubUrl: "http://localhost:9004/hub", instanceId: "sample-app-1"}
     */
    private java.util.Map<String, String> extractProxyParams(String dadpUrl) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        
        int queryIndex = dadpUrl.indexOf('?');
        if (queryIndex == -1) {
            return params; // 쿼리 파라미터 없음
        }
        
        String queryString = dadpUrl.substring(queryIndex + 1);
        String[] pairs = queryString.split("&");
        
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex).trim();
                String value = pair.substring(eqIndex + 1).trim();
                
                // Proxy 설정 파라미터만 추출
                if ("hubUrl".equals(key) || "instanceId".equals(key) || "failOpen".equals(key)) {
                    try {
                        // URL 디코딩
                        value = java.net.URLDecoder.decode(value, "UTF-8");
                    } catch (java.io.UnsupportedEncodingException e) {
                        // UTF-8은 항상 지원되므로 발생하지 않음
                    }
                    params.put(key, value);
                }
            }
        }
        
        return params;
    }
    
    /**
     * DADP URL에서 실제 DB URL 추출 (Proxy 파라미터 제거)
     * 예: jdbc:dadp:mysql://localhost:3306/db?hubUrl=...&useSSL=false
     * → jdbc:mysql://localhost:3306/db?useSSL=false
     */
    private String extractActualUrl(String dadpUrl) {
        if (!dadpUrl.startsWith(DADP_URL_PREFIX)) {
            throw new IllegalArgumentException("Invalid DADP URL: " + dadpUrl);
        }
        
        // jdbc:dadp: 제거
        String urlWithoutPrefix = dadpUrl.substring(DADP_URL_PREFIX.length());
        
        // Proxy 파라미터 제거 (hubUrl, instanceId, failOpen)
        int queryIndex = urlWithoutPrefix.indexOf('?');
        if (queryIndex != -1) {
            String baseUrl = urlWithoutPrefix.substring(0, queryIndex);
            String queryString = urlWithoutPrefix.substring(queryIndex + 1);
            
            // Proxy 파라미터를 제외한 쿼리 파라미터만 유지
            java.util.List<String> validParams = new java.util.ArrayList<>();
            String[] pairs = queryString.split("&");
            
            for (String pair : pairs) {
                int eqIndex = pair.indexOf('=');
                if (eqIndex > 0) {
                    String key = pair.substring(0, eqIndex).trim();
                    // Proxy 파라미터가 아니면 유지
                    if (!"hubUrl".equals(key) && !"instanceId".equals(key) && !"failOpen".equals(key)) {
                        validParams.add(pair);
                    }
                } else {
                    // 키=값 형식이 아닌 경우도 유지
                    validParams.add(pair);
                }
            }
            
            // 유효한 파라미터가 있으면 재구성
            if (!validParams.isEmpty()) {
                urlWithoutPrefix = baseUrl + "?" + String.join("&", validParams);
            } else {
                urlWithoutPrefix = baseUrl;
            }
        }
        
        // jdbc: 접두사 추가
        return "jdbc:" + urlWithoutPrefix;
    }
    
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }
    
    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }
    
    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }
    
    @Override
    public boolean jdbcCompliant() {
        return false; // JDBC 호환성 검증 우회
    }
    
    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }
}

