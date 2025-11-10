package com.dadp.jdbc;

import com.dadp.jdbc.config.ProxyConfig;
import com.dadp.jdbc.crypto.HubCryptoAdapter;
import com.dadp.jdbc.mapping.MappingSyncService;
import com.dadp.jdbc.policy.PolicyResolver;
import com.dadp.jdbc.schema.SchemaSyncService;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DADP Proxy Connection
 * 
 * 실제 DB Connection을 래핑하여 PreparedStatement와 ResultSet을 가로채어
 * 암복호화 처리를 수행합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class DadpProxyConnection implements Connection {
    
    private static final Logger log = LoggerFactory.getLogger(DadpProxyConnection.class);
    
    private final Connection actualConnection;
    private final String originalUrl;
    private final ProxyConfig config;
    private volatile HubCryptoAdapter hubCryptoAdapter;
    private final SchemaSyncService schemaSyncService;
    private final MappingSyncService mappingSyncService;
    private final PolicyResolver policyResolver;
    private boolean closed = false;
    
    // Proxy Instance별 스키마 동기화/매핑 로드 여부 (static으로 공유하여 중복 방지)
    private static final ConcurrentHashMap<String, Boolean> schemaSyncedMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> mappingsLoadedMap = new ConcurrentHashMap<>();
    
    // Proxy Instance별 매핑 폴링 스케줄러 (static으로 공유하여 중복 방지)
    private static final ConcurrentHashMap<String, ScheduledExecutorService> mappingPollingSchedulers = new ConcurrentHashMap<>();
    
    public DadpProxyConnection(Connection actualConnection, String originalUrl) {
        this(actualConnection, originalUrl, null);
    }
    
    public DadpProxyConnection(Connection actualConnection, String originalUrl, Map<String, String> urlParams) {
        this.actualConnection = actualConnection;
        this.originalUrl = originalUrl;
        // JDBC URL 파라미터가 있으면 사용, 없으면 싱글톤 인스턴스 사용
        this.config = urlParams != null ? new ProxyConfig(urlParams) : ProxyConfig.getInstance();
        
        // Hub 암복호화 어댑터 초기화 (지연 초기화 또는 Fail-open 모드)
        try {
            this.hubCryptoAdapter = new HubCryptoAdapter(config.getHubUrl(), config.isFailOpen());
            log.info("✅ Hub 암복호화 어댑터 초기화 완료: hubUrl={}, failOpen={}", config.getHubUrl(), config.isFailOpen());
        } catch (Exception e) {
            log.error("❌ Hub 암복호화 어댑터 초기화 실패: {}", e.getMessage());
            if (config.isFailOpen()) {
                // Fail-open 모드: 어댑터를 null로 두고 나중에 재시도
                log.warn("⚠️ Fail-open 모드: Hub 연결 실패해도 계속 진행. 암복호화는 나중에 재시도됩니다.");
                this.hubCryptoAdapter = null;
            } else {
                // Fail-closed 모드: 예외 발생
                throw new RuntimeException("Hub 연결 실패 (Fail-closed 모드)", e);
            }
        }
        
        // 스키마 동기화 서비스 초기화
        this.schemaSyncService = new SchemaSyncService(config.getHubUrl(), config.getInstanceId());
        
        // PolicyResolver 초기화 (Connection별로 인스턴스 생성)
        this.policyResolver = new PolicyResolver();
        
        // 매핑 동기화 서비스 초기화
        this.mappingSyncService = new MappingSyncService(config.getHubUrl(), config.getInstanceId(), policyResolver);
        
        // Connection 생성 시 스키마 메타데이터 수집 및 Hub로 전송 (비동기)
        syncSchemaMetadata();
        
        // Connection 생성 시 Hub에서 매핑 정보 로드 (비동기)
        loadMappingsFromHub();
        
        // 폴링 시작 (이미 로드되었어도 폴링은 시작되어야 함)
        startMappingPolling(config.getInstanceId());
        
        log.debug("✅ DADP Proxy Connection 생성 완료");
    }
    
    /**
     * 스키마 메타데이터를 Hub로 동기화 (비동기)
     * Proxy Instance별로 한 번만 실행됩니다.
     */
    private void syncSchemaMetadata() {
        String instanceId = config.getInstanceId();
        
        // 이미 동기화된 경우 스킵
        if (schemaSyncedMap.getOrDefault(instanceId, false)) {
            return;
        }
        
        // 동기화 시작 표시 (동시 실행 방지)
        if (schemaSyncedMap.putIfAbsent(instanceId, true) != null) {
            return; // 다른 스레드가 이미 시작함
        }
        
        // 별도 스레드에서 비동기로 실행 (Connection 생성 지연 방지)
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Connection 완전 초기화 대기
                schemaSyncService.syncSchemaToHub(actualConnection);
            } catch (Exception e) {
                log.warn("⚠️ 스키마 메타데이터 동기화 실패 (무시): {}", e.getMessage());
                // 동기화 실패 시 플래그 제거하여 재시도 가능하도록
                schemaSyncedMap.remove(instanceId);
            }
        }, "dadp-proxy-schema-sync-" + instanceId).start();
    }
    
    /**
     * Hub에서 정책 매핑 정보를 로드 (비동기)
     * Proxy Instance별로 한 번만 실행되고, 이후 주기적으로 폴링합니다.
     */
    private void loadMappingsFromHub() {
        String instanceId = config.getInstanceId();
        
        // 이미 로드된 경우 스킵 (폴링은 계속 진행)
        if (mappingsLoadedMap.getOrDefault(instanceId, false)) {
            return;
        }
        
        // 로드 시작 표시 (동시 실행 방지)
        if (mappingsLoadedMap.putIfAbsent(instanceId, true) != null) {
            return; // 다른 스레드가 이미 시작함
        }
        
        // 첫 로드 실행
        new Thread(() -> {
            try {
                Thread.sleep(1500); // 스키마 동기화 후 실행
                int count = mappingSyncService.loadMappingsFromHub();
                // 초기 로드 완료는 INFO 레벨로 로그 출력 (초기화 확인용)
                log.info("✅ 정책 매핑 정보 초기 로드 완료: {}개 매핑", count);
            } catch (Exception e) {
                log.warn("⚠️ 정책 매핑 정보 로드 실패 (무시): {}", e.getMessage());
                // 로드 실패 시 플래그 제거하여 재시도 가능하도록
                mappingsLoadedMap.remove(instanceId);
            }
        }, "dadp-proxy-mapping-load-" + instanceId).start();
    }
    
    /**
     * 주기적으로 Hub에서 매핑 정보를 폴링
     * Proxy Instance별로 한 번만 스케줄러가 시작됩니다.
     */
    private void startMappingPolling(String instanceId) {
        // 이미 스케줄러가 있는 경우 스킵
        if (mappingPollingSchedulers.containsKey(instanceId)) {
            return;
        }
        
        // 스케줄러 생성 및 시작
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dadp-proxy-mapping-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        
        // 기존 스케줄러가 없을 때만 추가
        ScheduledExecutorService existing = mappingPollingSchedulers.putIfAbsent(instanceId, scheduler);
        if (existing != null) {
            // 다른 스레드가 이미 생성했으므로 새로 만든 스케줄러 종료
            scheduler.shutdown();
            return;
        }
        
        // 초기 로드 후 즉시 첫 번째 변경사항 확인 (초기 지연 0초)
        // 이후 30초마다 변경사항 확인 (경량 요청)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 변경사항 확인 (경량 요청)
                boolean hasChange = mappingSyncService.checkMappingChange();
                if (hasChange) {
                    // 변경사항이 있으면 전체 매핑 로드
                    int count = mappingSyncService.loadMappingsFromHub();
                    log.info("🔄 정책 매핑 변경사항 반영 완료: {}개 매핑", count);
                } else {
                    log.trace("⏭️ 정책 매핑 변경사항 없음");
                }
            } catch (Exception e) {
                log.warn("⚠️ 정책 매핑 변경사항 확인 실패: {}", e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS); // 초기 지연 0초 (즉시 실행), 이후 30초마다
        
        log.info("✅ 정책 매핑 변경사항 확인 시작: proxyInstanceId={}, 주기=30초", instanceId);
    }
    
    /**
     * PolicyResolver 반환 (PreparedStatement에서 사용)
     */
    public PolicyResolver getPolicyResolver() {
        return policyResolver;
    }
    
    /**
     * 매핑 정보 강제 새로고침 (Hub에서 변경 알림 받을 때 사용)
     */
    public void refreshMappings() {
        new Thread(() -> {
            try {
                int count = mappingSyncService.loadMappingsFromHub();
                log.info("🔄 정책 매핑 정보 강제 새로고침 완료: {}개 매핑", count);
            } catch (Exception e) {
                log.warn("⚠️ 정책 매핑 정보 새로고침 실패: {}", e.getMessage());
            }
        }, "dadp-proxy-mapping-refresh").start();
    }
    
    public HubCryptoAdapter getHubCryptoAdapter() {
        // 지연 초기화: 아직 초기화되지 않았으면 재시도
        if (hubCryptoAdapter == null && config.isFailOpen()) {
            try {
                this.hubCryptoAdapter = new HubCryptoAdapter(config.getHubUrl(), config.isFailOpen());
                log.info("✅ Hub 암복호화 어댑터 지연 초기화 완료: hubUrl={}", config.getHubUrl());
            } catch (Exception e) {
                log.warn("⚠️ Hub 암복호화 어댑터 지연 초기화 실패 (무시): {}", e.getMessage());
            }
        }
        return hubCryptoAdapter;
    }
    
    public ProxyConfig getConfig() {
        return config;
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        return actualConnection.createStatement();
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        log.debug("🔍 PreparedStatement 생성: {}", sql);
        // TODO: PreparedStatement 래핑하여 암복호화 처리
        PreparedStatement actualPs = actualConnection.prepareStatement(sql);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return actualConnection.prepareCall(sql);
    }
    
    @Override
    public String nativeSQL(String sql) throws SQLException {
        return actualConnection.nativeSQL(sql);
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        actualConnection.setAutoCommit(autoCommit);
    }
    
    @Override
    public boolean getAutoCommit() throws SQLException {
        return actualConnection.getAutoCommit();
    }
    
    @Override
    public void commit() throws SQLException {
        actualConnection.commit();
    }
    
    @Override
    public void rollback() throws SQLException {
        actualConnection.rollback();
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            actualConnection.close();
            closed = true;
            // TRACE 레벨로 변경: 연결 풀에서 여러 Connection이 종료될 때 로그 스팸 방지
            log.trace("✅ DADP Proxy Connection 종료");
        }
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed || actualConnection.isClosed();
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return actualConnection.getMetaData();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        actualConnection.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        return actualConnection.isReadOnly();
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        actualConnection.setCatalog(catalog);
    }
    
    @Override
    public String getCatalog() throws SQLException {
        return actualConnection.getCatalog();
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        actualConnection.setTransactionIsolation(level);
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        return actualConnection.getTransactionIsolation();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return actualConnection.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        actualConnection.clearWarnings();
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return actualConnection.createStatement(resultSetType, resultSetConcurrency);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        PreparedStatement actualPs = actualConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return actualConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return actualConnection.getTypeMap();
    }
    
    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        actualConnection.setTypeMap(map);
    }
    
    @Override
    public void setHoldability(int holdability) throws SQLException {
        actualConnection.setHoldability(holdability);
    }
    
    @Override
    public int getHoldability() throws SQLException {
        return actualConnection.getHoldability();
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        return actualConnection.setSavepoint();
    }
    
    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return actualConnection.setSavepoint(name);
    }
    
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        actualConnection.rollback(savepoint);
    }
    
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        actualConnection.releaseSavepoint(savepoint);
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return actualConnection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        PreparedStatement actualPs = actualConnection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return actualConnection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        PreparedStatement actualPs = actualConnection.prepareStatement(sql, autoGeneratedKeys);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        PreparedStatement actualPs = actualConnection.prepareStatement(sql, columnIndexes);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        PreparedStatement actualPs = actualConnection.prepareStatement(sql, columnNames);
        return new DadpProxyPreparedStatement(actualPs, sql, this);
    }
    
    @Override
    public Clob createClob() throws SQLException {
        return actualConnection.createClob();
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        return actualConnection.createBlob();
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        return actualConnection.createNClob();
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        return actualConnection.createSQLXML();
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        return actualConnection.isValid(timeout);
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        actualConnection.setClientInfo(name, value);
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        actualConnection.setClientInfo(properties);
    }
    
    @Override
    public String getClientInfo(String name) throws SQLException {
        return actualConnection.getClientInfo(name);
    }
    
    @Override
    public Properties getClientInfo() throws SQLException {
        return actualConnection.getClientInfo();
    }
    
    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return actualConnection.createArrayOf(typeName, elements);
    }
    
    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return actualConnection.createStruct(typeName, attributes);
    }
    
    @Override
    public void setSchema(String schema) throws SQLException {
        actualConnection.setSchema(schema);
    }
    
    @Override
    public String getSchema() throws SQLException {
        return actualConnection.getSchema();
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        actualConnection.abort(executor);
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        actualConnection.setNetworkTimeout(executor, milliseconds);
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        return actualConnection.getNetworkTimeout();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return actualConnection.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || actualConnection.isWrapperFor(iface);
    }
    
    // 내부 메서드: 실제 Connection 반환
    Connection getActualConnection() {
        return actualConnection;
    }
}

