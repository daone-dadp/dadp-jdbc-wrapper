# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.5] - 2025-11-26

### 🎉 릴리즈 정보

**버전**: 3.0.5  
**릴리즈 일자**: 2025-11-26  
**주요 개선사항**: Hibernate/MyBatis 등 다중 ORM 지원, 첫 번째 쿼리부터 암호화 정책 적용 보장, Java 버전별 HTTP 클라이언트 추상화

### ✅ Added

- **Hibernate SQL Alias 자동 변환**: Hibernate가 생성하는 alias(`email3_0_`)를 원본 컬럼명(`email`)으로 자동 변환
- **다중 ORM/프레임워크 지원**: Hibernate, MyBatis, JdbcTemplate, jOOQ, QueryDSL 등 모든 JDBC 기반 프레임워크 호환
- **SqlParser alias 매핑**: SELECT문 파싱 시 `AS` 키워드 기반 alias 매핑 자동 생성
- **정책 로드 완료 대기 로직**: `CountDownLatch`를 사용하여 정책 로드 완료를 대기하는 기능 추가
- **`ensureMappingsLoaded()` 메서드**: 모든 `prepareStatement` 호출 전에 정책 로드 완료 확인
- **타임아웃 설정**: 정책 로드 대기 최대 10초 (무한 대기 방지)
- **DadpProxyStatement 클래스**: Statement 래핑하여 `executeQuery()`에서 복호화 처리
- **ResultSet.getObject() 복호화**: JdbcTemplate 호환을 위해 `getObject()` 메서드에 복호화 로직 추가
- **HTTP 클라이언트 추상화**: Java 버전에 따라 최적의 HTTP 클라이언트 자동 선택
  - Java 8: `HttpURLConnection` 사용
  - Java 11+: `java.net.http.HttpClient` 사용
  - `HttpClientAdapter.Factory.create()` 팩토리 패턴으로 구현체 생성
- **Hub 알림 시스템 통합**: 암복호화 실패 시 Hub로 자동 알림 전송
- **Data truncation 자동 복구**: 암호화된 데이터가 컬럼 크기를 초과할 경우 평문으로 자동 재시도 (Fail-open 모드)
- **원본 데이터 저장**: Data truncation 발생 시 평문으로 재시도하기 위한 원본 데이터 보관 기능

### 🔧 Changed

- **DadpProxyConnection**: 정책 로드가 완료될 때까지 쿼리 실행 대기 (첫 번째 쿼리부터 암호화 적용 보장)
- **DadpProxyConnection.createStatement()**: `DadpProxyStatement`를 반환하도록 변경
- **`loadMappingsFromHub()`**: `CountDownLatch`를 사용하여 완료 시점 알림
- **DadpProxyResultSet.getString(String)**: alias를 원본 컬럼명으로 변환 후 정책 조회
- **DadpProxyResultSet.getObject()**: String 타입인 경우 복호화 처리 추가
- **DadpProxyResultSet.decryptStringByLabel()**: alias 변환 로직 추가
- **SqlParser.SqlParseResult**: aliasToColumnMap 필드 추가, getOriginalColumnName() 메서드 추가
- **DadpProxyPreparedStatement**: `executeUpdate()` 메서드에서 Data truncation 에러 감지 및 자동 복구 로직 추가
- **HubCryptoAdapter**: 암복호화 실패 시 Hub 알림 서비스와 통합
- **DadpProxyConnection**: HubNotificationService 초기화 및 통합

### 🐛 Fixed

- ✅ **Hibernate 복호화 실패 문제 해결**: alias(`email3_0_`) → 원본 컬럼명(`email`) 변환으로 정책 조회 성공
- ✅ 첫 번째 쿼리에 암호화 정책이 적용되지 않던 문제 해결
- ✅ 정책 로드가 비동기로 수행되어 발생하던 타이밍 이슈 해결
- ✅ JdbcTemplate이 Statement를 사용할 때 복호화가 안 되던 문제 해결 (DadpProxyStatement 추가)
- ✅ ResultSet.getObject() 호출 시 복호화가 안 되던 문제 해결
- ✅ DadpProxyResultSet.getString() 중괄호 오류 수정
- Data truncation 에러 발생 시 애플리케이션 중단 문제 해결 (평문으로 자동 재시도)
- 암호화된 데이터가 컬럼 크기를 초과할 경우 알림 전송 및 자동 복구 기능 추가

