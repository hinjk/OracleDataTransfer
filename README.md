# 🔄 Oracle Data Transfer Tool

> **운영 DB → 개발 DB** 데이터 이관을 위한 Java Swing 기반 GUI 툴

IntelliJ Ultimate의 DB 데이터 복사 기능과 유사하게, Oracle 데이터베이스 간 테이블 데이터를 손쉽게 이관할 수 있는 독립 실행형 도구입니다.

---

## 📸 주요 화면

```
┌─────────────────────────────────────────────────────────────────────┐
│  ⬡  ORACLE DATA TRANSFER  v2   │  Production → Development          │
├──────────────────────┬──────────────────────────────────────────────┤
│  SOURCE (운영 DB)    │  TABLE SELECTION                             │
│  ┌────────────────┐  │  ┌──────────────────────────────────────┐   │
│  │ Server IP      │  │  │ 필터: [___________] [목록조회][미리보기]│  │
│  │ Port           │  │  │ EMPLOYEE                             │   │
│  │ SID            │  │  │ DEPARTMENT                           │   │
│  │ Schema         │  │  │ ORDERS  ◀── 더블클릭 → 미리보기      │   │
│  │ User ID        │  │  └──────────────────────────────────────┘   │
│  │ Password       │  │                                              │
│  │ [연결 테스트]  │  │  PROGRESS                                    │
│  └────────────────┘  │  ┌──────────────────────────────────────┐   │
│                      │  │ 테이블명  │ 상태  │ 건수 │ 이관 │ 배치│   │
│  TARGET (개발 DB)    │  │ EMPLOYEE  │ ✔완료 │1,234 │1,234 │  2 │   │
│  ┌────────────────┐  │  └──────────────────────────────────────┘   │
│  │ ...            │  │                                              │
│  │ [연결 테스트]  │  │  LOG                                         │
│  └────────────────┘  │  [08:42:11] [DONE] EMPLOYEE 총 1,234건       │
│                      │                                              │
│  OPTIONS             │                  [▶ 데이터 이관 시작]        │
└──────────────────────┴──────────────────────────────────────────────┘
```

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **Oracle Thin 접속** | IP / Port / SID / Schema / User / Password 개별 입력 |
| **스키마 지정** | SOURCE · TARGET 각각 다른 스키마 지정 가능 |
| **연결 테스트** | 이관 전 SOURCE / TARGET 연결 상태 개별 확인 |
| **테이블 목록** | `ALL_TABLES` 기반 스키마 내 전체 테이블 자동 조회 |
| **실시간 필터** | 테이블명 키워드 필터링 |
| **데이터 미리보기** | 더블클릭 또는 [미리보기] 버튼으로 SOURCE / TARGET 데이터 별도 확인 |
| **WHERE 조건 조회** | 미리보기 시 조건절 직접 입력 가능 |
| **배치 INSERT** | 지정 건수(기본 1,000건) 단위 분할 INSERT |
| **TRUNCATE 옵션** | INSERT 전 대상 테이블 데이터 삭제 |
| **배치 COMMIT** | 배치 단위 또는 테이블 단위 COMMIT 선택 |
| **FK 제약 제어** | 이관 중 외래키 DISABLE / ENABLE 자동 처리 |
| **진행 상황 표시** | 테이블별 상태, 총 건수, 이관 건수, 배치 번호 실시간 표시 |
| **이관 중단** | 진행 중 즉시 중단 가능 |

---

## 🚀 빠른 시작

### 요구사항

- Java 17 이상 (Java 11도 패턴매칭 제거 시 동작)
- Oracle JDBC Driver (`ojdbc8.jar` 또는 `ojdbc11.jar`)

### Oracle JDBC 드라이버 다운로드

Oracle 공식 사이트에서 무료 다운로드:
👉 https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html

> `ojdbc8.jar` (Oracle 19c 이하) 또는 `ojdbc11.jar` (Oracle 21c 이상) 권장

### 설치 및 실행

```bash
# 1. 저장소 클론
git clone https://github.com/YOUR_USERNAME/oracle-data-transfer.git
cd oracle-data-transfer

# 2. ojdbc8.jar를 프로젝트 루트에 복사 (별도 다운로드 필요)
cp /path/to/ojdbc8.jar ./lib/ojdbc8.jar

# 3. 컴파일
javac -cp .:lib/ojdbc8.jar OracleDataTransfer.java

# 4. 실행
java -cp .:lib/ojdbc8.jar OracleDataTransfer
```

