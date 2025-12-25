# StoLink 에디터 핵심 기능 명세

> **버전**: 1.3
> **최종 수정**: 2024년 12월 25일
> **검증**: 코드베이스 대조 완료

---

## 구현 현황 요약

| 기능               | 상태      | 검증 결과                                  |
| ------------------ | --------- | ------------------------------------------ |
| 통합 Document 모델 | ✅ 완료   | `src/types/document.ts` - folder/text 타입 |
| Repository 패턴    | ✅ 완료   | `src/repositories/` - 2개 파일             |
| Section Strip      | ✅ 완료   | `SectionStrip.tsx` - EditorPage에서 사용   |
| 분할 화면          | ✅ 완료   | `useEditorStore.ts` - splitView            |
| 집중 모드          | ✅ 완료   | `useEditorStore.ts` - isFocusMode          |
| 복선 관리          | ✅ 완료   | `useForeshadowingStore.ts` - appearances   |
| 씬 인스펙터        | ✅ 완료   | `SceneInspector.tsx`                       |
| Character 타입     | ✅ 완료   | `character.ts` - Role, Relationship        |
| Place/Item 타입    | ✅ 완료   | `character.ts` - 세계관 요소               |
| Scrivenings 뷰     | ✅ 완료   | `ScriveningsEditor.tsx` - 통합 편집 모드   |
| Outline 뷰         | ✅ 완료   | `OutlineView.tsx` - 테이블 기반 아웃라인   |
| 🆕 사이드바 분리   | ✅ 완료   | `sidebar/` 폴더 - 6개 컴포넌트             |
| 🆕 컨텍스트 메뉴   | ✅ 완료   | `ContextMenu.tsx` - 객체/컨테이너 분리     |
| 🆕 에디터 줌       | ✅ 완료   | `TiptapEditor.tsx` - 50-200% 줌            |
| 🆕 텍스트 가져오기 | ✅ 완료   | `LibraryPage.tsx` - TXT/MD 스마트 정리     |
| 🆕 내보내기 서비스 | ✅ 완료   | `exportService.ts` - PDF/EPUB/TXT          |
| 버전/스냅샷        | ❌ 미구현 | grep 검색 결과 없음                        |
| 인라인 링크        | ❌ 미구현 | `[[...]]` 패턴 없음                        |

---

## 1. 문서 구조 관리

### 1.1 계층적 바인더 ✅ 검증됨

**타입 정의** (`src/types/document.ts`):

```typescript
export type DocumentType = "folder" | "text";

export interface Document {
  id: string;
  projectId: string;
  parentId?: string;
  type: DocumentType;
  title: string;
  order: number;
  content: string;
  synopsis: string;
  characterIds: string[];
  // ...
}
```

**Repository 구현** (`src/repositories/`):

- `DocumentRepository.ts` - 인터페이스 + buildDocumentTree()
- `LocalDocumentRepository.ts` - Zustand 기반 구현

**Hooks** (`src/hooks/useDocuments.ts`):

- `useDocumentTree(projectId)` - 트리 구조 반환
- `useDocumentContent(id)` - 콘텐츠 읽기/저장
- `useChildDocuments(parentId, projectId)` - 자식 문서

### 1.2 뷰 모드 ✅ 완료

| 모드          | 상태    | 위치                                |
| ------------- | ------- | ----------------------------------- |
| Editor        | ✅ 완료 | `TiptapEditor.tsx`                  |
| Section Strip | ✅ 완료 | `SectionStrip.tsx` (하단 카드 네비) |
| Scrivenings   | ✅ 완료 | `ScriveningsEditor.tsx`             |
| Outline       | ✅ 완료 | `OutlineView.tsx`                   |

---

## 2. 🆕 사이드바 컴포넌트 구조

### 2.1 폴더 구조

```
src/components/editor/sidebar/
├── index.ts          # Export 모음
├── types.ts          # ChapterNode 타입, 유틸리티 함수
├── NodeIcon.tsx      # 타입별 아이콘 컴포넌트
├── ContextMenu.tsx   # 재사용 가능한 컨텍스트 메뉴
├── TreeItem.tsx      # 트리 아이템 (핵심)
└── ChapterTree.tsx   # 메인 컨테이너 컴포넌트
```

### 2.2 ChapterNode 타입