### 🔌 ORM/Framework Compatibility

| 프레임워크 | 암호화 | 복호화 | 비고 |
|-----------|--------|--------|------|
| **JdbcTemplate** | ✅ | ✅ | 직접 컬럼명 사용 |
| **Hibernate/JPA** | ✅ | ✅ | alias 자동 변환 |
| **MyBatis** | ✅ | ✅ | AS alias 파싱 지원 |
| **jOOQ** | ✅ | ✅ | AS alias 파싱 지원 |
| **QueryDSL** | ✅ | ✅ | AS alias 파싱 지원 |

### 📚 Compatibility

| Java 버전 | 지원 여부 | 비고 |
|-----------|----------|------|
| Java 8    | ✅ 지원   | 기존 지원 유지 |
| Java 11   | ✅ 지원   | 기존 지원 유지 |
| Java 17   | ✅ 지원   | 기본 빌드 버전 |
| Java 21   | ✅ 지원   | 하위 호환성으로 지원 |

### 🔗 Links

- Release Notes: [RELEASE_NOTES_v3.0.5.md](RELEASE_NOTES_v3.0.5.md)

---

## [3.0.4] - 2025-11-12

### 🎉 릴리즈 정보

**버전**: 3.0.4  
**릴리즈 일자**: 2025-11-12  
**주요 개선사항**: Java 8 호환성 개선

### ✅ Added

- Java 8 호환성 지원 추가
- Java 8, 11, 17 프로파일별 빌드 지원

### 🔧 Changed

- **SchemaSyncService**: `java.net.http.HttpClient` (Java 11+) → `java.net.HttpURLConnection` (Java 8+)
- **MappingSyncService**: `java.net.http.HttpClient` (Java 11+) → `java.net.HttpURLConnection` (Java 8+)

### 🐛 Fixed

- Java 8 환경에서 발생하던 `NoClassDefFoundError: java/net/http/HttpClient` 오류 해결
- Java 8 환경에서 정상 동작 확인

### 📦 Build & Deployment

- Java 버전별 프로파일 빌드 지원:
  - `-Pjava8`: Java 8용 빌드
  - `-Pjava11`: Java 11용 빌드
  - `-Pjava17`: Java 17용 빌드 (기본)
- Maven Central 배포 완료 (Java 8, 11, 17 프로파일)

### 📚 Compatibility

| Java 버전 | 지원 여부 | 비고 |
|-----------|----------|------|
| Java 8    | ✅ 지원   | 이번 릴리즈에서 추가 |
| Java 11   | ✅ 지원   | 기존 지원 유지 |
| Java 17   | ✅ 지원   | 기본 빌드 버전 |
| Java 21   | ✅ 지원   | 하위 호환성으로 지원 |

### 🔗 Links

- GitHub: https://github.com/daone-dadp/dadp-jdbc-wrapper
- Maven Central: https://central.sonatype.com/artifact/io.github.daone-dadp/dadp-jdbc-wrapper
- Release Notes: [RELEASE_NOTES_v3.0.4.md](RELEASE_NOTES_v3.0.4.md)

---

## [3.0.3] - 이전 버전

이전 버전의 변경사항은 [GitHub Releases](https://github.com/daone-dadp/dadp-jdbc-wrapper/releases)에서 확인하세요.

---

## 릴리즈 노트 형식

각 주요 릴리즈에 대한 상세한 릴리즈 노트는 별도 파일로 관리됩니다:

- [v3.0.5 Release Notes](RELEASE_NOTES_v3.0.5.md)
- [v3.0.4 Release Notes](RELEASE_NOTES_v3.0.4.md)

