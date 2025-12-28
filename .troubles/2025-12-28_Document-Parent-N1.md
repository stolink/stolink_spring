# Document Parent N+1 Issue

## Issue Description

`ShareService.buildDocumentTree`에서 `documents.stream().collect(groupingBy(doc -> doc.getParent().getId()))`를 수행할 때, `Document.parent`가 LAZY로 설정되어 있다면 각 문서마다 부모를 조회하는 쿼리가 발생할 수 있습니다 (N+1 문제).

- 파일: `src/main/java/com/stolink/backend/domain/share/service/ShareService.java`
- 라인: 106 (`findAllByProject` 호출 후 트리 빌딩 시점)
- 에러 유형: 🔴 치명적 (성능)

## Solution Strategy

`DocumentRepository`에 `parent`를 패치 조인(Fetch Join)으로 함께 조회하는 메서드를 추가하여 쿼리를 1회로 최적화합니다.

### 변경 전

```java
// ShareService
List<Document> allDocuments = documentRepository.findByProject(project);
```

### 변경 후

```java
// DocumentRepository
@Query("SELECT d FROM Document d LEFT JOIN FETCH d.parent WHERE d.project = :project")
List<Document> findByProjectWithParent(@Param("project") Project project);

// ShareService
List<Document> allDocuments = documentRepository.findByProjectWithParent(project);
```

## Outcome

- **상태**: ✅ 해결됨
- **검증**: 빌드 및 코드 리뷰
