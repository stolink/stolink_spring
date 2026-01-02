// StoLink 프로젝트 더미 캐릭터 데이터 추가 스크립트
// Project ID: 0b881641-b75d-44d6-9ef6-ddb3cb329ff6

// 1. 기존 데이터 정리 (선택적)
// MATCH (c:Character {project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6'}) DETACH DELETE c;

// 2. 캐릭터 생성
CREATE (c1:Character {
  id: '550e8400-e29b-41d4-a716-446655440001',
  project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6',
  character_id: 'char-이지훈-001',
  name: '이지훈',
  role: 'protagonist',
  status: 'alive',
  age: 28,
  gender: '남성',
  race: '한국인',
  mbti: 'INTJ',
  backstory: '평범한 회사원이었으나 어느 날 갑자기 시스템 메시지를 받게 되면서 인생이 바뀌게 된다. 어릴 적 부모님을 잃고 고아원에서 자랐으며, 이로 인해 독립심이 강하고 타인을 쉽게 믿지 않는다.',
  faction: '무소속',
  imageUrl: 'https://example.com/images/jihoon.jpg',
  aliasesJson: '["지훈", "훈이", "시스템 마스터"]',
  appearanceJson: '{"physique": "건장한 체형", "skin_tone": "밝은 피부", "eyes": "갈색", "hair": "짧은 흑발", "attire": "캐주얼한 정장"}',
  personalityJson: '{"core_traits": ["냉철함", "논리적", "결단력"], "flaws": ["고집이 셈", "감정 표현 서툼"], "values": ["정의", "효율성", "자립"]}',
  motivation: '진실을 밝히고 시스템의 정체를 알아내는 것',
  firstAppearance: '1장'
})

CREATE (c2:Character {
  id: '550e8400-e29b-41d4-a716-446655440002',
  project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6',
  character_id: 'char-박서연-002',
  name: '박서연',
  role: 'supporting',
  status: 'alive',
  age: 26,
  gender: '여성',
  race: '한국인',
  mbti: 'ENFP',
  backstory: '천재 해커로 알려진 프리랜서. 어릴 적부터 컴퓨터에 재능을 보였으며, 대학 중퇴 후 독학으로 해킹 기술을 익혔다. 정의감이 강해 약자를 돕는 일에 관심이 많다.',
  faction: '레지스탕스',
  imageUrl: 'https://example.com/images/seoyeon.jpg',
  aliasesJson: '["서연", "해커S", "디지털 요정"]',
  appearanceJson: '{"physique": "날씬한 체형", "skin_tone": "하얀 피부", "eyes": "검은색", "hair": "긴 갈색 머리", "attire": "후드티와 청바지"}',
  personalityJson: '{"core_traits": ["명랑함", "창의적", "호기심 많음"], "flaws": ["산만함", "계획성 부족"], "values": ["자유", "정의", "우정"]}',
  motivation: '시스템의 독점을 막고 모두가 평등한 세상을 만드는 것',
  firstAppearance: '3장'
})

CREATE (c3:Character {
  id: '550e8400-e29b-41d4-a716-446655440003',
  project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6',
  character_id: 'char-강민석-003',
  name: '강민석',
  role: 'antagonist',
  status: 'alive',
  age: 35,
  gender: '남성',
  race: '한국인',
  mbti: 'ENTJ',
  backstory: '대기업 회장의 아들로 태어나 모든 것을 가진 듯 보였으나, 시스템의 존재를 알게 된 후 그것을 독점하려 한다. 어릴 적부터 승부욕이 강했으며, 목적을 위해서는 수단을 가리지 않는다.',
  faction: '섀도우 코퍼레이션',
  imageUrl: 'https://example.com/images/minseok.jpg',
  aliasesJson: '["민석", "강회장", "시스템 헌터"]',
  appearanceJson: '{"physique": "근육질 체형", "skin_tone": "중간 피부", "eyes": "날카로운 검은 눈", "hair": "짧고 단정한 흑발", "attire": "고급 정장"}',
  personalityJson: '{"core_traits": ["야망", "카리스마", "냉혹함"], "flaws": ["오만함", "공감 능력 부족"], "values": ["권력", "성공", "지배"]}',
  motivation: '시스템을 독점하여 세계를 지배하는 것',
  firstAppearance: '5장'
})

