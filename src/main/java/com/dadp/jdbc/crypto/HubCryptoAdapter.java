package com.dadp.jdbc.crypto;

import com.dadp.hub.crypto.HubCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hub 암복호화 어댑터
 * 
 * dadp-hub-crypto-lib의 HubCryptoService를 래핑하여 사용합니다.
 * Fail-open/Fail-closed 모드를 지원합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class HubCryptoAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(HubCryptoAdapter.class);
    
    private final HubCryptoService hubCryptoService;
    private final boolean failOpen;
    private volatile boolean hubAvailable = true; // Hub 연결 가능 여부
    
    public HubCryptoAdapter(String hubUrl, boolean failOpen) {
        this.failOpen = failOpen;
        // HubCryptoService는 Spring Bean이 아니므로 createInstance 사용
        // 초기화 시에는 연결 테스트를 하지 않음 (지연 초기화)
        this.hubCryptoService = HubCryptoService.createInstance(hubUrl, 5000, true);
        log.info("✅ Hub 암복호화 어댑터 생성: hubUrl={}, failOpen={}", hubUrl, failOpen);
    }
    
    /**
     * Hub 연결 가능 여부 확인
     */
    public boolean isHubAvailable() {
        return hubAvailable;
    }
    
    /**
     * 암호화
     * 
     * Wrapper는 성공/실패만 판단하며, 메시지는 Engine에 의존합니다.
     * 
     * @param data 평문 데이터
     * @param policyName 정책명
     * @return 암호화된 데이터 (실패 시 failOpen 모드에 따라 원본 반환 또는 예외)
     */
    public String encrypt(String data, String policyName) {
        if (data == null) {
            return null;
        }
        
        try {
            log.debug("🔐 암호화 요청: policy={}, dataLength={}", policyName, data != null ? data.length() : 0);
            String encrypted = hubCryptoService.encrypt(data, policyName);
            log.debug("✅ 암호화 완료");
            hubAvailable = true; // 성공 시 연결 가능으로 표시
            return encrypted;
        } catch (Exception e) {
            // 실패 처리: 메시지 파싱 없이 단순히 성공/실패만 판단
            log.error("❌ 암호화 실패: {}", e.getMessage());
            hubAvailable = false; // 실패 시 연결 불가로 표시
            
            if (failOpen) {
                // Fail-open 모드: 원본 데이터 반환
                log.warn("⚠️ Fail-open 모드: 원본 데이터 반환");
                return data;
            } else {
                // Fail-closed 모드: 예외 발생
                throw new RuntimeException("암호화 실패 (Fail-closed 모드)", e);
            }
        }
    }
    
    /**
     * 복호화
     * 
     * Proxy에서는 암호화 여부를 판단하지 않고, 정책 매핑이 있으면 무조건 Hub에 요청합니다.
     * Hub/Engine에서 암호화 여부를 판단하고 처리합니다.
     * Wrapper는 성공/실패만 판단하며, 메시지는 Engine에 의존합니다.
     * 
     * @param encryptedData 암호화된 데이터 (또는 일반 텍스트)
     * @return 복호화된 데이터 (실패 시 failOpen 모드에 따라 원본 반환 또는 예외)
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null) {
            return null;
        }
        
        try {
            log.debug("🔓 복호화 요청: dataLength={}", encryptedData != null ? encryptedData.length() : 0);
            // Hub/Engine에서 암호화 여부 판단 및 처리
            String decrypted = hubCryptoService.decrypt(encryptedData);
            
            // null 반환 시 "데이터가 암호화되지 않았습니다" 의미 (원본 데이터 반환)
            if (decrypted == null) {
                log.debug("데이터가 암호화되지 않았습니다 - 원본 데이터 반환");
                return encryptedData;
            }
            
            log.debug("✅ 복호화 완료");
            hubAvailable = true; // 성공 시 연결 가능으로 표시
            return decrypted;
        } catch (Exception e) {
            // 실제 에러만 처리 (HubCryptoService에서 "데이터가 암호화되지 않았습니다"는 null 반환하므로 여기까지 오지 않음)
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            log.error("❌ 복호화 실패: {}", errorMessage);
            hubAvailable = false; // 실패 시 연결 불가로 표시
            
            if (failOpen) {
                // Fail-open 모드: 원본 데이터 반환
                log.warn("⚠️ Fail-open 모드: 원본 데이터 반환");
                return encryptedData;
            } else {
                // Fail-closed 모드: 예외 발생
                throw new RuntimeException("복호화 실패 (Fail-closed 모드)", e);
            }
        }
    }
    
    /**
     * 데이터가 암호화된 형태인지 확인
     * 
     * @param data 확인할 데이터
     * @return 암호화된 데이터인지 여부
     */
    public boolean isEncryptedData(String data) {
        return hubCryptoService.isEncryptedData(data);
    }
}

