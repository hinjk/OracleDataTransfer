# 기여 가이드

Oracle Data Transfer Tool에 기여해 주셔서 감사합니다! 🎉

---

## 버그 리포트

[Issues](../../issues) 탭에서 버그를 신고해 주세요.

버그 리포트 시 아래 정보를 포함해 주세요:

- **Java 버전** (`java -version` 출력)
- **Oracle DB 버전** (11g / 12c / 19c 등)
- **OS** (Windows / macOS / Linux)
- **재현 절차** (단계별로 상세히)
- **기대 동작** vs **실제 동작**
- **오류 메시지** (있는 경우 전체 스택 트레이스)

---

## 기능 제안

[Issues](../../issues) 탭에서 `enhancement` 레이블로 제안해 주세요.

---

## Pull Request

1. 저장소를 **Fork** 합니다.
2. 기능 브랜치를 생성합니다:
   ```bash
   git checkout -b feature/기능명
   ```
3. 변경사항을 커밋합니다:
   ```bash
   git commit -m "feat: 새 기능 설명"
   ```
4. 브랜치를 Push 합니다:
   ```bash
   git push origin feature/기능명
   ```
5. **Pull Request**를 생성합니다.

### 커밋 메시지 규칙

```
feat:     새로운 기능
fix:      버그 수정
refactor: 코드 리팩토링 (기능 변경 없음)
style:    UI / 스타일 변경
docs:     문서 수정
chore:    빌드, 설정 파일 변경
```

---

## 개발 환경 설정

```bash
git clone https://github.com/YOUR_USERNAME/oracle-data-transfer.git
cd oracle-data-transfer

# ojdbc8.jar를 lib/ 디렉토리에 배치 후
javac -cp .:lib/ojdbc8.jar OracleDataTransfer.java
java  -cp .:lib/ojdbc8.jar OracleDataTransfer
```

외부 빌드 툴(Maven, Gradle) 없이 단일 `.java` 파일로 동작합니다.