CREATE (c4:Character {
  id: '550e8400-e29b-41d4-a716-446655440004',
  project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6',
  character_id: 'char-최은주-004',
  name: '최은주',
  role: 'mentor',
  status: 'alive',
  age: 52,
  gender: '여성',
  race: '한국인',
  mbti: 'INFJ',
  backstory: '과거 시스템의 첫 번째 사용자 중 한 명. 시스템의 위험성을 깨닫고 은퇴했으나, 이지훈의 등장으로 다시 나서게 된다. 과거에는 국가 정보기관 요원이었다.',
  faction: '옛 수호자들',
  imageUrl: 'https://example.com/images/eunju.jpg',
  aliasesJson: '["은주", "최 선생", "첫 번째 수호자"]',
  appearanceJson: '{"physique": "날씬한 체형", "skin_tone": "약간 탄 피부", "eyes": "부드러운 갈색", "hair": "단발 흰머리", "attire": "단정한 정장 재킷"}',
  personalityJson: '{"core_traits": ["지혜로움", "신중함", "보호 본능"], "flaws": ["과거에 얽매임", "지나친 걱정"], "values": ["균형", "보호", "지식"]}',
  motivation: '시스템의 균형을 유지하고 차세대를 올바르게 이끄는 것',
  firstAppearance: '7장'
})

CREATE (c5:Character {
  id: '550e8400-e29b-41d4-a716-446655440005',
  project_id: '0b881641-b75d-44d6-9ef6-ddb3cb329ff6',
  character_id: 'char-김태양-005',
  name: '김태양',
  role: 'sidekick',
  status: 'alive',
  age: 24,
  gender: '남성',
  race: '한국인',
  mbti: 'ESFJ',
  backstory: '이지훈의 후배로 회사에서 만났다. 순수하고 밝은 성격으로 팀의 분위기 메이커 역할을 한다. 시스템 능력은 없지만 뛰어난 사교력과 정보 수집 능력으로 도움을 준다.',
  faction: '무소속',
  imageUrl: 'https://example.com/images/taeyang.jpg',
  aliasesJson: '["태양", "태양이", "인포맨"]',
  appearanceJson: '{"physique": "보통 체형", "skin_tone": "밝은 피부", "eyes": "큰 검은 눈", "hair": "파마한 갈색 머리", "attire": "캐주얼한 티셔츠와 청바지"}',
  personalityJson: '{"core_traits": ["친절함", "사교적", "낙천적"], "flaws": ["너무 순진함", "우유부단함"], "values": ["우정", "화합", "도움"]}',
  motivation: '친구들을 돕고 모두가 행복한 결말을 만드는 것',
  firstAppearance: '2장'
});

// 3. 관계 생성
// 이지훈 → 박서연 (친구)
MATCH (c1:Character {character_id: 'char-이지훈-001'})
MATCH (c2:Character {character_id: 'char-박서연-002'})
CREATE (c1)-[:RELATED_TO {
  source: 'char-이지훈-001',
  type: 'friend',
  strength: 8,
  description: '신뢰하는 동료이자 친구',
  since: '3장',
  history: '처음에는 경계했으나 함께 위기를 극복하며 깊은 신뢰 관계를 형성',
  bidirectional: true,
  revealedInChapter: 3
}]->(c2);

// 박서연 → 이지훈 (친구, 양방향)
MATCH (c2:Character {character_id: 'char-박서연-002'})
MATCH (c1:Character {character_id: 'char-이지훈-001'})
CREATE (c2)-[:RELATED_TO {
  source: 'char-박서연-002',
  type: 'friend',
  strength: 8,
  description: '든든한 파트너이자 친구',
  since: '3장',
  history: '이지훈의 냉철함과 자신의 창의성이 잘 맞는다고 생각',
  bidirectional: true,
  revealedInChapter: 3
}]->(c1);

// 이지훈 → 강민석 (적)
MATCH (c1:Character {character_id: 'char-이지훈-001'})
MATCH (c3:Character {character_id: 'char-강민석-003'})
CREATE (c1)-[:RELATED_TO {
  source: 'char-이지훈-001',
  type: 'enemy',
  strength: 9,
  description: '시스템을 두고 대립하는 숙적',
  since: '5장',
  history: '첫 만남부터 충돌했으며 서로의 신념이 정반대',
  bidirectional: true,
  revealedInChapter: 5
}]->(c3);

// 강민석 → 이지훈 (적, 양방향)
MATCH (c3:Character {character_id: 'char-강민석-003'})
MATCH (c1:Character {character_id: 'char-이지훈-001'})
CREATE (c3)-[:RELATED_TO {
  source: 'char-강민석-003',
  type: 'enemy',
  strength: 9,
  description: '목표 달성을 방해하는 장애물',
  since: '5장',
  history: '이지훈을 제거 대상으로 여기지만 동시에 능력을 인정',
  bidirectional: true,
  revealedInChapter: 5
}]->(c1);

