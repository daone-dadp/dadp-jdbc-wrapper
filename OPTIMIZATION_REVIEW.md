# Proxy 최적화 검토 및 로그 레벨 조정

## 프로세스 검토 결과

### 현재 프로세스 (모두 필요)

1. **스키마 동기화 (Proxy → Hub)**
   - ✅ 필요: Hub에서 스키마 정보를 관리하기 위해 필요
   - ✅ 최적화됨: 스키마 해시를 사용하여 변경 없으면 동기화 건너뜀
   - 실행 시점: Connection 생성 시 1회 (비동기)

2. **정책 매핑 로드 (Hub → Proxy)**
   - ✅ 필요: 암복호화 대상 컬럼 식별을 위해 필요
   - ✅ 최적화됨: 메모리 캐시에 저장하여 빠른 조회
   - 실행 시점: Connection 생성 시 1회 (비동기)

3. **SQL 파싱**
   - ✅ 필요: 테이블명과 컬럼명 추출을 위해 필요
   - 실행 시점: PreparedStatement/ResultSet 생성 시 (매번)
   - 로그 레벨: `trace`로 변경 (불필요한 로그 감소)

4. **정책 확인 (PolicyResolver)**
   - ✅ 필요: 암복호화 대상 여부 확인을 위해 필요
   - ✅ 최적화됨: 메모리 캐시에서 O(1) 조회
   - 실행 시점: setString/getString 호출 시 (매번)
   - 로그 레벨: `trace`로 변경 (불필요한 로그 감소)

5. **암복호화 수행**
   - ✅ 필요: 실제 암복호화 작업
   - 실행 시점: 정책 매핑이 있는 경우에만 Hub 호출
   - 로그 레벨: `debug` (작업 수행 결과)

### 결론

**불필요한 과정 없음**: 모든 과정이 필수이며, 이미 최적화되어 있습니다.

## 로그 레벨 조정

### TRACE 레벨 (단일 결과 로그)

다음 로그들을 `trace` 레벨로 변경하여 불필요한 로그 감소:

- ✅ 정책 캐시 적중/미적중 (`PolicyResolver.resolvePolicy`)
- ✅ 정책 매핑 추가/제거 (`PolicyResolver.addMapping/removeMapping`)
- ✅ SQL 파싱 완료 (`SqlParser.parse`)
- ✅ PreparedStatement/ResultSet 생성 (`DadpProxyPreparedStatement`, `DadpProxyResultSet`)
- ✅ 정책 확인 결과 (`DadpProxyResultSet.getString`)
- ✅ 암호화 대상 아님 (`DadpProxyPreparedStatement.setString`)
- ✅ 복호화 대상 아님 (`DadpProxyResultSet.getString`)
- ✅ 매핑 로드 (`MappingSyncService.loadMappingsFromHub`)

### DEBUG 레벨 (작업 수행 결과)

다음 로그들은 `debug` 레벨 유지:

- ✅ 암호화 완료 (`DadpProxyPreparedStatement.setString`)
- ✅ 복호화 완료 (`DadpProxyResultSet.getString`)
- ✅ SQL 파싱 실패 (`SqlParser.parse`)

### INFO 레벨 (중요한 작업 완료)

다음 로그들은 `info` 레벨 유지:

- ✅ 스키마 동기화 시작/완료 (`SchemaSyncService`)
- ✅ 정책 매핑 로드 시작/완료 (`MappingSyncService`)
- ✅ 정책 매핑 캐시 갱신 (`PolicyResolver.refreshMappings`)
- ✅ Hub 암복호화 어댑터 초기화 (`HubCryptoAdapter`)

## 로그 레벨 설정 방법

`sample-app`의 `application-proxy.properties`에서 로그 레벨 설정:

```properties
# TRACE 레벨로 상세 로그 확인 (개발/디버깅 시)
logging.level.com.dadp.jdbc=TRACE

# DEBUG 레벨로 작업 결과만 확인 (일반 운영)
logging.level.com.dadp.jdbc=DEBUG

# INFO 레벨로 중요한 작업만 확인 (프로덕션)
logging.level.com.dadp.jdbc=INFO
```

## 성능 영향

- **로그 레벨 조정으로 인한 성능 향상**: 미미함 (로그 출력 비용만 감소)
- **프로세스 최적화**: 이미 최적화되어 있음 (스키마 해시, 메모리 캐싱)

