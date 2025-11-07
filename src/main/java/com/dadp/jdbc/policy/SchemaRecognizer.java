package com.dadp.jdbc.policy;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 스키마 인식기
 * 
 * DB 메타데이터를 조회하여 테이블/컬럼 정보를 수집합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.0
 * @since 2025-11-07
 */
public class SchemaRecognizer {
    
    private static final Logger log = LoggerFactory.getLogger(SchemaRecognizer.class);
    
    /**
     * 스키마 메타데이터 수집
     * 
     * @param connection DB 연결
     * @return 스키마 메타데이터 목록
     */
    public List<SchemaMetadata> collectSchemaMetadata(Connection connection) throws SQLException {
        List<SchemaMetadata> schemas = new ArrayList<>();
        
        // 시스템 스키마 제외 목록 (MySQL, PostgreSQL 등 공통)
        final String[] EXCLUDED_SCHEMAS = {
            "information_schema", "performance_schema", "sys", "mysql", 
            "pg_catalog", "pg_toast", "pg_temp_1", "pg_toast_temp_1"
        };
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = connection.getCatalog();
            
            log.trace("🔍 스키마 메타데이터 수집 시작: database={}", databaseName);
            
            // 현재 데이터베이스의 테이블만 조회 (시스템 스키마 제외)
            try (ResultSet tables = metaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableSchema = tables.getString("TABLE_SCHEM");
                    
                    // 시스템 스키마 제외
                    if (tableSchema != null) {
                        String lowerSchema = tableSchema.toLowerCase();
                        boolean isExcluded = false;
                        for (String excluded : EXCLUDED_SCHEMAS) {
                            if (lowerSchema.equals(excluded)) {
                                isExcluded = true;
                                log.trace("⏭️ 시스템 스키마 제외: {}.{}", tableSchema, tableName);
                                break;
                            }
                        }
                        if (isExcluded) {
                            continue;
                        }
                    }
                    
                    log.trace("📋 테이블 발견: {}.{}", tableSchema, tableName);
                    
                    // 컬럼 정보 조회
                    try (ResultSet columns = metaData.getColumns(databaseName, tableSchema, tableName, "%")) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            String columnType = columns.getString("TYPE_NAME");
                            String columnDefault = columns.getString("COLUMN_DEF");
                            String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                            
                            // 암복호화 대상에서 제외할 컬럼 필터링
                            if (shouldExcludeColumn(columnName, columnType, columnDefault, isAutoIncrement)) {
                                log.trace("   ⏭️ 제외: {} ({}) - 암복호화 대상 아님", columnName, columnType);
                                continue;
                            }
                            
                            SchemaMetadata schema = new SchemaMetadata();
                            schema.setDatabaseName(databaseName);
                            schema.setTableName(tableName);
                            schema.setColumnName(columnName);
                            schema.setColumnType(columnType);
                            schema.setIsNullable("YES".equals(columns.getString("IS_NULLABLE")));
                            schema.setColumnDefault(columnDefault);
                            
                            schemas.add(schema);
                            
                            log.trace("   └─ 컬럼: {} ({})", schema.getColumnName(), schema.getColumnType());
                        }
                    }
                }
            }
            
            log.trace("✅ 스키마 메타데이터 수집 완료: {}개 컬럼", schemas.size());
            
        } catch (SQLException e) {
            log.error("❌ 스키마 메타데이터 수집 실패: {}", e.getMessage(), e);
            throw e;
        }
        
        return schemas;
    }
    
    /**
     * 암복호화 대상에서 제외할 컬럼인지 확인
     * 
     * @param columnName 컬럼명
     * @param columnType 컬럼 타입
     * @param columnDefault 기본값
     * @param isAutoIncrement 자동 증가 여부
     * @return 제외 여부 (true: 제외, false: 포함)
     */
    private boolean shouldExcludeColumn(String columnName, String columnType, 
                                       String columnDefault, String isAutoIncrement) {
        if (columnName == null || columnType == null) {
            return false;
        }
        
        String lowerColumnName = columnName.toLowerCase();
        String lowerColumnType = columnType.toLowerCase();
        String lowerDefault = columnDefault != null ? columnDefault.toLowerCase() : "";
        
        // 1. 자동 증가 컬럼 제외 (AUTO_INCREMENT)
        if ("YES".equalsIgnoreCase(isAutoIncrement)) {
            return true;
        }
        
        // 2. 날짜/시간 타입 제외
        if (lowerColumnType.contains("date") || 
            lowerColumnType.contains("time") || 
            lowerColumnType.contains("timestamp") ||
            lowerColumnType.equals("year")) {
            return true;
        }
        
        // 3. UUID/GUID 타입 제외
        if (lowerColumnType.contains("uuid") || 
            lowerColumnType.contains("guid") ||
            lowerColumnType.contains("uniqueidentifier")) {
            return true;
        }
        
        // 4. ID/UID 컬럼명 패턴 제외 (id, uid, uuid, guid 등)
        if (lowerColumnName.equals("id") || 
            lowerColumnName.equals("uid") ||
            lowerColumnName.equals("uuid") ||
            lowerColumnName.equals("guid") ||
            lowerColumnName.endsWith("_id") ||
            lowerColumnName.endsWith("_uid") ||
            lowerColumnName.endsWith("_uuid")) {
            return true;
        }
        
        // 5. 자동 생성 타임스탬프 컬럼 제외 (created_at, updated_at 등)
        if ((lowerColumnName.equals("created_at") || 
             lowerColumnName.equals("updated_at") ||
             lowerColumnName.equals("deleted_at") ||
             lowerColumnName.equals("modified_at")) &&
            (lowerColumnType.contains("timestamp") || 
             lowerColumnType.contains("datetime") ||
             lowerDefault.contains("current_timestamp") ||
             lowerDefault.contains("now()"))) {
            return true;
        }
        
        // 6. 기본값이 자동 생성되는 컬럼 제외 (CURRENT_TIMESTAMP, NOW() 등)
        if (lowerDefault.contains("current_timestamp") ||
            lowerDefault.contains("now()") ||
            lowerDefault.contains("gen_random_uuid()") ||
            lowerDefault.contains("uuid_generate_v4()")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 스키마 메타데이터 DTO
     */
    public static class SchemaMetadata {
        private String databaseName;
        private String tableName;
        private String columnName;
        private String columnType;
        private Boolean isNullable;
        private String columnDefault;
        
        // Getters and Setters
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
        
        public String getColumnType() {
            return columnType;
        }
        
        public void setColumnType(String columnType) {
            this.columnType = columnType;
        }
        
        public Boolean getIsNullable() {
            return isNullable;
        }
        
        public void setIsNullable(Boolean isNullable) {
            this.isNullable = isNullable;
        }
        
        public String getColumnDefault() {
            return columnDefault;
        }
        
        public void setColumnDefault(String columnDefault) {
            this.columnDefault = columnDefault;
        }
    }
}

