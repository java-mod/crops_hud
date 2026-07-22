# 변경 이력

## [v1.0.2] — 2026-07-22

### 📦 변경 사항
- 👷 ci: VirusTotal 검사 결과를 CHANGELOG.md에도 링크로 첨부

- CHANGELOG.md 생성 방식을 전체 재생성(--output)에서 증분 방식
  (--current --prepend)으로 변경 — 과거 섹션이 다음 릴리즈 때 통째로
  재생성되며 사라지지 않도록 함
- virustotal.yml이 스캔 완료 후 해당 태그의 CHANGELOG.md 섹션 아래에
  jar별 VirusTotal 분석 링크를 삽입하고 커밋/푸시하도록 추가
- 삽입 스크립트는 실제 CHANGELOG.md 스냅샷으로 로컬 검증 완료

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>

- 버전 v1.0.1 빌드 카운터 업데이트 [skip ci]

- 👷 ci: VirusTotal 자동 검사 기능 제거

- virustotal.yml 삭제
- CHANGELOG.md 생성 방식을 원래의 전체 재생성(--output)으로 복원
  (--current --prepend은 VirusTotal 링크 삽입을 위해서만 필요했음)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>

- 버전 v1.0.2 빌드 카운터 업데이트 [skip ci]


## [v1.0.0] — 2026-07-22

### 📦 변경 사항
- ✨ feat: 초기 릴리즈

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.0 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.0 업데이트 [skip ci]

- 📝 docs: 명령어 사용 가이드 업데이트

대기시간, 작물고정, 배경 설정 명령어 추가.
자동 일시정지 설명을 고정 5초에서 설정 가능으로 수정.
한/영 명령어 병기 및 예시 보강.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.1 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.1 업데이트 [skip ci]

- ✨ feat: HUD 시간당 수익 제거 및 예상 수익 소숫점 미표시 처리

- 시간당 수익 행 제거 (CARD_HEIGHT 86→76)
- 예상 수익 표시를 소숫점 절사(RoundingMode.DOWN) 정수로 변경
- formatInteger() 헬퍼 메서드 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.2 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.2 업데이트 [skip ci]

- ♻️ refactor: 자동 업데이트를 수동 알림 방식으로 전환

- ⚖️ license: fabric 메타데이터를 GPL-3.0-only로 변경

- ⚖️ license: GPL-3.0-only 라이선스 파일 추가

- 📝 docs: v1.0.3 변경 이력 업데이트

- 버전 v1.0.3 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.3 업데이트 [skip ci]

- 📝 docs: README 문서로 전환하고 사용 가이드 재작성

Ultraworked with [Sisyphus](https://github.com/code-yeongyu/oh-my-openagent)

Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>

- 버전 v1.0.4 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.4 업데이트 [skip ci]

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

- 🐛 fix: HUD 오버레이 등록 시 JDK 모듈 경계에서 발생하는 IllegalAccessException 수정

- Event.register() 호출을 impl 서브클래스가 아닌 공개 Event 베이스 클래스에서 조회하도록 변경
- setAccessible(true) 추가로 JDK 16+ 모듈 시스템 접근 제한 우회
- registerOldestHudLayer()를 별도 메서드로 분리하여 폴백 체인 명확화
- registerHudRendererCompat() 폴백 흐름에 누락된 return 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.6 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.6 업데이트 [skip ci]

- 🐛 fix: HUD 예외 처리 범위 확장 및 1.21.10 KeyBinding.Category 탐색 로직 수정

- registerHudRendererCompat() catch 블록을 ReflectiveOperationException → Exception으로 확장
- findMethod()가 NoSuchMethodException을 던지도록 수정하여 catch 블록에서 올바르게 처리
- findKeyBindingCategoryClass()를 create(Identifier) 또는 constructor(Identifier) 중
  하나만 있어도 Category 클래스를 찾도록 변경 (MC 1.21.10에서 String 생성자 제거 대응)

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 🏗️ build: 1.21.10·1.21.11 지원 추가 및 로더 의존성 버전 동적 주입

- supportedVersionMatrix에 1.21.10, 1.21.11 항목 추가
  - 1.21.10: yarn 1.21.10+build.2 / fabric-api 0.138.4 / loader 0.17.2
  - 1.21.11: yarn 1.21.11+build.4 / fabric-api 0.141.1 / loader 0.18.1
- loader_dependency 프로퍼티를 추가하여 버전별 최소 로더 요구사항을
  fabric.mod.json에 빌드 시 동적으로 주입
- buildAllSupportedMcVersions 태스크에 loader_dependency 파라미터 전달

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 🔧 chore: fabric.mod.json Fabric Loader 의존 버전을 빌드 시 주입 방식으로 변경

hardcode된 >=0.16.10 대신 ${loader_dependency} 플레이스홀더를 사용하여
버전별 JAR마다 올바른 최소 로더 요구사항이 기록되도록 수정

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 📝 docs: 지원 버전 1.21.10·1.21.11 및 Fabric Loader 요구사항 반영

- 지원 버전 목록을 표 형식으로 변경하고 버전별 최소 Loader 요구사항 명시
- 특정 버전 빌드 예시에 1.21.10, 1.21.11 추가 및 loader_dependency 파라미터 포함
- dist 출력 경로에 1.21.10, 1.21.11 디렉터리 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 📝 docs: CHANGELOG v1.0.8 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.8 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.8 업데이트 [skip ci]

- 👷 ci: 1.21.10·1.21.11 matrix 추가 및 loader_dependency 파라미터 전달

- build matrix에 1.21.10, 1.21.11 항목 추가
- 모든 matrix 항목에 loader_dependency 필드 추가
- Build 스텝에 -Ploader_dependency 파라미터 전달 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

- 버전 v1.0.9 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.9 업데이트 [skip ci]

- ✨ feat: HUD 배경 색상/투명도/커스텀 이미지 커스터마이징 기능 추가

- /hud edit 화면에 배경 색상(프리셋+헥스 입력)·투명도 슬라이더·커스텀 이미지
  토글·이미지 선택(드래그앤드롭 및 네이티브 파일 대화상자) 패널을 통합
- 선택한 이미지는 카드 비율에 맞춰 고품질 리샘플링(단계적 다운스케일+bicubic)
  후 config/crophud/background.png로 저장
- DrawContext#drawTexture, NativeImageBackedTexture 생성자 등 1.21.4~1.21.11
  사이에 시그니처가 바뀌는 API를 리플렉션으로 흡수
- net.minecraft.* 클래스/메서드 리플렉션은 FabricLoader의 MappingResolver로
  intermediary 이름을 런타임 이름으로 변환하도록 수정 (프로덕션 환경에서
  Class.forName 문자열이 리매핑되지 않아 항상 실패하던 문제의 근본 원인 수정)
- 6개 지원 버전(1.21.4~1.21.11) 전체 compileJava 통과 확인

👷 ci: 릴리즈 발행 시 VirusTotal 자동 검사 워크플로우 추가

- release event(published) 트리거로 첨부된 jar를 스캔하고 결과 링크를
  릴리즈 노트에 자동 첨부 (crazy-max/ghaction-virustotal)
- VT_API_KEY 시크릿 등록 필요

🔧 chore: 로컬 빌드 카운터를 0으로 리셋

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>

- 버전 v1.0.0 빌드 카운터 업데이트 [skip ci]

- CHANGELOG.md v1.0.0 업데이트 [skip ci]



