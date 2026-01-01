# 🛠️ StoLink Backend 트러블슈팅 기록

> 이 문서는 프로젝트 개발 과정에서 마주한 **기술적 이슈와 해결 과정**을 정리한 것입니다.
> N+1 쿼리 최적화, JPA Lazy Loading 처리, 보안 취약점 대응 등 취업 면접에서 어필 가능한 경험을 담았습니다.

**최종 업데이트**: 2025년 12월 28일

---

## 📊 요약

|           분류            | 이슈 수 | 핵심 키워드                       |
| :-----------------------: | :-----: | --------------------------------- |
|     🔴 치명적 (성능)      |   3건   | N+1 쿼리, Fetch Join, EntityGraph |
|     🔴 치명적 (보안)      |   1건   | 평문 비밀번호, 기능 삭제 결정     |
| 🔴 치명적 (데이터 정합성) |   1건   | Neo4j 관계 매핑                   |
|   ⚠️ 경고 (성능/안전성)   |   4건   | Lazy Loading, Dead Code, CI 보안  |

---

## 🔴 N+1 쿼리 최적화 (3건)

### 1. Project-User N+1 문제

**상황**
`ShareService`에서 프로젝트 소유자 권한 검증을 위해 `project.getUser().getId()`를 호출할 때, `Project.user`가 LAZY 로딩이므로 추가 쿼리가 발생했습니다.

```java
// 문제 코드
Project project = projectRepository.findById(projectId).orElseThrow(...);
if (!project.getUser().getId().equals(userId)) ...  // 추가 쿼리 발생!
```

**해결**
`ProjectRepository`에 Fetch Join을 적용한 커스텀 메서드를 추가했습니다.

```java
// ProjectRepository
@Query("SELECT p FROM Project p JOIN FETCH p.user WHERE p.id = :id")
Optional<Project> findByIdWithUser(@Param("id") UUID id);

// ShareService
Project project = projectRepository.findByIdWithUser(projectId).orElseThrow(...);
```

**결과**: 2개 쿼리 → 1개 쿼리로 최적화 ✅

---

### 2. Document-Parent N+1 문제

**상황**
문서 트리 구성을 위해 `groupingBy(doc -> doc.getParent().getId())`를 수행할 때, 각 문서마다 부모 조회 쿼리가 발생했습니다.

```java
// 문제 코드
List<Document> allDocuments = documentRepository.findByProject(project);
// 100개 문서 → 100개 추가 쿼리 발생!
Map<UUID, List<Document>> byParent = documents.stream()
    .collect(groupingBy(doc -> doc.getParent().getId()));
```

**해결**
`DocumentRepository`에 Fetch Join + DISTINCT를 적용했습니다.

```java
// DocumentRepository
@Query("SELECT DISTINCT d FROM Document d LEFT JOIN FETCH d.parent WHERE d.project = :project ORDER BY d.order ASC")
List<Document> findByProjectWithParent(@Param("project") Project project);
```

**결과**: N+1 쿼리 제거, 정렬 보장 ✅

---

### 3. ShareResponse Lazy Loading 위험

**상황**
DTO 변환 시 `ShareResponse.from(share)`에서 `share.getProject().getId()`를 호출하면, 트랜잭션 외부 호출 시 `LazyInitializationException`이 발생하거나 불필요한 프록시 초기화 쿼리가 실행됩니다.

```java
// 문제 코드
return ShareResponse.from(share);  // share.getProject() 호출 시 위험
```

**해결**
이미 조회된 `projectId`를 사용해 Builder 패턴으로 직접 DTO를 생성합니다.

```java
// 개선 코드 - 엔티티 탐색 방지
return ShareResponse.builder()
        .shareId(share.getId())
        .projectId(projectId)  // 이미 알고 있는 값 사용
        .build();
```

**결과**: 엔티티 그래프 탐색 방지, 런타임 안정성 확보 ✅

---

## 🔴 보안 이슈 대응 (1건)

### 4. 평문 비밀번호 저장 이슈 → 기능 삭제 결정

**상황**
공유 링크에 비밀번호 설정 기능이 있었으나, 평문으로 저장되어 보안 취약점이 지속적으로 지적되었습니다.

```java
// 문제 코드
@Column(name = "password")
private String password;  // 평문 저장 위험!
```

**해결**
비밀번호 기능의 필요성이 낮다고 판단하여, **기능 자체를 삭제**하는 의사결정을 내렸습니다.

삭제된 항목:

- `Share` 엔티티: `password` 필드, `updatePassword()` 메서드
- `CreateShareRequest` DTO: `password` 필드
- `ShareResponse` DTO: `hasPassword` 필드
- `ShareService`: 비밀번호 설정/검증 로직
- `ShareController`: `password` 쿼리 파라미터

**결과**: 보안 취약점 원천 제거, 코드 복잡도 감소 ✅

> 💡 **면접 포인트**: "보안 이슈를 해결하기 위해 BCrypt 해싱 적용 vs 기능 삭제를 검토했고, MVP 단계에서 기능 필요성이 낮다고 판단하여 삭제를 선택했습니다. Over-engineering보다 실용적인 의사결정을 우선했습니다."

---

## 🔴 데이터 정합성 (1건)

### 5. Neo4j 관계 타입 명시 누락

**상황**
`@Relationship` 어노테이션에 `type` 속성이 누락되어, Neo4j 그래프 스키마가 불명확해지고 데이터 정합성 문제가 발생할 수 있었습니다.

