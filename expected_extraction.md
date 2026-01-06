# remi-last.txt 속성 추출 정답표 (Ground Truth)

## 📚 원문 개요
- **출처**: 빅토르 위고 「레 미제라블」(Les Misérables) 중 '샹마티유 사건' 법정 장면
- **분량**: 약 8,700 bytes, 31 lines
- **핵심 사건**: 장 발장(마들렌 시장)이 무고한 사람(샹마티유)을 살리기 위해 법정에서 자신의 정체를 밝히는 장면

---

## 👤 Characters (캐릭터)

### 1. 장 발장 (Jean Valjean) / 마들렌 씨 (M. Madeleine)
| 필드 | 값 |
|------|-----|
| character_id | `char-장발장-001` |
| name | 장 발장 |
| aliases | ["마들렌 씨", "마들렌 시장"] |
| role | **protagonist** |
| gender | male |
| race | human |
| age | 중년 이상 (머리카락이 완전히 하얗게 셈) |
| status | alive |
| **personality.core_traits** | ["자기희생적", "정직함", "용기"] |
| **personality.flaws** | ["과거의 죄책감", "도피성향"] |
| **personality.values** | ["정의", "진실", "구원"] |
| backstory | 빵을 훔쳐 갤러선에서 19년을 보낸 전과자. 몽세뇌르 주교의 은총으로 변화하여 M. 쉬르 M.에서 부자가 되고 시장이 됨. 쁘띠 제르베와 주교에게서 물건을 훔친 과거가 있음. |
| **appearance.physique** | average |
| **appearance.hair_color** | 완전히 하얀색 (한 시간 만에 희끗희끗에서 하얗게 변함) |
| **appearance.attire** | ["코트 (단추가 꼼꼼하게 채워짐)", "모자 (손에 들고 있음)"] |
| **appearance.expression** | 매우 창백하고 약간 떨고 있음 |
| **current_mood.emotion** | 결연함/체념 |
| **current_mood.intensity** | 10 |
| **current_mood.trigger** | 무고한 자를 구하기 위해 자신을 희생하는 결단 |

### 2. 브레베 (Brevet)
| 필드 | 값 |
|------|-----|
| character_id | `char-브레베-001` |
| name | 브레베 |
| role | **minor** |
| gender | male |
| status | alive |
| backstory | 갤러선 전과자. 장 발장과 함께 수감되었음. 체크무늬 짠 멜빵을 입었던 동료. |
| **appearance.expression** | 놀라서 움찔하며 겁에 질림 |

### 3. 슈니디외 (Chenildieu)
| 필드 | 값 |
|------|-----|
| character_id | `char-슈니디외-001` |
| name | 슈니디외 |
| aliases | ["Jenie-Dieu"] |
| role | **minor** |
| gender | male |
| status | alive |
| backstory | 갤러선 전과자. 오른쪽 어깨에 T.F.P. 글자를 지우려고 화상을 입은 흔적이 있음. |
| **appearance.scars_tattoos** | ["오른쪽 어깨 전체에 깊은 화상 (T.F.P. 문신 위)"] |

### 4. 코슈파이유 (Cochepaille)
| 필드 | 값 |
|------|-----|
| character_id | `char-코슈파이유-001` |
| name | 코슈파이유 |
| role | **minor** |
| gender | male |
| status | alive |
| backstory | 갤러선 전과자. 왼쪽 팔 오금 근처에 1815년 3월 1일(황제 칸 상륙일) 날짜 문신이 있음. |
| **appearance.scars_tattoos** | ["왼쪽 팔 오금 근처 화약 문신: 1815년 3월 1일"] |
| **appearance.expression** | 군대식 경례를 함 (겁먹음) |

### 5. 재판장
| 필드 | 값 |
|------|-----|
| character_id | `char-재판장-001` |
| name | 재판장 |
| role | **minor** |
| gender | unknown |
| status | alive |
| **appearance.expression** | 연민과 슬픔 |
| **current_mood.emotion** | 연민 |

### 6. 검사
| 필드 | 값 |
|------|-----|
| character_id | `char-검사-001` |
| name | 검사 |
| role | **minor** |
| gender | male |
| status | alive |

### 7. 자베르 (Javert) - 언급만
| 필드 | 값 |
|------|-----|
| character_id | `char-자베르-001` |
| name | 자베르 |
| role | **supporting** (언급만) |
| status | alive |
| backstory | 장 발장을 알아볼 수 있는 인물로 언급됨 |

### 8. 샹마티유 (피고인) - 암시적
| 필드 | 값 |
|------|-----|
| character_id | `char-샹마티유-001` |
| name | 샹마티유 (피고인/수감자) |
| role | **minor** |
| status | alive |
| backstory | 장 발장으로 오인받아 재판 중인 무고한 사람 |

---

## 🗓 Events (이벤트)

### E001: 장 발장의 법정 등장
| 필드 | 값 |
|------|-----|
| event_id | E001 |
| event_type | revelation |
| narrative_summary | 마들렌 시장이 법정에 나타나 "바로 접니다!"라고 외침 |
| description | 서기의 램프 불빛 아래 창백하고 떨리는 마들렌 씨가 법정에 등장. 한 시간 사이 머리가 완전히 하얗게 변함. |
| participants | ["장 발장", "청중", "서기"] |
| location | 아라스 법원/재판실 |
| importance_score | 10 |
| changes_made | 법정 전체가 충격과 정적에 빠짐 |

