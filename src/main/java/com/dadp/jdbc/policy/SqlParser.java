package com.dadp.jdbc.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 파서
 * 
 * SQL 쿼리를 파싱하여 테이블명, 컬럼명, 파라미터 위치를 추출합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class SqlParser {
    
    private static final Logger log = LoggerFactory.getLogger(SqlParser.class);
    
    // INSERT 문 패턴: INSERT INTO table (col1, col2, ...) VALUES (?, ?, ...)
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    // UPDATE 문 패턴: UPDATE table SET col1 = ?, col2 = ? WHERE ...
    // WHERE 키워드 전까지 매칭 (대소문자 구분 없음)
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
        "UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE|$)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // SELECT 문 패턴: SELECT col1, col2, ... FROM table [alias]
    // FROM users u1_0 -> users 추출
    // 대소문자 구분 없이 FROM 키워드 전까지 매칭
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "SELECT\\s+(.*?)\\s+FROM\\s+(\\S+)(?:\\s+\\S+)?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * SQL 파싱 결과
     */
    public static class SqlParseResult {
        private String tableName;
        private String[] columns;
        private String sqlType; // INSERT, UPDATE, SELECT
        
        public String getTableName() {
            return tableName;
        }
        
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        
        public String[] getColumns() {
            return columns;
        }
        
        public void setColumns(String[] columns) {
            this.columns = columns;
        }
        
        public String getSqlType() {
            return sqlType;
        }
        
        public void setSqlType(String sqlType) {
            this.sqlType = sqlType;
        }
    }
    
    /**
     * SQL 파싱
     * 
     * @param sql SQL 쿼리
     * @return 파싱 결과
     */
    public SqlParseResult parse(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }
        
        String sqlUpper = sql.trim().toUpperCase();
        SqlParseResult result = new SqlParseResult();
        
        // INSERT 문 파싱
        if (sqlUpper.startsWith("INSERT")) {
            result = parseInsert(sql);
        }
        // UPDATE 문 파싱
        else if (sqlUpper.startsWith("UPDATE")) {
            result = parseUpdate(sql);
        }
        // SELECT 문 파싱
        else if (sqlUpper.startsWith("SELECT")) {
            result = parseSelect(sql);
        }
        
        if (result != null && result.getTableName() != null) {
            log.trace("🔍 SQL 파싱 완료: type={}, table={}, columns={}", 
                     result.getSqlType(), result.getTableName(), 
                     result.getColumns() != null ? String.join(", ", result.getColumns()) : "null");
        } else {
            log.debug("⚠️ SQL 파싱 실패: sql={}", sql);
        }
        
        return result;
    }
    
    /**
     * INSERT 문 파싱
     */
    private SqlParseResult parseInsert(String sql) {
        Matcher matcher = INSERT_PATTERN.matcher(sql);
        if (matcher.find()) {
            SqlParseResult result = new SqlParseResult();
            result.setSqlType("INSERT");
            result.setTableName(matcher.group(1));
            
            // 컬럼 목록 추출
            String columnsStr = matcher.group(2);
            String[] columns = columnsStr.split(",");
            for (int i = 0; i < columns.length; i++) {
                columns[i] = columns[i].trim();
            }
            result.setColumns(columns);
            
            return result;
        }
        return null;
    }
    
    /**
     * UPDATE 문 파싱
     */
    private SqlParseResult parseUpdate(String sql) {
        Matcher matcher = UPDATE_PATTERN.matcher(sql);
        if (matcher.find()) {
            SqlParseResult result = new SqlParseResult();
            result.setSqlType("UPDATE");
            result.setTableName(matcher.group(1));
            
            // SET 절의 컬럼 목록 추출
            String setClause = matcher.group(2).trim();
            // 콤마로 분리 (단, 괄호 안의 콤마는 제외)
            java.util.List<String> assignments = new java.util.ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < setClause.length(); i++) {
                char c = setClause.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 0) {
                    assignments.add(setClause.substring(start, i).trim());
                    start = i + 1;
                }
            }
            if (start < setClause.length()) {
                assignments.add(setClause.substring(start).trim());
            }
            
            String[] columns = new String[assignments.size()];
            for (int i = 0; i < assignments.size(); i++) {
                String assignment = assignments.get(i);
                // col = ? 또는 col=? 형식에서 컬럼명 추출
                int equalsIndex = assignment.indexOf('=');
                if (equalsIndex > 0) {
                    String columnName = assignment.substring(0, equalsIndex).trim();
                    // 테이블 별칭 제거 (table.col -> col)
                    int dotIndex = columnName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        columnName = columnName.substring(dotIndex + 1);
                    }
                    columns[i] = columnName;
                } else {
                    columns[i] = null;
                }
            }
            result.setColumns(columns);
            
            return result;
        }
        return null;
    }
    
    /**
     * SELECT 문 파싱
     */
    private SqlParseResult parseSelect(String sql) {
        Matcher matcher = SELECT_PATTERN.matcher(sql);
        if (matcher.find()) {
            SqlParseResult result = new SqlParseResult();
            result.setSqlType("SELECT");
            // FROM 절에서 테이블명 추출 (별칭 제거)
            // matcher.group(2)는 "users" (별칭은 이미 정규식에서 제외됨)
            String tableName = matcher.group(2).trim();
            result.setTableName(tableName);
            
            // SELECT 절의 컬럼 목록 추출
            String selectClause = matcher.group(1);
            String[] columns;
            if (selectClause.trim().equals("*")) {
                // * 인 경우는 나중에 ResultSetMetaData로 확인
                columns = new String[0];
            } else {
                columns = selectClause.split(",");
                for (int i = 0; i < columns.length; i++) {
                    String col = columns[i].trim();
                    // table.col 또는 col 형식 처리
                    int dotIndex = col.lastIndexOf('.');
                    if (dotIndex > 0) {
                        col = col.substring(dotIndex + 1);
                    }
                    // 별칭 처리 (AS alias)
                    int asIndex = col.toUpperCase().lastIndexOf(" AS ");
                    if (asIndex > 0) {
                        col = col.substring(asIndex + 4).trim();
                    }
                    columns[i] = col;
                }
            }
            result.setColumns(columns);
            
            return result;
        }
        return null;
    }
}

