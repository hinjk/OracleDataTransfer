# Changelog

모든 주요 변경 사항은 이 파일에 기록됩니다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따릅니다.

---

## [2.0.0] - 2025-04-17

### Added
- **스키마 입력 필드** — SOURCE / TARGET 각 DB 카드에 Schema 입력란 추가
  - `ALL_TABLES WHERE OWNER = ?` 기반으로 지정 스키마의 테이블 조회
  - 스키마 비워두면 접속 User ID 스키마로 자동 처리
  - 이관 SQL 자동으로 `SCHEMA.TABLE` 형식 적용
- **데이터 미리보기 다이얼로그**
  - 테이블 더블클릭 또는 [⊞ 미리보기] 버튼으로 팝업
  - SOURCE / TARGET 탭으로 각각 조회
  - [◀▶ 양쪽 동시 조회] 버튼으로 비교 편의 제공
  - WHERE 조건 입력 지원 (Enter 키로 즉시 실행)
  - 표시 건수 설정 (최대 5,000건)
  - 컬럼명 + 데이터 타입 헤더 표시
  - 자동 컬럼 너비 조정
  - null 값 시각적 구분 (`(null)`, 이탤릭 회색)
  - 홀짝 행 색상 구분으로 가독성 향상
- 빌드 스크립트 추가 (`build.sh`, `build.bat`)
- `lib/` 디렉토리 구조 및 안내 파일 추가

### Changed
- 테이블 목록 필터: 원본 리스트(`allTableNames`) 별도 유지로 필터 해제 시 전체 복원
- 이관 확인 다이얼로그에 SOURCE / TARGET 스키마 정보 표시

---

## [1.0.0] - 2025-04-10

### Added
- Oracle Thin JDBC 접속 (IP / Port / SID / User / Password)
- SOURCE / TARGET 연결 테스트 (개별)
- USER_TABLES 기반 테이블 목록 조회
- 테이블 다중 선택 및 키워드 필터
- 배치 단위 INSERT (건수 지정)
- TRUNCATE 옵션
- 배치 / 테이블 단위 COMMIT 선택
- FK 제약조건 DISABLE / ENABLE 자동 처리
- 이관 진행 상황 실시간 표시 (테이블별 상태, 건수, 배치)
- 이관 중단 기능
- 다크 테마 Java Swing UI
