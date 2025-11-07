package com.dadp.jdbc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Proxy 설정 관리
 * 
 * 설정 우선순위:
 * 1. JDBC URL 쿼리 파라미터 (hubUrl, instanceId, failOpen)
 * 2. 시스템 프로퍼티 (dadp.proxy.hub-url, dadp.proxy.instance-id, dadp.proxy.fail-open)
 * 3. 환경 변수 (DADP_PROXY_HUB_URL, DADP_PROXY_INSTANCE_ID, DADP_PROXY_FAIL_OPEN)
 * 4. 기본값
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class ProxyConfig {
    
    private static final Logger log = LoggerFactory.getLogger(ProxyConfig.class);
    
    private static final String DEFAULT_HUB_URL = "http://localhost:9004";
    private static final String DEFAULT_INSTANCE_ID = "proxy-1";
    
    private static volatile ProxyConfig instance;
    private final String hubUrl;
    private final String instanceId;
    private final boolean failOpen;
    
    /**
     * JDBC URL 쿼리 파라미터에서 Proxy 설정을 읽어서 생성
     */
    public ProxyConfig(Map<String, String> urlParams) {
        // Hub URL 읽기 (우선순위: URL 파라미터 > 시스템 프로퍼티 > 환경 변수 > 기본값)
        String hubUrlProp = urlParams != null ? urlParams.get("hubUrl") : null;
        if (hubUrlProp == null || hubUrlProp.trim().isEmpty()) {
            hubUrlProp = System.getProperty("dadp.proxy.hub-url");
        }
        if (hubUrlProp == null || hubUrlProp.trim().isEmpty()) {
            hubUrlProp = System.getenv("DADP_PROXY_HUB_URL");
        }
        if (hubUrlProp == null || hubUrlProp.trim().isEmpty()) {
            hubUrlProp = DEFAULT_HUB_URL;
        }
        this.hubUrl = hubUrlProp.trim();
        
        // Instance ID 읽기
        String instanceIdProp = urlParams != null ? urlParams.get("instanceId") : null;
        if (instanceIdProp == null || instanceIdProp.trim().isEmpty()) {
            instanceIdProp = System.getProperty("dadp.proxy.instance-id");
        }
        if (instanceIdProp == null || instanceIdProp.trim().isEmpty()) {
            instanceIdProp = System.getenv("DADP_PROXY_INSTANCE_ID");
        }
        if (instanceIdProp == null || instanceIdProp.trim().isEmpty()) {
            instanceIdProp = DEFAULT_INSTANCE_ID;
        }
        this.instanceId = instanceIdProp.trim();
        
        // Fail-open 모드 읽기
        String failOpenProp = urlParams != null ? urlParams.get("failOpen") : null;
        if (failOpenProp == null || failOpenProp.trim().isEmpty()) {
            failOpenProp = System.getProperty("dadp.proxy.fail-open");
        }
        if (failOpenProp == null || failOpenProp.trim().isEmpty()) {
            failOpenProp = System.getenv("DADP_PROXY_FAIL_OPEN");
        }
        this.failOpen = failOpenProp == null || failOpenProp.trim().isEmpty() || 
                       Boolean.parseBoolean(failOpenProp);
        
        log.info("✅ Proxy 설정 로드 완료:");
        log.info("   - Hub URL: {}", this.hubUrl);
        log.info("   - Instance ID: {}", this.instanceId);
        log.info("   - Fail-open: {}", this.failOpen);
    }
    
    /**
     * 기본 생성자 (레거시 호환성)
     */
    private ProxyConfig() {
        this(null);
    }
    
    /**
     * 싱글톤 인스턴스 (레거시 호환성, 권장하지 않음)
     */
    public static ProxyConfig getInstance() {
        if (instance == null) {
            synchronized (ProxyConfig.class) {
                if (instance == null) {
                    instance = new ProxyConfig();
                }
            }
        }
        return instance;
    }
    
    public String getHubUrl() {
        return hubUrl;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public boolean isFailOpen() {
        return failOpen;
    }
}

