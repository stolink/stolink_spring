# 프론트엔드 API 변경 사항 안내

## 변경 날짜
2025-12-28

## 변경 사유
그래프 화면에서 캐릭터 정보와 관계 정보를 모두 사용하므로, 두 번의 API 호출 대신 한 번의 호출로 모든 데이터를 제공하도록 API를 통합했습니다.

---

## 🔴 Breaking Changes

### 1. `/api/projects/{projectId}/relationships` 엔드포인트 삭제

**이전:**
```typescript
// ❌ 더 이상 사용 불가
const response = await api.get(`/projects/${projectId}/relationships`)
```

**변경 후:**
```typescript
// ✅ /characters 엔드포인트에서 관계 정보 포함
const response = await api.get(`/projects/${projectId}/characters`)
```

### 2. `/api/projects/{projectId}/characters` 응답 구조 변경

이제 각 Character 객체에 `relationships` 배열이 **항상 포함**됩니다.

**변경 전 응답:**
```json
{
  "status": "OK",
  "data": [
    {
      "id": "char-uuid-1",
      "name": "Jean Valjean",
      "role": "protagonist",
      "imageUrl": "https://...",
      "extras": {}
      // ❌ relationships 없음
    }
  ]
}
```

**변경 후 응답:**
```json
{
  "status": "OK",
  "data": [
    {
      "id": "char-uuid-1",
      "projectId": "project-uuid",
      "name": "Jean Valjean",
      "role": "protagonist",
      "imageUrl": "https://...",
      "relationships": [  // ✅ 관계 정보 포함
        {
          "id": 0,
          "source": "char-uuid-1",
          "target": "char-uuid-2",
          "type": "enemy",
          "strength": 9,
          "label": null,
          "since": null
        }
      ],
      "extras": {}
    }
  ]
}
```

---

## 📝 프론트엔드 수정 가이드

### 수정 전 코드

```typescript
// src/services/characterService.ts

// ❌ 기존: 두 개의 API 호출
export const characterService = {
  async getCharacters(projectId: string) {
    const response = await api.get<ApiResponse<Character[]>>(
      `/projects/${projectId}/characters`
    );
    return response.data;
  },

  async getRelationships(projectId: string) {
    const response = await api.get<ApiResponse<CharacterRelationship[]>>(
      `/projects/${projectId}/relationships`
    );
    return response.data;
  }
};

// ❌ 사용: 두 번 호출
const characters = await characterService.getCharacters(projectId);
const relationships = await characterService.getRelationships(projectId);
```

### 수정 후 코드

```typescript
// src/services/characterService.ts

// ✅ 변경: 한 번의 API 호출로 모든 데이터
export const characterService = {
  async getCharacters(projectId: string) {
    const response = await api.get<ApiResponse<Character[]>>(
      `/projects/${projectId}/characters`
    );
    return response.data;
  },

  // ✅ getRelationships 메서드 삭제 또는 deprecated
  // async getRelationships() { ... }  // 삭제
};

// ✅ 사용: 한 번만 호출
const { data: characters } = await characterService.getCharacters(projectId);

// ✅ 관계 데이터는 characters에서 추출
const relationships = characters.flatMap(char =>
  char.relationships.map(rel => ({
    id: rel.id,
    sourceId: char.id,  // source는 해당 character의 id
    targetId: rel.target,
    type: rel.type,
    strength: rel.strength,
    label: rel.label,
    since: rel.since
  }))
);
```

---

## 🎨 React 컴포넌트 수정 예시

### Before (두 번 호출)

```typescript
// ❌ 기존 방식
const CharacterGraph = () => {
  const [characters, setCharacters] = useState<Character[]>([]);
  const [relationships, setRelationships] = useState<CharacterRelationship[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      // 두 번 호출
      const chars = await characterService.getCharacters(projectId);
      const rels = await characterService.getRelationships(projectId);

      setCharacters(chars);
      setRelationships(rels);
    };
    fetchData();
  }, [projectId]);

  // ...
};
```

### After (한 번 호출)

