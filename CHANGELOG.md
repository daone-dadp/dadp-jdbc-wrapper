# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.5] - 2025-11-12

### 🎉 릴리즈 정보

**버전**: 3.0.5  
**릴리즈 일자**: 2025-11-12  
**주요 개선사항**: Data truncation 에러 처리 개선 및 Hub 알림 시스템 통합

### ✅ Added

- **Hub 알림 시스템 통합**: 암복호화 실패 시 Hub로 자동 알림 전송
- **Data truncation 자동 복구**: 암호화된 데이터가 컬럼 크기를 초과할 경우 평문으로 자동 재시도 (Fail-open 모드)
- **원본 데이터 저장**: Data truncation 발생 시 평문으로 재시도하기 위한 원본 데이터 보관 기능

### 🔧 Changed

- **DadpProxyPreparedStatement**: `executeUpdate()` 메서드에서 Data truncation 에러 감지 및 자동 복구 로직 추가
- **HubCryptoAdapter**: 암복호화 실패 시 Hub 알림 서비스와 통합
- **DadpProxyConnection**: HubNotificationService 초기화 및 통합

### 🐛 Fixed

- Data truncation 에러 발생 시 애플리케이션 중단 문제 해결 (평문으로 자동 재시도)
- 암호화된 데이터가 컬럼 크기를 초과할 경우 알림 전송 및 자동 복구 기능 추가

### 📚 Compatibility

| Java 버전 | 지원 여부 | 비고 |
|-----------|----------|------|
| Java 8    | ✅ 지원   | 기존 지원 유지 |
| Java 11   | ✅ 지원   | 기존 지원 유지 |
| Java 17   | ✅ 지원   | 기본 빌드 버전 |
| Java 21   | ✅ 지원   | 하위 호환성으로 지원 |

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

- [v3.0.4 Release Notes](RELEASE_NOTES_v3.0.4.md)

