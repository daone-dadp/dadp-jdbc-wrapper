# DADP JDBC Wrapper

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
spring.datasource.url=jdbc:dadp:mysql://localhost:3306/mydb
spring.datasource.driver-class-name=com.dadp.jdbc.DadpJdbcDriver

# Proxy 설정 (선택)
dadp.proxy.hub-url=http://localhost:9004
dadp.proxy.instance-id=proxy-1
```

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

