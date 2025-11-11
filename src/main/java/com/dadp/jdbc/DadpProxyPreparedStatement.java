package com.dadp.jdbc;

import com.dadp.jdbc.crypto.HubCryptoAdapter;
import com.dadp.jdbc.policy.PolicyResolver;
import com.dadp.jdbc.policy.SqlParser;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DADP Proxy PreparedStatement
 * 
 * PreparedStatement를 래핑하여 파라미터 바인딩 시 암호화 처리를 수행합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class DadpProxyPreparedStatement implements PreparedStatement {
    
    private static final Logger log = LoggerFactory.getLogger(DadpProxyPreparedStatement.class);
    
    private final PreparedStatement actualPreparedStatement;
    private final String sql;
    private final DadpProxyConnection proxyConnection;
    private final SqlParser.SqlParseResult sqlParseResult;
    private final Map<Integer, String> parameterToColumnMap; // parameterIndex -> columnName
    
    public DadpProxyPreparedStatement(PreparedStatement actualPs, String sql, DadpProxyConnection proxyConnection) {
        this.actualPreparedStatement = actualPs;
        this.sql = sql;
        this.proxyConnection = proxyConnection;
        
        // SQL 파싱
        SqlParser sqlParser = new SqlParser();
        this.sqlParseResult = sqlParser.parse(sql);
        
        // 파라미터 인덱스와 컬럼명 매핑 생성
        this.parameterToColumnMap = buildParameterMapping(sqlParseResult);
        
            if (sqlParseResult != null && !parameterToColumnMap.isEmpty()) {
                log.trace("🔍 DADP Proxy PreparedStatement 생성: {} ({}개 파라미터 매핑)", sql, parameterToColumnMap.size());
            } else {
                log.trace("🔍 DADP Proxy PreparedStatement 생성: {}", sql);
            }
    }
    
    /**
     * SQL 파싱 결과로부터 파라미터 인덱스와 컬럼명 매핑 생성
     * INSERT/UPDATE: SET 절의 컬럼만 매핑
     * SELECT: WHERE 절의 파라미터도 매핑
     */
    private Map<Integer, String> buildParameterMapping(SqlParser.SqlParseResult parseResult) {
        Map<Integer, String> mapping = new HashMap<>();
        
        if (parseResult == null) {
            return mapping;
        }
        
        // INSERT/UPDATE: SET 절 또는 VALUES 절의 컬럼 매핑
        if ("INSERT".equals(parseResult.getSqlType()) || "UPDATE".equals(parseResult.getSqlType())) {
            if (parseResult.getColumns() != null) {
                String[] columns = parseResult.getColumns();
                for (int i = 0; i < columns.length; i++) {
                    // null이 아닌 컬럼명만 매핑
                    if (columns[i] != null && !columns[i].trim().isEmpty()) {
                        // 파라미터 인덱스는 1부터 시작
                        mapping.put(i + 1, columns[i].trim());
                    }
                }
            }
        }
        // SELECT: WHERE 절의 파라미터 매핑
        else if ("SELECT".equals(parseResult.getSqlType())) {
            // WHERE 절에서 파라미터와 컬럼 매핑 추출
            parseWhereClauseParameters(sql, parseResult.getTableName(), mapping);
        }
        
        return mapping;
    }
    
    /**
     * WHERE 절에서 파라미터와 컬럼명 매핑 추출
     * 예: WHERE u1_0.phone like ? -> parameterIndex 1 -> phone
     */
    private void parseWhereClauseParameters(String sql, String tableName, Map<Integer, String> mapping) {
        if (sql == null || tableName == null) {
            return;
        }
        
        // WHERE 절 찾기
        int whereIndex = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIndex < 0) {
            return;
        }
        
        // WHERE 절 이전의 ? 개수 계산 (INSERT/UPDATE의 VALUES/SET 절 파라미터)
        String beforeWhere = sql.substring(0, whereIndex);
        int beforeWhereParamCount = countParameters(beforeWhere);
        
        String whereClause = sql.substring(whereIndex + 7); // " WHERE " 길이
        
        // WHERE 절에서 파라미터 위치와 컬럼명 매핑
        // 패턴: table.col like ?, table.col = ?, table.col > ? 등
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:\\w+\\.)?(\\w+)\\s*(?:like|=|!=|<>|>|<|>=|<=|in|not\\s+in)\\s*\\?",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(whereClause);
        
        while (matcher.find()) {
            String columnName = matcher.group(1);
            // WHERE 절의 ? 위치 찾기
            int questionMarkIndex = matcher.end() - 1; // ? 위치
            // WHERE 절 내에서 이 ? 이전의 ? 개수 계산
            String beforeQuestionMark = whereClause.substring(0, questionMarkIndex);
            int localParamIndex = countParameters(beforeQuestionMark);
            // 전체 파라미터 인덱스 = WHERE 절 이전 파라미터 개수 + WHERE 절 내 파라미터 인덱스
            int globalParamIndex = beforeWhereParamCount + localParamIndex + 1; // 1-based
            
            if (!mapping.containsKey(globalParamIndex)) {
                mapping.put(globalParamIndex, columnName);
                log.trace("🔍 WHERE 절 파라미터 매핑: parameterIndex={} -> column={}", globalParamIndex, columnName);
            }
        }
    }
    
    /**
     * SQL 문자열에서 ? 파라미터 개수 계산
     */
    private int countParameters(String sql) {
        if (sql == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public ResultSet executeQuery() throws SQLException {
        // TODO: 실행 전 SQL 파싱 및 정책 확인
        ResultSet actualRs = actualPreparedStatement.executeQuery();
        // TODO: ResultSet 래핑하여 복호화 처리
        return new DadpProxyResultSet(actualRs, sql, proxyConnection);
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        // TODO: 실행 전 SQL 파싱 및 암호화 처리
        return actualPreparedStatement.executeUpdate();
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        actualPreparedStatement.setNull(parameterIndex, sqlType);
    }
    
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        actualPreparedStatement.setBoolean(parameterIndex, x);
    }
    
    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        actualPreparedStatement.setByte(parameterIndex, x);
    }
    
    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        actualPreparedStatement.setShort(parameterIndex, x);
    }
    
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        actualPreparedStatement.setInt(parameterIndex, x);
    }
    
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        actualPreparedStatement.setLong(parameterIndex, x);
    }
    
    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        actualPreparedStatement.setFloat(parameterIndex, x);
    }
    
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        actualPreparedStatement.setDouble(parameterIndex, x);
    }
    
    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        actualPreparedStatement.setBigDecimal(parameterIndex, x);
    }
    
    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        // 암호화 대상 확인
        if (x != null && sqlParseResult != null) {
            String columnName = parameterToColumnMap.get(parameterIndex);
            String tableName = sqlParseResult.getTableName();
            
            if (columnName == null || tableName == null) {
                log.warn("⚠️ 테이블명 또는 컬럼명 없음: 암호화 대상 확인 불가, tableName={}, columnName={}, parameterIndex={}", 
                        tableName, columnName, parameterIndex);
            } else {
                // SELECT 문의 WHERE 절 파라미터는 암호화하지 않음
                // 이유: 부분 암호화된 데이터 검색을 위해 평문으로 검색해야 함
                // 예: DB에 "3422::ENC::..." 형태로 저장된 경우, "3422"로 검색해야 함
                if ("SELECT".equals(sqlParseResult.getSqlType())) {
                    log.trace("🔓 SELECT WHERE 절 파라미터: 암호화하지 않음 (부분 암호화 검색 지원), {}.{}", tableName, columnName);
                    actualPreparedStatement.setString(parameterIndex, x);
                    return;
                }
                
                // PolicyResolver에서 정책 확인 (메모리 캐시에서 조회)
                PolicyResolver policyResolver = proxyConnection.getPolicyResolver();
                String policyName = policyResolver.resolvePolicy(tableName, columnName);
                
                if (policyName != null) {
                    // 암호화 대상: Hub를 통해 암호화
                    HubCryptoAdapter adapter = proxyConnection.getHubCryptoAdapter();
                    if (adapter != null) {
                        try {
                            String encrypted = adapter.encrypt(x, policyName);
                            actualPreparedStatement.setString(parameterIndex, encrypted);
                            log.debug("🔐 암호화 완료: {}.{} → {} (정책: {})", tableName, columnName, 
                                     encrypted != null && encrypted.length() > 20 ? encrypted.substring(0, 20) + "..." : encrypted, 
                                     policyName);
                            return;
                        } catch (Exception e) {
                            log.error("❌ 암호화 실패: {}.{} (정책: {}), 원본 데이터로 저장", 
                                     tableName, columnName, policyName);
                            // 암호화 실패 시 원본 데이터로 저장 (Fail-open)
                        }
                    } else {
                        log.warn("⚠️ Hub 어댑터가 초기화되지 않았습니다: {}.{} (정책: {}), 원본 데이터로 저장", 
                                tableName, columnName, policyName);
                    }
                } else {
                    log.trace("🔓 암호화 대상 아님: {}.{}", tableName, columnName);
                }
            }
        } else if (x != null && sqlParseResult == null) {
            log.warn("⚠️ SQL 파싱 결과 없음: 암호화 대상 확인 불가, parameterIndex={}", parameterIndex);
        }
        
        // 암호화 대상이 아니거나 암호화 실패 시 원본 데이터 그대로 저장
        actualPreparedStatement.setString(parameterIndex, x);
    }
    
    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        actualPreparedStatement.setBytes(parameterIndex, x);
    }
    
    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        actualPreparedStatement.setDate(parameterIndex, x);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        actualPreparedStatement.setTime(parameterIndex, x);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        actualPreparedStatement.setTimestamp(parameterIndex, x);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        actualPreparedStatement.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        actualPreparedStatement.setUnicodeStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        actualPreparedStatement.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void clearParameters() throws SQLException {
        actualPreparedStatement.clearParameters();
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        // TODO: Object 타입인 경우 String으로 변환하여 암호화 처리
        actualPreparedStatement.setObject(parameterIndex, x, targetSqlType);
    }
    
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        // TODO: Object 타입인 경우 String으로 변환하여 암호화 처리
        actualPreparedStatement.setObject(parameterIndex, x);
    }
    
    @Override
    public boolean execute() throws SQLException {
        // TODO: 실행 전 SQL 파싱 및 암호화 처리
        return actualPreparedStatement.execute();
    }
    
    @Override
    public void addBatch() throws SQLException {
        actualPreparedStatement.addBatch();
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        actualPreparedStatement.setCharacterStream(parameterIndex, reader, length);
    }
    
    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        actualPreparedStatement.setRef(parameterIndex, x);
    }
    
    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        actualPreparedStatement.setBlob(parameterIndex, x);
    }
    
    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        actualPreparedStatement.setClob(parameterIndex, x);
    }
    
    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        actualPreparedStatement.setArray(parameterIndex, x);
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return actualPreparedStatement.getMetaData();
    }
    
    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        actualPreparedStatement.setDate(parameterIndex, x, cal);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        actualPreparedStatement.setTime(parameterIndex, x, cal);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        actualPreparedStatement.setTimestamp(parameterIndex, x, cal);
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        actualPreparedStatement.setNull(parameterIndex, sqlType, typeName);
    }
    
    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        actualPreparedStatement.setURL(parameterIndex, x);
    }
    
    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return actualPreparedStatement.getParameterMetaData();
    }
    
    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        actualPreparedStatement.setRowId(parameterIndex, x);
    }
    
    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        // TODO: 암호화 처리 (setString과 동일)
        actualPreparedStatement.setNString(parameterIndex, value);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        actualPreparedStatement.setNCharacterStream(parameterIndex, value, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        actualPreparedStatement.setNClob(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        actualPreparedStatement.setClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        actualPreparedStatement.setBlob(parameterIndex, inputStream, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        actualPreparedStatement.setNClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        actualPreparedStatement.setSQLXML(parameterIndex, xmlObject);
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        actualPreparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        actualPreparedStatement.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        actualPreparedStatement.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        actualPreparedStatement.setCharacterStream(parameterIndex, reader, length);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        actualPreparedStatement.setAsciiStream(parameterIndex, x);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        actualPreparedStatement.setBinaryStream(parameterIndex, x);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        actualPreparedStatement.setCharacterStream(parameterIndex, reader);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        actualPreparedStatement.setNCharacterStream(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        actualPreparedStatement.setClob(parameterIndex, reader);
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        actualPreparedStatement.setBlob(parameterIndex, inputStream);
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        actualPreparedStatement.setNClob(parameterIndex, reader);
    }
    
    // Statement 인터페이스 메서드들
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return actualPreparedStatement.executeQuery(sql);
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        return actualPreparedStatement.executeUpdate(sql);
    }
    
    @Override
    public void close() throws SQLException {
        actualPreparedStatement.close();
    }
    
    @Override
    public int getMaxFieldSize() throws SQLException {
        return actualPreparedStatement.getMaxFieldSize();
    }
    
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        actualPreparedStatement.setMaxFieldSize(max);
    }
    
    @Override
    public int getMaxRows() throws SQLException {
        return actualPreparedStatement.getMaxRows();
    }
    
    @Override
    public void setMaxRows(int max) throws SQLException {
        actualPreparedStatement.setMaxRows(max);
    }
    
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        actualPreparedStatement.setEscapeProcessing(enable);
    }
    
    @Override
    public int getQueryTimeout() throws SQLException {
        return actualPreparedStatement.getQueryTimeout();
    }
    
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        actualPreparedStatement.setQueryTimeout(seconds);
    }
    
    @Override
    public void cancel() throws SQLException {
        actualPreparedStatement.cancel();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return actualPreparedStatement.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        actualPreparedStatement.clearWarnings();
    }
    
    @Override
    public void setCursorName(String name) throws SQLException {
        actualPreparedStatement.setCursorName(name);
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        return actualPreparedStatement.execute(sql);
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        ResultSet actualRs = actualPreparedStatement.getResultSet();
        if (actualRs != null) {
            return new DadpProxyResultSet(actualRs, sql, proxyConnection);
        }
        return null;
    }
    
    @Override
    public int getUpdateCount() throws SQLException {
        return actualPreparedStatement.getUpdateCount();
    }
    
    @Override
    public boolean getMoreResults() throws SQLException {
        return actualPreparedStatement.getMoreResults();
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        actualPreparedStatement.setFetchDirection(direction);
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        return actualPreparedStatement.getFetchDirection();
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        actualPreparedStatement.setFetchSize(rows);
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        return actualPreparedStatement.getFetchSize();
    }
    
    @Override
    public int getResultSetConcurrency() throws SQLException {
        return actualPreparedStatement.getResultSetConcurrency();
    }
    
    @Override
    public int getResultSetType() throws SQLException {
        return actualPreparedStatement.getResultSetType();
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
        actualPreparedStatement.addBatch(sql);
    }
    
    @Override
    public void clearBatch() throws SQLException {
        actualPreparedStatement.clearBatch();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        return actualPreparedStatement.executeBatch();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return proxyConnection;
    }
    
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return actualPreparedStatement.getMoreResults(current);
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        ResultSet actualRs = actualPreparedStatement.getGeneratedKeys();
        if (actualRs != null) {
            return new DadpProxyResultSet(actualRs, sql, proxyConnection);
        }
        return null;
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return actualPreparedStatement.executeUpdate(sql, autoGeneratedKeys);
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return actualPreparedStatement.executeUpdate(sql, columnIndexes);
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return actualPreparedStatement.executeUpdate(sql, columnNames);
    }
    
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return actualPreparedStatement.execute(sql, autoGeneratedKeys);
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return actualPreparedStatement.execute(sql, columnIndexes);
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return actualPreparedStatement.execute(sql, columnNames);
    }
    
    @Override
    public int getResultSetHoldability() throws SQLException {
        return actualPreparedStatement.getResultSetHoldability();
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return actualPreparedStatement.isClosed();
    }
    
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        actualPreparedStatement.setPoolable(poolable);
    }
    
    @Override
    public boolean isPoolable() throws SQLException {
        return actualPreparedStatement.isPoolable();
    }
    
    @Override
    public void closeOnCompletion() throws SQLException {
        actualPreparedStatement.closeOnCompletion();
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return actualPreparedStatement.isCloseOnCompletion();
    }
    
    @Override
    public long getLargeUpdateCount() throws SQLException {
        return actualPreparedStatement.getLargeUpdateCount();
    }
    
    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        actualPreparedStatement.setLargeMaxRows(max);
    }
    
    @Override
    public long getLargeMaxRows() throws SQLException {
        return actualPreparedStatement.getLargeMaxRows();
    }
    
    @Override
    public long[] executeLargeBatch() throws SQLException {
        return actualPreparedStatement.executeLargeBatch();
    }
    
    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        return actualPreparedStatement.executeLargeUpdate(sql);
    }
    
    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return actualPreparedStatement.executeLargeUpdate(sql, autoGeneratedKeys);
    }
    
    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return actualPreparedStatement.executeLargeUpdate(sql, columnIndexes);
    }
    
    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return actualPreparedStatement.executeLargeUpdate(sql, columnNames);
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return actualPreparedStatement.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || actualPreparedStatement.isWrapperFor(iface);
    }
}

