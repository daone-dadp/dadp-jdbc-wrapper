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
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = connection.getCatalog();
            
            log.trace("🔍 스키마 메타데이터 수집 시작: database={}", databaseName);
            
            // 테이블 목록 조회
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableSchema = tables.getString("TABLE_SCHEM");
                    
                    log.trace("📋 테이블 발견: {}.{}", tableSchema, tableName);
                    
                    // 컬럼 정보 조회
                    try (ResultSet columns = metaData.getColumns(null, tableSchema, tableName, "%")) {
                        while (columns.next()) {
                            SchemaMetadata schema = new SchemaMetadata();
                            schema.setDatabaseName(databaseName);
                            schema.setTableName(tableName);
                            schema.setColumnName(columns.getString("COLUMN_NAME"));
                            schema.setColumnType(columns.getString("TYPE_NAME"));
                            schema.setIsNullable("YES".equals(columns.getString("IS_NULLABLE")));
                            schema.setColumnDefault(columns.getString("COLUMN_DEF"));
                            
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