// 이지훈 → 최은주 (멘토 관계, 존경)
MATCH (c1:Character {character_id: 'char-이지훈-001'})
MATCH (c4:Character {character_id: 'char-최은주-004'})
CREATE (c1)-[:RELATED_TO {
  source: 'char-이지훈-001',
  type: 'friend',
  strength: 7,
  description: '시스템에 대해 가르쳐주는 스승',
  since: '7장',
  history: '처음에는 경계했으나 점차 신뢰하게 됨',
  bidirectional: false,
  revealedInChapter: 7
}]->(c4);

// 박서연 → 강민석 (적)
MATCH (c2:Character {character_id: 'char-박서연-002'})
MATCH (c3:Character {character_id: 'char-강민석-003'})
CREATE (c2)-[:RELATED_TO {
  source: 'char-박서연-002',
  type: 'enemy',
  strength: 7,
  description: '시스템 독점을 막기 위해 대적',
  since: '6장',
  history: '해킹 전투에서 여러 차례 충돌',
  bidirectional: true,
  revealedInChapter: 6
}]->(c3);

// 강민석 → 박서연 (적, 양방향)
MATCH (c3:Character {character_id: 'char-강민석-003'})
MATCH (c2:Character {character_id: 'char-박서연-002'})
CREATE (c3)-[:RELATED_TO {
  source: 'char-강민석-003',
  type: 'enemy',
  strength: 7,
  description: '성가신 해커',
  since: '6장',
  history: '능력은 인정하지만 제거 대상',
  bidirectional: true,
  revealedInChapter: 6
}]->(c2);

// 이지훈 → 김태양 (친구)
MATCH (c1:Character {character_id: 'char-이지훈-001'})
MATCH (c5:Character {character_id: 'char-김태양-005'})
CREATE (c1)-[:RELATED_TO {
  source: 'char-이지훈-001',
  type: 'friend',
  strength: 6,
  description: '회사 후배이자 도움을 주는 친구',
  since: '2장',
  history: '함께 일하면서 친해짐',
  bidirectional: true,
  revealedInChapter: 2
}]->(c5);

// 김태양 → 이지훈 (친구, 양방향)
MATCH (c5:Character {character_id: 'char-김태양-005'})
MATCH (c1:Character {character_id: 'char-이지훈-001'})
CREATE (c5)-[:RELATED_TO {
  source: 'char-김태양-005',
  type: 'friend',
  strength: 7,
  description: '존경하는 선배',
  since: '2장',
  history: '선배를 따르며 무엇이든 돕고 싶어함',
  bidirectional: true,
  revealedInChapter: 2
}]->(c1);

// 박서연 → 김태양 (친구)
MATCH (c2:Character {character_id: 'char-박서연-002'})
MATCH (c5:Character {character_id: 'char-김태양-005'})
CREATE (c2)-[:RELATED_TO {
  source: 'char-박서연-002',
  type: 'friend',
  strength: 5,
  description: '팀원이자 친구',
  since: '4장',
  history: '같이 작전을 수행하며 친해짐',
  bidirectional: true,
  revealedInChapter: 4
}]->(c5);

// 김태양 → 박서연 (친구, 양방향)
MATCH (c5:Character {character_id: 'char-김태양-005'})
MATCH (c2:Character {character_id: 'char-박서연-002'})
CREATE (c5)-[:RELATED_TO {
  source: 'char-김태양-005',
  type: 'friend',
  strength: 5,
  description: '밝은 성격이 잘 맞는 친구',
  since: '4장',
  history: '서연의 해킹 실력을 존경함',
  bidirectional: true,
  revealedInChapter: 4
}]->(c2);

// 최은주 → 강민석 (과거의 인연, 복잡한 관계)
MATCH (c4:Character {character_id: 'char-최은주-004'})
MATCH (c3:Character {character_id: 'char-강민석-003'})
CREATE (c4)-[:RELATED_TO {
  source: 'char-최은주-004',
  type: 'enemy',
  strength: 6,
  description: '과거 함께했던 동료였으나 길이 갈라짐',
  since: '과거',
  history: '강민석의 아버지와 함께 일했던 적이 있으나 이념 차이로 결별',
  bidirectional: false,
  revealedInChapter: 10
}]->(c3);