```typescript
export interface ChapterNode {
  id: string;
  title: string;
  type: "part" | "chapter" | "section";
  characterCount?: number;
  isPlot?: boolean;
  isModified?: boolean;
  status?: "todo" | "inProgress" | "done" | "revised";
  children?: ChapterNode[];
}
```

### 2.3 아이콘 시스템

| 타입    | 아이콘     | 색상       |
| ------- | ---------- | ---------- |
| Part    | FolderOpen | Sage-600   |
| Chapter | BookOpen   | Amber-500  |
| Section | FileText   | Stone-400  |
| Plot    | Lightbulb  | Yellow-500 |

### 2.4 상호작용 패턴

| 동작             | 기능                   |
| ---------------- | ---------------------- |
| 싱글 클릭        | 문서 선택 / 폴더 펼침  |
| 더블 클릭        | 인라인 이름 변경       |
| 우클릭 (파일 위) | 객체 컨텍스트 메뉴     |
| 우클릭 (빈 공간) | 컨테이너 컨텍스트 메뉴 |
| F2 (hover 상태)  | 이름 변경 모드         |

### 2.5 상태 표시

```typescript
const statusColors = {
  todo: "bg-stone-400", // 🔴 구상 중
  inProgress: "bg-amber-400", // 🟡 집필 중
  done: "bg-emerald-400", // 🟢 탈고 완료
  revised: "bg-blue-400", // 🔵 퇴고 완료
};
```

---

## 3. 에디터 기능

### 3.1 분할 화면 ✅ 검증됨

**Store** (`src/stores/useEditorStore.ts`):

```typescript
splitView: {
  enabled: boolean;
  direction: "horizontal" | "vertical";
}
toggleSplitView();
```

**구현**: `react-resizable-panels` 사용

### 3.2 집중 모드 ✅ 검증됨

```typescript
isFocusMode: boolean;
toggleFocusMode();
```

### 3.3 🆕 줌 기능 ✅ 구현됨

```typescript
// TiptapEditor.tsx
const [zoom, setZoom] = useState(100);
// Range: 50% - 200%
// 슬라이더 또는 +/- 버튼으로 조절
```

---

## 4. 🆕 가져오기/내보내기

### 4.1 텍스트 가져오기

**지원 형식:**

- TXT (인코딩 자동 감지)
- MD (마크다운)

**스마트 텍스트 정리:**

```typescript
// 하드 줄바꿈 자동 제거, 단락 보존
function cleanupHardLineBreaks(text: string): string {
  // 빈 줄로 구분된 실제 단락 찾기
  // 단락 내 줄바꿈을 공백으로 변환
  // 원본 단락 구조 유지
}
```

### 4.2 내보내기 서비스

**위치**: `src/services/exportService.ts`

**지원 형식:**

- PDF (jsPDF)
- EPUB (예정)
- TXT (순수 텍스트)
- JSON (프로젝트 백업)

---

## 5. 에디터 컴포넌트 목록 (18개)

```
src/components/editor/
├── sidebar/              🆕 6개 컴포넌트
│   ├── ChapterTree.tsx
│   ├── TreeItem.tsx
│   ├── ContextMenu.tsx
│   ├── NodeIcon.tsx
│   ├── types.ts
│   └── index.ts
├── AIAssistantPanel.tsx
├── ConsistencyPanel.tsx
├── DemoHeader.tsx
├── EditorLeftSidebar.tsx
├── EditorRightSidebar.tsx
├── EditorToolbar.tsx
├── ForeshadowingPanel.tsx
├── OutlineView.tsx
├── ScriveningsEditor.tsx
├── SectionStrip.tsx
├── TiptapEditor.tsx
└── editor-prose.css      🆕 에디터 스타일
```

---

## 6. 다음 구현 대상

| 우선순위 | 기능                    | 상태 |
| -------- | ----------------------- | ---- |
| ~~P1~~   | ~~Scrivenings 뷰~~      | ✅   |
| ~~P1~~   | ~~Outline 뷰~~          | ✅   |
| ~~P1~~   | ~~사이드바 분리~~       | ✅   |
| ~~P1~~   | ~~컨텍스트 메뉴~~       | ✅   |
| ~~P2~~   | ~~내보내기 서비스~~     | ✅   |
| P2       | 스냅샷/버전 관리        | ❌   |
| P2       | 드래그 앤 드롭 순서     | ❌   |
| P3       | 인라인 링크 (`[[...]]`) | ❌   |