```java
// 문제 코드
@Relationship  // type 미지정!
private List<CharacterRelationship> relationships = new ArrayList<>();
```

**해결**
명시적으로 관계 타입과 방향을 지정했습니다.

```java
// 개선 코드
@Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
private List<CharacterRelationship> relationships = new ArrayList<>();
```

**결과**: Neo4j 스키마 명확화, 그래프 탐색 쿼리 신뢰성 확보 ✅

---

## ⚠️ 코드 품질 개선 (3건)

### 6. Dead Code 제거 (ShareResponse)

**이슈**: 사용되지 않는 `shareUrl` 필드가 DTO에 존재
**해결**: 불필요한 필드 삭제로 코드 명확성 개선

### 7. Controller 표준 패턴 준수

**이슈**: `@RequestBody(required = false)` + 수동 null 체크의 비표준 패턴
**해결**: 스프링 기본값(`required = true`) 사용, 수동 체크 로직 제거

### 8. Document 쿼리 DISTINCT 추가

**이슈**: LEFT JOIN 시 중복 엔티티 반환 가능성
**해결**: `SELECT DISTINCT` 적용, `ORDER BY` 명시

---

## ⚠️ CI/CD 보안 (1건)

### 9. GitHub Actions Secret 누락 시 처리

**상황**
API 키 누락 시에도 `exit 0`으로 처리되어, 워크플로우가 성공으로 표시되는 문제가 있었습니다.

```bash
# 문제 코드
if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "API Error..." > review_result.txt
  exit 0  # 성공으로 처리됨!
fi
```

**해결**
명시적 실패 처리 + 로그 마스킹을 적용했습니다.

```bash
# 개선 코드
if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "API Error..." > review_result.txt
  echo "error=API_KEY_MISSING" >> $GITHUB_OUTPUT
  exit 1  # 명시적 실패
fi

echo "::add-mask::$ANTHROPIC_API_KEY"  # 로그 보안
```

**결과**: 실패 상태 명확화, 시크릿 로그 노출 방지 ✅

---

## 📌 아키텍처 결정 (ADR)

### Document Tree 전체 로딩 방식 유지

**배경**
문서 트리 조회 시 전체 문서를 메모리에 로드하는 방식으로, 대용량 프로젝트에서 성능 이슈가 우려되었습니다.

**결정**
MVP 단계에서는 현재 방식을 유지하되, N+1 문제는 Fetch Join으로 해결하여 쿼리 수를 최소화했습니다.

**근거**:

- 현재 API 스펙이 전체 트리 반환을 전제로 설계됨
- Lazy Loading 도입 시 프론트엔드 API 계약 변경 필요
- Recursive CTE 등 복잡한 쿼리 패턴이 요구됨

**향후 계획**:

- `MAX_TREE_DEPTH` 상수로 깊이 제한 적용
- 대용량 프로젝트 지원 시 API 구조 변경 검토 (루트만 조회 → Lazy 확장)

---

## 🎤 면접 어필 포인트

### 1. N+1 문제 해결 경험

> "JPA LAZY 로딩으로 인한 N+1 문제를 Fetch Join과 EntityGraph로 해결했습니다. 100개 문서 조회 시 101개 → 1개 쿼리로 최적화했습니다."

### 2. 트레이드오프 의사결정

> "보안 이슈를 해결하기 위해 BCrypt 적용과 기능 삭제를 검토했고, MVP 단계의 실용성을 고려해 기능 삭제를 선택했습니다."

### 3. 방어적 프로그래밍

> "트랜잭션 경계 외부에서 Lazy Loading 접근 시 발생하는 LazyInitializationException을 방지하기 위해, DTO 변환 시 엔티티 그래프 탐색을 최소화했습니다."

### 4. 이중 DB 아키텍처 경험

> "PostgreSQL(RDBMS)과 Neo4j(그래프DB)를 함께 사용하면서, 각 DB의 특성에 맞는 최적화를 적용했습니다."

---

## 🔴 AI 리뷰 피드백 반영 (2026-01-02)

### 10. Entity 노출 및 N+1 문제 종합 해결

**상황**
AI 코드 리뷰에서 Controller의 Entity 직접 반환, 비동기 작업의 트랜잭션 범위 문제, 그리고 `ManuscriptJobService`와 `DocumentService`의 N+1 쿼리 문제가 지적되었습니다.

**해결**

- **DTO 도입**: `CharacterResponse`, `DocumentResponse`를 도입하여 엔티티 노출을 차단했습니다.
- **Batch Insert**: `ManuscriptJobService`에서 300+개 섹션을 `saveAll()`로 일괄 저장하여 성능을 개선했습니다.
- **In-Memory Tree**: `DocumentService.getDocumentTree`에서 재귀 쿼리(N+1) 대신 `findByProjectWithParent`로 한 번에 조회 후 메모리에서 트리로 변환했습니다.

**[2차 반영]**

- **DocumentService**: `parseManuscript`의 반복문 내 `save()` 호출 문제를 **Batch Insert**(`saveAll`)로 추가 수정했습니다.
- **OAuth2**: Access Token 전달 방식을 **URL Fragment**(`#`)로 변경하여 보안을 강화했습니다.

**결과**: API 안정성 확보, 대용량 처리 속도 개선, N+1 쿼리 완전 제거 ✅
