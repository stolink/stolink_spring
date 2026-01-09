// 1. 기존 프로젝트 데이터 삭제
MATCH (n:Character {projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor'}) DETACH DELETE n;

// 2. 캐릭터 생성 (20명)
CREATE (v:Character {
  id: 'c-01', 
  projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', 
  name: '장발장', 
  role: 'protagonist', 
  faction: 'ABC의 벗들', 
  imageUrl: 'https://randomuser.me/api/portraits/men/32.jpg', 
  extras_stats_strength: 18, 
  extras_state_hp: 120, 
  extras_personality_traits: ['자애', '강인'], 
  extras_social_rank: 'LEGENDARY'
})
CREATE (j:Character {
  id: 'c-02', 
  projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', 
  name: '자베르', 
  role: 'antagonist', 
  faction: '경찰청', 
  imageUrl: 'https://randomuser.me/api/portraits/men/75.jpg', 
  extras_stats_intelligence: 18, 
  extras_state_hp: 110, 
  extras_personality_traits: ['원칙', '집착'], 
  extras_social_rank: 'ELITE'
})
CREATE (c:Character {id: 'c-03', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '코제트', role: 'supporting', faction: '가족', imageUrl: 'https://randomuser.me/api/portraits/women/44.jpg', extras_personality_traits: ['순수', '희망']})
CREATE (m:Character {id: 'c-04', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '마리우스', role: 'supporting', faction: 'ABC의 벗들', imageUrl: 'https://randomuser.me/api/portraits/men/83.jpg', extras_personality_traits: ['열정', '사랑']})
CREATE (e:Character {id: 'c-05', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '앙졸라', role: 'supporting', faction: 'ABC의 벗들', imageUrl: 'https://randomuser.me/api/portraits/men/22.jpg', extras_personality_traits: ['결연', '카리스마']})
CREATE (ep:Character {id: 'c-06', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '에포닌', role: 'supporting', faction: '테나르디에', imageUrl: 'https://randomuser.me/api/portraits/women/65.jpg', extras_personality_traits: ['헌신', '슬픔']})
CREATE (t:Character {id: 'c-07', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '테나르디에', role: 'antagonist', faction: '테나르디에', imageUrl: 'https://randomuser.me/api/portraits/men/55.jpg', extras_personality_traits: ['교활', '탐욕']})
CREATE (g:Character {id: 'c-08', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '가브로슈', role: 'supporting', faction: '파리', imageUrl: 'https://randomuser.me/api/portraits/men/12.jpg', extras_personality_traits: ['용기', '기민']})
CREATE (my:Character {id: 'c-09', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '미리엘 주교', role: 'mentor', faction: '교회', imageUrl: 'https://randomuser.me/api/portraits/men/45.jpg', extras_personality_traits: ['용서', '자비']})
CREATE (f:Character {id: 'c-10', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '팡틴', role: 'supporting', faction: '가족', imageUrl: 'https://randomuser.me/api/portraits/women/17.jpg', extras_personality_traits: ['희생', '모성']})
CREATE (co:Character {id: 'c-11', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '쿠르페락', role: 'supporting', faction: 'ABC의 벗들', imageUrl: 'https://randomuser.me/api/portraits/men/38.jpg'})
CREATE (com:Character {id: 'c-12', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '콩브페르', role: 'supporting', faction: 'ABC의 벗들', imageUrl: 'https://randomuser.me/api/portraits/men/41.jpg'})
CREATE (gr:Character {id: 'c-13', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '그랑테르', role: 'supporting', faction: 'ABC의 벗들', imageUrl: 'https://randomuser.me/api/portraits/men/49.jpg'})
CREATE (mt:Character {id: 'c-14', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '테나르디에 부인', role: 'antagonist', faction: '테나르디에', imageUrl: 'https://randomuser.me/api/portraits/women/28.jpg'})
CREATE (az:Character {id: 'c-15', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '아젤마', role: 'supporting', faction: '테나르디에', imageUrl: 'https://randomuser.me/api/portraits/women/31.jpg'})
CREATE (fa:Character {id: 'c-16', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '포슐르방', role: 'supporting', faction: '수녀원', imageUrl: 'https://randomuser.me/api/portraits/men/33.jpg'})
CREATE (gi:Character {id: 'c-17', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '질노르망', role: 'mentor', faction: '가족', imageUrl: 'https://randomuser.me/api/portraits/men/35.jpg'})
CREATE (po:Character {id: 'c-18', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '퐁메르시 대령', role: 'mentor', faction: '군', imageUrl: 'https://randomuser.me/api/portraits/men/39.jpg'})
CREATE (bp:Character {id: 'c-19', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '밥티스틴', role: 'supporting', faction: '교회', imageUrl: 'https://randomuser.me/api/portraits/women/10.jpg'})
CREATE (mag:Character {id: 'c-20', projectId: 'a482edc3-a9e5-484c-b7c9-9c67935a1234/editor', name: '마글루아르', role: 'supporting', faction: '교회', imageUrl: 'https://randomuser.me/api/portraits/women/11.jpg'})

// 3. 관계 생성
CREATE (v)-[:RELATED_TO {type: 'conflict', strength: 10}]->(j)
CREATE (j)-[:RELATED_TO {type: 'conflict', strength: 10}]->(v)
CREATE (v)-[:RELATED_TO {type: 'family', strength: 10}]->(c)
CREATE (m)-[:RELATED_TO {type: 'romance', strength: 9}]->(c)
CREATE (c)-[:RELATED_TO {type: 'romance', strength: 9}]->(m)
CREATE (ep)-[:RELATED_TO {type: 'romance', strength: 8}]->(m)
CREATE (e)-[:RELATED_TO {type: 'friendship', strength: 9}]->(m)
CREATE (my)-[:RELATED_TO {type: 'friendship', strength: 10}]->(v)
CREATE (f)-[:RELATED_TO {type: 'family', strength: 10}]->(c)
CREATE (g)-[:RELATED_TO {type: 'friendship', strength: 7}]->(m)
CREATE (co)-[:RELATED_TO {type: 'friendship', strength: 8}]->(m)
RETURN count(*) as total_entities_synced;
