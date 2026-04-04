# 변경 이력

## [v1.0.8] — 2026-04-04

### 🐛 버그 수정
- HUD 등록 호환 로직의 예외 처리 범위를 `ReflectiveOperationException` → `Exception`으로 확장하여 런타임 예외 누락 방지
- `findMethod()` 반환 실패 시 `IllegalStateException` 대신 `NoSuchMethodException`을 던지도록 수정 (catch 블록에서 올바르게 잡힘)
- MC 1.21.10에서 `KeyBinding.Category` 탐색 시 `create(Identifier)` 또는 생성자 중 하나만 있어도 인식하도록 수정 (String 기반 카테고리 생성자 제거 대응)

### 🏗️ 빌드
- MC 1.21.10 (`fabric-api 0.138.4+1.21.10`, Yarn `1.21.10+build.2`) 지원 추가
- MC 1.21.11 (`fabric-api 0.141.1+1.21.11`, Yarn `1.21.11+build.4`) 지원 추가
- `fabric.mod.json`의 Fabric Loader 의존 버전을 빌드 시 동적으로 주입하도록 변경
  - 1.21.4 ~ 1.21.8: `>= 0.16.10`
  - 1.21.10: `>= 0.17.2`
  - 1.21.11: `>= 0.18.1`

---

## [v1.0.6] — 2026-04-04

### 📦 변경 사항
- 🐛 fix: HUD 오버레이 등록 시 JDK 모듈 경계에서 발생하는 IllegalAccessException 수정

- Event.register() 호출을 impl 서브클래스가 아닌 공개 Event 베이스 클래스에서 조회하도록 변경
- setAccessible(true) 추가로 JDK 16+ 모듈 시스템 접근 우회
- registerOldestHudLayer()를 별도 메서드로 분리하여 폴백 체인 명확화
- registerHudRendererCompat() 폴백 흐름에 누락된 return 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.6 빌드 카운터 업데이트 [skip ci]

---

## [v1.0.5] — 2026-04-03

### 📦 변경 사항
- ♻️ refactor: HUD 호환 렌더링과 입력 처리를 정리

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 🏗️ build: 지원 버전 빌드 구성을 1.21.8까지로 재정리

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 👷 ci: 멀티버전 워크플로우를 지원 범위에 맞게 축소

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 📝 docs: 지원 버전과 빌드 가이드를 최신화

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 버전 v1.0.5 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.5 업데이트 [skip ci]


## [v1.0.4] — 2026-04-03

### 📦 변경 사항
- 📝 docs: README 문서로 전환하고 사용 가이드 재작성

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 버전 v1.0.4 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.4 업데이트 [skip ci]


## [v1.0.3] — 2026-04-03

### 📦 변경 사항
- ♻️ refactor: 자동 업데이트를 수동 알림 방식으로 전환

- ⚖️ license: fabric 메타데이터를 GPL-3.0-only로 변경

- ⚖️ license: GPL-3.0-only 라이선스 파일 추가

- 📝 docs: v1.0.3 변경 이력 업데이트

- 버전 v1.0.3 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.3 업데이트 [skip ci]


## [v1.0.2] — 2026-03-27

### 📦 변경 사항
- ✨ feat: HUD 시간당 수익 제거 및 예상 수익 소숫점 미표시 처리

- 시간당 수익 행 제거 (CARD_HEIGHT 86→76)
- 예상 수익 표시를 소숫점 절사(RoundingMode.DOWN) 정수로 변경
- formatInteger() 헬퍼 메서드 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.2 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.2 업데이트 [skip ci]


## [v1.0.1] — 2026-03-27

### 📦 변경 사항
- 📝 docs: 명령어 사용 가이드 업데이트

대기시간, 작물고정, 배경 설정 명령어 추가.
자동 일시정지 설명을 고정 5초에서 설정 가능으로 수정.
한/영 명령어 병기 및 예시 보강.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.1 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.1 업데이트 [skip ci]


## [v1.0.0] — 2026-03-27

### 📦 변경 사항
- ✨ feat: 초기 릴리즈

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.0 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.0 업데이트 [skip ci]