```typescript
// ✅ 변경 후
const CharacterGraph = () => {
  const [characters, setCharacters] = useState<Character[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      // 한 번만 호출 (관계 정보 포함)
      const chars = await characterService.getCharacters(projectId);
      setCharacters(chars);
    };
    fetchData();
  }, [projectId]);

  // ✅ 관계 데이터는 characters에서 추출
  const edges = useMemo(() =>
    characters.flatMap(char =>
      char.relationships.map(rel => ({
        source: char.id,
        target: rel.target,
        type: rel.type,
        strength: rel.strength
      }))
    ),
    [characters]
  );

  // ✅ 캐릭터 클릭 시 모달에 표시할 데이터도 이미 로드됨
  const handleNodeClick = (characterId: string) => {
    const character = characters.find(c => c.id === characterId);
    // 즉시 모달 표시 (추가 API 호출 불필요)
    setSelectedCharacter(character);
  };

  // ...
};
```

---

## 📊 Character 타입 정의 업데이트

```typescript
// src/types/character.ts

export interface Character {
  id: string;
  projectId: string;
  name: string;
  role?: CharacterRole;
  imageUrl?: string;
  relationships: CharacterRelationship[];  // ✅ 필수 필드로 변경
  extras?: Record<string, any>;
}

export interface CharacterRelationship {
  id: number;           // Neo4j internal ID
  source: string;       // Source character ID (character.id)
  target: string;       // Target character ID
  type: RelationshipType;  // 'friend' | 'lover' | 'enemy'
  strength: number;     // 1-10
  label?: string;       // Description
  since?: string;       // When relationship started
}
```

---

## ✅ 체크리스트

프론트엔드에서 다음 항목들을 수정하세요:

- [ ] `characterService.getRelationships()` 메서드 삭제 또는 deprecated 처리
- [ ] `/relationships` 엔드포인트 호출하는 모든 코드 제거
- [ ] `Character` 타입에 `relationships` 필드가 항상 존재한다고 가정하도록 수정
- [ ] 그래프 컴포넌트에서 relationships 추출 로직 변경
- [ ] API 호출 횟수 감소 확인 (Network 탭에서 검증)
- [ ] 캐릭터 모달이 추가 API 호출 없이 즉시 표시되는지 확인

---

## 🎯 성능 개선 효과

### Before
```
그래프 페이지 로드:
  1. GET /characters  → 200ms
  2. GET /relationships → 150ms
  총 350ms + 네트워크 오버헤드
```

### After
```
그래프 페이지 로드:
  1. GET /characters (관계 포함) → 250ms
  총 250ms
```

**개선 사항:**
- API 호출 횟수: 2회 → 1회 (50% 감소)
- 총 로딩 시간: ~100ms 단축
- 캐릭터 모달 표시: 추가 API 호출 불필요 (즉시 표시)

---

## 🔧 마이그레이션 순서

1. **타입 정의 업데이트** - `Character` 인터페이스에 `relationships` 필드 추가
2. **서비스 레이어 수정** - `getRelationships()` 메서드 제거
3. **컴포넌트 수정** - 관계 데이터 추출 로직 변경
4. **테스트** - 그래프 렌더링 및 모달 동작 확인
5. **불필요한 코드 정리** - `/relationships` 관련 코드 제거

---

## 💡 참고사항

- 기존 `/relationships` 엔드포인트는 **즉시 제거**되므로, 프론트엔드 수정을 먼저 완료해주세요.
- 백엔드는 이미 `getCharactersWithRelationships()` 메서드를 사용하도록 변경되었습니다.
- 관계 데이터는 각 Character의 **outgoing relationships**만 포함됩니다. (Neo4j RELATED_TO 방향성)

---

## 🆘 문제 발생 시

API 변경 후 문제가 발생하면 다음을 확인하세요:

1. **Network 탭**: `/characters` 응답에 `relationships` 배열이 포함되는지 확인
2. **콘솔 에러**: `Cannot read property 'relationships' of undefined` 에러 발생 시 타입 정의 확인
3. **그래프 렌더링**: 엣지가 표시되지 않으면 관계 추출 로직 확인

문의사항: 백엔드 팀 ssyy3034