### E002: 장 발장의 정체 고백
| 필드 | 값 |
|------|-----|
| event_id | E002 |
| event_type | confession |
| narrative_summary | 장 발장이 "제가 장 발장입니다"라고 선언하며 피고인의 석방과 자신의 체포를 요청 |
| description | "배심원 여러분, 수감자를 석방하도록 명령하십시오! 재판장님, 저를 체포하십시오. 제가 장 발장입니다." |
| participants | ["장 발장", "배심원", "재판장", "검사"] |
| location | 아라스 법원/재판실 |
| importance_score | 10 |
| changes_made | 무덤 같은 침묵, 종교적 공포가 법정을 휩쓸음 |

### E003: 검사의 회유 시도
| 필드 | 값 |
|------|-----|
| event_id | E003 |
| event_type | dialogue |
| narrative_summary | 검사가 마들렌 씨를 정신 이상으로 취급하며 의사를 부르려 함 |
| description | 검사가 마들렌 씨를 존경하는 시장이라 소개하며, 청중석 의사에게 그를 돌보라고 요청 |
| participants | ["검사", "장 발장"] |
| location | 아라스 법원/재판실 |
| importance_score | 6 |

### E004: 장 발장의 과거 고백
| 필드 | 값 |
|------|-----|
| event_id | E004 |
| event_type | confession |
| narrative_summary | 장 발장이 자신의 과거(주교 도둑질, 쁘띠 제르베 사건, 갤러선 경험)를 고백 |
| description | "몽세뇌르 주교님을 턴 건 사실입니다. 쁘띠 제르베를 턴 것도 사실입니다... 갤러선이 저를 변화시켰습니다." |
| participants | ["장 발장", "청중", "재판관들"] |
| location | 아라스 법원/재판실 |
| importance_score | 9 |
| changes_made | 장 발장의 삶의 역사가 밝혀짐 |

### E005: 세 증인을 통한 신원 증명
| 필드 | 값 |
|------|-----|
| event_id | E005 |
| event_type | identification |
| narrative_summary | 장 발장이 브레베, 슈니디외, 코슈파이유의 신체적 특징을 정확히 지적하여 자신의 정체를 증명 |
| description | 브레베의 멜빵, 슈니디외의 어깨 화상(T.F.P.), 코슈파이유의 팔 문신(1815.3.1)을 정확히 기술 |
| participants | ["장 발장", "브레베", "슈니디외", "코슈파이유", "헌병"] |
| location | 아라스 법원/재판실 |
| importance_score | 10 |
| changes_made | 장 발장의 정체가 명백하게 확인됨 |

### E006: 법정의 경외와 침묵
| 필드 | 값 |
|------|-----|
| event_id | E006 |
| event_type | climax |
| narrative_summary | 법정 전체가 장 발장의 자기희생에 감동받아 역할을 잊고 경외심에 빠짐 |
| description | "더 이상 판사도, 고발자도, 헌병도 없었다. 응시하는 눈과 동정하는 마음들뿐이었다." |
| participants | ["장 발장", "청중 전체", "재판관들", "배심원"] |
| location | 아라스 법원/재판실 |
| importance_score | 10 |
| changes_made | 모든 사람이 숭고한 자기희생의 순간에 압도됨 |

---

## 🏠 Settings (장소/배경)

### 아라스 법원/재판실 (Arras Courthouse)
| 필드 | 값 |
|------|-----|
| setting_id | `setting-아라스법원-001` |
| name | 아라스 법원 재판실 |
| location_type | courthouse/courtroom |
| atmosphere_keywords | ["긴장", "엄숙", "충격", "정적", "종교적 경외"] |
| lighting_description | 서기의 램프 불빛 (얼굴을 비춤) |
| description | 배심원, 재판장, 검사, 청중이 있는 법정. 헌병이 램프를 가까이 대어 증인의 팔을 비춤. |
| story_significance | 장 발장의 자기희생적 고백이 이루어지는 클라이맥스 장소 |
| is_primary_location | true |

---

## 🔗 Relationships (관계)

| source | target | type | strength | description | bidirectional |
|--------|--------|------|----------|-------------|---------------|
| 장 발장 | 브레베 | KNOWS | 7 | 갤러선 동료 전과자, 체크무늬 멜빵 기억 | true |
| 장 발장 | 슈니디외 | KNOWS | 7 | 갤러선 동료 전과자, 어깨 화상 기억 | true |
| 장 발장 | 코슈파이유 | KNOWS | 7 | 갤러선 동료 전과자, 문신 기억 | true |
| 장 발장 | 자베르 | KNOWS | 9 | 장 발장을 알아볼 수 있는 인물 (긴장 관계) | true |
| 장 발장 | 샹마티유 | PROTECTS | 10 | 무고한 피고인을 구하기 위해 정체를 밝힘 | false |
| 장 발장 | 몽세뇌르 주교 | TRANSFORMED_BY | 10 | "관용과 친절이 저를 구했다" (언급) | false |
| 재판장 | 검사 | COLLEAGUE | 6 | 빠른 신호를 교환하는 동료 관계 | true |

---

## 📋 요약 통계

| 항목 | 수량 |
|------|------|
| **캐릭터** | 8명 (등장 6명 + 언급 2명) |
| **이벤트** | 6개 |
| **장소** | 1개 (아라스 법원) |
| **관계** | 7개 |

---

## ⚠️ 주의사항

1. **언급만 되는 캐릭터**: 자베르, 몽세뇌르 주교, 쁘띠 제르베는 직접 등장하지 않고 대화 중 언급됨
2. **역사적 맥락**: 1815년 3월 1일 = 나폴레옹의 칸 상륙일 (back from Elba)
3. **인물 별칭**: 마들렌 씨 = 장 발장 (같은 인물)
4. **감정 표현**: 장 발장의 미소는 "승리의 미소이자 절망의 미소"로 묘사됨 - 복합적 감정
5. **상징적 변화**: 한 시간 만에 머리가 하얗게 변함 = 극심한 스트레스/결단의 무게
