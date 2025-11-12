# DADP JDBC Wrapper

<<<<<<< HEAD
> **🔐 DB 자동 암복호화 JDBC Wrapper Driver (3.0)**

JDBC URL만 변경하여 코드 수정 없이 자동 암복호화를 제공하는 JDBC Wrapper Driver입니다.

## 📦 제공 라이브러리

| 라이브러리 | 버전 | 설명 |
|----------|------|------|
| `dadp-jdbc-wrapper` | 3.0.0 | JDBC Wrapper Driver (Fat JAR) ⭐ |

## 🚀 빠른 시작

### Maven 설정

Maven Central에서 자동으로 다운로드됩니다. 별도의 repository 설정이 필요 없습니다.

```xml
<dependencies>
    <dependency>
        <groupId>io.github.daone-dadp</groupId>
        <artifactId>dadp-jdbc-wrapper</artifactId>
        <version>3.0.0</version>
        <classifier>all</classifier>
    </dependency>
</dependencies>
```

### application.properties 설정

```properties
# JDBC URL 변경 (코드 수정 없음)
=======
> **🔐 DB 자동 암복호화 Proxy 모듈 (3.0)**

JDBC URL만 변경하여 코드 수정 없이 자동 암복호화를 제공하는 JDBC Wrapper Driver입니다.

## 빠른 시작

### 1. Wrapper JAR 및 DB 드라이버 배포

**고객사 애플리케이션은 재빌드하지 않고, Wrapper JAR와 필요한 DB 드라이버만 배치하면 됩니다:**

```bash
# 1. Wrapper JAR 빌드 (DB 드라이버는 포함되지 않음)
mvn clean package
# 결과물: target/dadp-jdbc-wrapper-3.0.0-all.jar

# 2. 필요한 DB 드라이버를 lib 폴더에 배치
# 예: MySQL 사용 시
mkdir -p libs
cp mysql-connector-java-8.0.33.jar libs/

# 3. Wrapper JAR도 lib 폴더에 배치
cp target/dadp-jdbc-wrapper-3.0.0-all.jar libs/

# 4. 실행 시 lib 폴더를 클래스패스에 포함
# Spring Boot:
java -Dloader.path=libs -jar target/app.jar --spring.profiles.active=proxy

# 또는 직접 클래스패스 지정:
java -cp "target/app.jar:libs/*" org.springframework.boot.loader.JarLauncher --spring.profiles.active=proxy
```

**지원하는 DB 드라이버:**
- MySQL: `mysql-connector-java-8.0.33.jar` 또는 `mysql-connector-j-8.x.x.jar`
- PostgreSQL: `postgresql-42.6.0.jar`
- Oracle: `ojdbc8.jar` 또는 `ojdbc11.jar`
- MariaDB: `mariadb-java-client-3.1.4.jar`
- 기타: JDBC 4.0+ 표준 드라이버 모두 지원

### 2. application.properties 수정

**애플리케이션 코드나 `pom.xml` 수정 없이 설정 파일만 변경:**

```properties
# 기존
# spring.datasource.url=jdbc:mysql://localhost:3306/mydb
# spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# 변경 (코드 수정 없음)
>>>>>>> master
spring.datasource.url=jdbc:dadp:mysql://localhost:3306/mydb
spring.datasource.driver-class-name=com.dadp.jdbc.DadpJdbcDriver

# Proxy 설정 (선택)
dadp.proxy.hub-url=http://localhost:9004
dadp.proxy.instance-id=proxy-1
```

<<<<<<< HEAD
### DB 드라이버 배치

**중요**: Wrapper JAR에는 DB 드라이버가 포함되지 않습니다. 필요한 DB 드라이버를 `libs` 폴더에 배치하세요.

```bash
# libs 폴더 생성
mkdir libs

# 필요한 DB 드라이버만 배치
# MySQL 사용 시:
cp mysql-connector-java-8.0.33.jar libs/

# PostgreSQL 사용 시:
cp postgresql-42.6.0.jar libs/

# Oracle 사용 시:
cp ojdbc8.jar libs/
```

**실행 시 lib 폴더를 클래스패스에 포함:**
```bash
java -Dloader.path=libs -jar app.jar
```

## 📚 문서

- **[CHANGELOG](CHANGELOG.md)** - 버전별 변경사항 (최신: v3.0.5)
- **[Release Notes v3.0.4](RELEASE_NOTES_v3.0.4.md)** - v3.0.4 상세 릴리즈 노트
- **[사용 가이드](docs/USER_GUIDE.md)** - 고객사용 통합 가이드 (예정)

## 🔗 링크

- **GitHub**: https://github.com/daone-dadp/dadp-jdbc-wrapper
- **Maven Central**: https://repo1.maven.org/maven2/io/github/daone-dadp/dadp-jdbc-wrapper/
- **배포 상태**: ✅ Maven Central 배포 완료

## 📄 라이선스

Apache License 2.0

---

**작성일**: 2025-11-07  
**최종 업데이트**: 2025-11-07
=======
### 3. JDBC Driver 자동 로드

- `META-INF/services/java.sql.Driver` 파일 포함
- `static` 블록에서 `DriverManager.registerDriver()` 자동 호출
- 클래스패스에 JAR만 추가하면 자동으로 Driver가 등록됨

## 모듈 구조

```
dadp-jdbc-wrapper/
├── driver/                      # JDBC Driver 구현
│   ├── DadpJdbcDriver          # JDBC Driver 메인 클래스
│   ├── DadpProxyConnection      # Connection 래퍼
│   ├── DadpProxyPreparedStatement  # PreparedStatement 래퍼
│   └── DadpProxyResultSet      # ResultSet 래퍼
├── policy/                      # Policy Resolver
│   ├── SchemaRecognizer        # 스키마 인식기
│   ├── PolicyResolver           # 정책 리졸버
│   └── SqlParser                # SQL 파서
├── crypto/                      # Hub 연동
│   └── HubCryptoAdapter        # Hub API 호출 어댑터
└── schema/                      # 스키마 메타데이터 관리
    ├── SchemaMetadataManager   # 스키마 메타데이터 관리
    └── SchemaSyncService       # Hub와 스키마 동기화
```

## 동작 방식

1. **JDBC URL 파싱**: `jdbc:dadp:mysql://...` → `jdbc:mysql://...`
2. **Connection 래핑**: 실제 DB Connection을 Proxy로 래핑
3. **SQL 파싱**: PreparedStatement/ResultSet에서 SQL 분석
4. **정책 리졸버**: 테이블.컬럼 → 정책명 자동 매핑
5. **암복호화**: Hub API 호출하여 암복호화 처리

## 지원 데이터베이스

- MySQL
- PostgreSQL (예정)

## 참고 문서

- [설계 문서](../docs/modules/dadp-proxy/design.md)
- [전체 아키텍처](../docs/design/architecture.md)
>>>>>>> master

