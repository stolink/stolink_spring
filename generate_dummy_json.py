import json
import random
import uuid

# Configuration
NUM_CHARACTERS = 300
PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7" # The project the user is looking at

KOREAN_LAST_NAMES = ["김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황", "안", "송", "전", "홍"]
KOREAN_FIRST_NAMES = ["민준", "서준", "도윤", "예준", "시우", "하준", "주원", "지호", "지후", "준서", "서연", "서윤", "지우", "서현", "하은", "하윤", "민서", "지유", "윤서", "지아"]

ROLES = ["protagonist", "antagonist", "sidekick", "mentor", "supporting", "other"]
RELATION_TYPES_POOL = [
    ["FRIEND"], ["ENEMY"], ["RIVAL"], ["LOVER"], ["FAMILY"], ["MENTOR"], ["APPRENTICE"],
    ["ALLY"], ["BETRAYER"], ["CHILDHOOD_FRIEND"], ["BUSINESS_PARTNER"],
    ["FRIEND", "RIVAL"], ["ENEMY", "FAMILY"], ["MENTOR", "FATHER_FIGURE"],
    ["LOVER", "EX"], ["ALLY", "SPY"], ["MASTER", "SERVANT"], ["GUARDIAN", "PARENT"]
]

def generate_unique_names(n):
    names = set()
    while len(names) < n:
        names.add(random.choice(KOREAN_LAST_NAMES) + random.choice(KOREAN_FIRST_NAMES))
    return list(names)

UNIQUE_NAMES = generate_unique_names(NUM_CHARACTERS)

def generate_profile(i):
    if i == 0:
        name = "장발장"
        role = "protagonist"
    elif i == 1:
        name = "자베르"
        role = "antagonist"
    elif i == 2:
        name = "포슐르방"
        role = "supporting"
    elif i == 3:
        name = "가브로슈"
        role = "sidekick"
    elif i == 4:
        name = "마리우스"
        role = "protagonist"
    elif i == 5:
        name = "코제트"
        role = "supporting"
    else:
        name = UNIQUE_NAMES[i]
        role = random.choices(ROLES, weights=[1, 2, 5, 3, 20, 10])[0]

    char_id = f"char_{i:03d}"
    return {
        "_id": char_id,
        "role": role,
        "status": "active",
        "aliases": [name, f"캐릭터_{i}"],
        "profile": {
            "character_id": char_id,
            "name": name,
            "age": random.randint(10, 80),
            "gender": random.choice(["남성", "여성", "기타"]),
            "race": "인간",
            "mbti": random.choice(["INTJ", "ENFP", "ISTJ", "ENTJ", "INFJ"]),
            "backstory": f"{name}은(는) 이 세계관의 중요한 {role}입니다."
        },
        "appearance": {
            "physique": "보통",
            "attire": ["평상복"]
        },
        "imageUrl": f"https://api.dicebear.com/7.x/pixel-art/svg?seed={char_id}"
    }

def generate_data():
    characters = [generate_profile(i) for i in range(NUM_CHARACTERS)]
    relationships = []

    # Fixed relationships
    relationships.append({
        "source": "장발장",
        "target": "자베르",
        "relation_types": ["ENEMY"],
        "strength": 10,
        "description": "숙명의 라이벌",
        "bidirectional": True
    })
    relationships.append({
        "source": "장발장",
        "target": "포슐르방",
        "relation_types": ["ALLY"],
        "strength": 8,
        "description": "생명의 은인",
        "bidirectional": True
    })
    relationships.append({
        "source": "마리우스",
        "target": "코제트",
        "relation_types": ["LOVER"],
        "strength": 10,
        "description": "운명적 사랑",
        "bidirectional": True
    })

    # Random relationships
    for i in range(NUM_CHARACTERS):
        num_rels = random.randint(0, 2)
        source_name = characters[i]["profile"]["name"]

        for _ in range(num_rels):
            target_idx = random.randint(0, NUM_CHARACTERS - 1)
            if target_idx == i: continue
            target_name = characters[target_idx]["profile"]["name"]

            if any(r["source"] == source_name and r["target"] == target_name for r in relationships):
                continue

            types = random.choice(RELATION_TYPES_POOL)
            relationships.append({
                "source": source_name,
                "target": target_name,
                "relation_types": types,
                "strength": random.randint(1, 10),
                "description": f"{source_name}와 {target_name}의 관계",
                "bidirectional": random.choice([True, False])
            })

    data = {
        "jobId": f"dummy::{PROJECT_ID}::{uuid.uuid4()}",
        "status": "COMPLETED",
        "result": {
            "characters": characters,
            "relationships": relationships,
            "settings": [],
            "events": []
        }
    }

    with open("dummy_data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"Generated {NUM_CHARACTERS} Korean characters for project {PROJECT_ID} in dummy_data.json")

if __name__ == "__main__":
    generate_data()