**Windows:**
```cmd
javac -cp .;lib\ojdbc8.jar OracleDataTransfer.java
java  -cp .;lib\ojdbc8.jar OracleDataTransfer
```

---

## 📖 사용 방법

### Step 1 — DB 연결 설정

SOURCE(운영) / TARGET(개발) 각각 입력:

| 항목 | 예시 | 설명 |
|------|------|------|
| Server IP | `192.168.1.10` | Oracle 서버 IP 또는 호스트명 |
| Port | `1521` | Oracle Listener 포트 |
| SID | `ORCL` | Oracle SID (Service Name은 `//ip:port/service` 형식 사용) |
| Schema | `SCOTT` | 테이블이 속한 스키마 (비우면 User ID와 동일) |
| User ID | `scott` | 접속 계정 |
| Password | `****` | 접속 비밀번호 |

> **[연결 테스트]** 버튼으로 접속 확인 후 진행하세요.

### Step 2 — 테이블 선택

1. **[목록 조회]** 클릭 → SOURCE 스키마의 테이블 목록 로드
2. 필터 입력란에 키워드를 입력하면 실시간 필터링
3. 이관할 테이블을 클릭(다중 선택: Ctrl/Shift + 클릭)

### Step 3 — 데이터 미리보기 (선택)

테이블을 **더블클릭** 하거나 **[⊞ 미리보기]** 버튼 클릭:

- **◀ SOURCE 조회** / **▶ TARGET 조회** 각각 확인
- **◀▶ 양쪽 동시 조회** 로 SOURCE ↔ TARGET 비교
- `WHERE` 조건 입력 후 Enter 키로 필터 조회
- 표시 건수 조정 (최대 5,000건)

### Step 4 — 이관 옵션 설정

| 옵션 | 설명 |
|------|------|
| Batch Size | 한 번에 INSERT할 행 수 (기본 1,000) |
| TRUNCATE | INSERT 전 TARGET 테이블 비우기 |
| 배치 단위 COMMIT | 배치마다 COMMIT (OFF 시 테이블 완료 후 COMMIT) |
| FK DISABLE | 외래키 제약조건 일시 비활성화 (DBA 권한 필요) |

### Step 5 — 이관 시작

**[▶ 데이터 이관 시작]** 클릭 → 진행 상황 실시간 확인

---

## ⚠️ 주의사항

- **운영 DB는 읽기 전용으로만 사용** — SELECT 쿼리만 실행됩니다
- TRUNCATE 옵션 사용 시 TARGET 데이터가 **영구 삭제**됩니다
- FK DISABLE 옵션은 **DBA 또는 해당 테이블 소유자 권한**이 필요합니다
- 대용량 이관 시 `Batch Size`와 네트워크 상태를 고려하여 적절히 설정하세요
- Oracle 11g 이하는 `FETCH FIRST N ROWS ONLY` 대신 `ROWNUM` 방식을 사용해야 할 수 있습니다

---

## 🛠 개발 환경

- **Language**: Java 17
- **UI Framework**: Java Swing
- **JDBC**: Oracle Thin Driver (ojdbc8 / ojdbc11)
- **외부 의존성**: Oracle JDBC Driver 외 없음 (순수 Java)

---

## 📁 프로젝트 구조

```
oracle-data-transfer/
├── OracleDataTransfer.java   # 메인 소스 (단일 파일)
├── lib/
│   └── ojdbc8.jar            # Oracle JDBC (별도 다운로드 필요, .gitignore 처리)
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🔧 Oracle 11g 호환 수정

Oracle 11g 이하를 사용하는 경우 미리보기 SQL의 `FETCH FIRST` 구문을 아래와 같이 변경하세요:

```java
// Before (Oracle 12c+)
"SELECT * FROM " + qualified + " FETCH FIRST " + maxRows + " ROWS ONLY"

// After (Oracle 11g)
"SELECT * FROM (SELECT * FROM " + qualified + ") WHERE ROWNUM <= " + maxRows
```

---

## 📄 라이선스

MIT License — 자유롭게 사용, 수정, 배포 가능합니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

---

## 🤝 기여

버그 리포트, 기능 제안, PR 모두 환영합니다!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
