import json
import random
import uuid

# Configuration
NUM_CHARACTERS = 3
PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7"

KOREAN_LAST_NAMES = ["김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황", "안", "송", "전", "홍"]
KOREAN_FIRST_NAMES = ["민준", "서준", "도윤", "예준", "시우", "하준", "주원", "지호", "지후", "준서", "서연", "서윤", "지우", "서현", "하은", "하윤", "민서", "지유", "윤서", "지아"]

ROLES = ["protagonist", "antagonist", "sidekick", "mentor", "supporting", "other"]

# Faction Definitions
FACTIONS = ["왕실 근위대", "반란군", "마법사 협회", "암흑 길드", "상인 연합"]

# Faction Relationship Matrix (Symmetric for now, but logic can handle asymmetry)
# HOSTILE: Enemy, Rival
# FRIENDLY: Ally, Friend
# NEUTRAL: Business Partner, Acquaintance, None
FACTION_RELATIONSHIPS = {
    ("왕실 근위대", "반란군"): "HOSTILE",
    ("왕실 근위대", "암흑 길드"): "HOSTILE",
    ("왕실 근위대", "마법사 협회"): "NEUTRAL",
    ("왕실 근위대", "상인 연합"): "NEUTRAL",

    ("반란군", "암흑 길드"): "NEUTRAL", # Sometimes they work together
    ("반란군", "마법사 협회"): "FRIENDLY",
    ("반란군", "상인 연합"): "FRIENDLY", # Funding

    ("마법사 협회", "암흑 길드"): "HOSTILE",
    ("마법사 협회", "상인 연합"): "NEUTRAL",

    ("암흑 길드", "상인 연합"): "NEUTRAL", # Black market deals
}

def get_faction_stance(f1, f2):
    if f1 == f2: return "SAME"
    key = tuple(sorted((f1, f2)))
    return FACTION_RELATIONSHIPS.get(key, "NEUTRAL")

def generate_unique_names(n):
    names = set()
    while len(names) < n:
        names.add(random.choice(KOREAN_LAST_NAMES) + random.choice(KOREAN_FIRST_NAMES))
    return list(names)

UNIQUE_NAMES = generate_unique_names(NUM_CHARACTERS)

def create_character_profile(i, assigned_faction, is_leader=False):
    name = UNIQUE_NAMES[i]
    role = "supporting"

    # Special assignments for fixed names if we want to keep them,
    # but for this logic, we'll mostly use generated names or overwrite specific indices if needed.
    # Overwriting specific indices to keep familiar names
    if i == 0: name = "장발장"; role = "protagonist"
    if i == 1: name = "자베르"; role = "antagonist"
    if i == 2: name = "포슐르방"
    if i == 3: name = "가브로슈"; role = "sidekick"
    if i == 4: name = "마리우스"; role = "protagonist"
    if i == 5: name = "코제트"

    if is_leader:
        role = "mentor" if role == "supporting" else role # Leaders usually have significant roles
        description = f"{assigned_faction}의 수장"
    else:
        role = random.choices(ROLES, weights=[1, 2, 5, 3, 20, 10])[0] if role == "supporting" else role
        description = f"{assigned_faction}의 구성원"

    char_id = f"char_{i:03d}"

    return {
        "_id": char_id,
        "role": role,
        "status": "active",
        "aliases": [name, f"{assigned_faction}_{'Leader' if is_leader else 'Member'}_{i}"],
        "profile": {
            "character_id": char_id,
            "name": name,
            "age": random.randint(20, 60),
            "gender": random.choice(["남성", "여성"]),
            "race": "인간",
            "mbti": random.choice(["INTJ", "ENFP", "ISTJ", "ENTJ", "INFJ"]),
            "backstory": f"{name}은(는) {description}입니다.",
            "faction": {
                "name": assigned_faction
            },
            "is_leader": is_leader # Custom field for generation logic
        },
        "appearance": {
            "physique": "강인함" if is_leader else "보통",
            "attire": [f"{assigned_faction} 제복" if "근위대" in assigned_faction else "평상복"]
        },
        "imageUrl": f"https://api.dicebear.com/7.x/pixel-art/svg?seed={char_id}"
    }

def generate_data():
    characters = []
    faction_buckets = {f: [] for f in FACTIONS}

    # 1. Assign Characters to Factions
    char_idx = 0

    while char_idx < NUM_CHARACTERS:
        # Custom logic for small dataset (N=3)
        if char_idx == 0:
            assigned_faction = FACTIONS[0]
            is_leader = True
        elif char_idx == 1:
             assigned_faction = FACTIONS[0]
             is_leader = False
        elif char_idx == 2:
             assigned_faction = FACTIONS[1]
             is_leader = True
        else:
             assigned_faction = FACTIONS[char_idx % len(FACTIONS)]
             is_leader = False

        # Name safety
        if char_idx < len(UNIQUE_NAMES):
            # Normal flow
            pass
        else:
            # Fallback if I screwed up UNIQUE_NAMES length
            pass # Actually create_character_profile uses global UNIQUE_NAMES

        char = create_character_profile(char_idx, assigned_faction, is_leader=is_leader)
        characters.append(char)
        faction_buckets[assigned_faction].append(char)
        char_idx += 1

    # Fill remaining characters (Removed loop)
    # dangling lines removed

    relationships = []

    # 2. Generate Relationships based on Logic

    # Helper to add relationship
    def add_rel(source, target, types, strength, desc):
        # Check for duplicates
        if any(r["source"] == source["profile"]["name"] and r["target"] == target["profile"]["name"] for r in relationships):
            return

        relationships.append({
            "source": source["profile"]["name"],
            "target": target["profile"]["name"],
            "relation_types": types,
            "strength": strength,
            "description": desc,
            "bidirectional": True
        })

    for i, source in enumerate(characters):
        source_faction = source["profile"]["faction"]["name"]
        source_is_leader = source["profile"].get("is_leader", False)

        # Decide how many relationships this character should have
        # Leaders have more connections
        num_rels = random.randint(3, 6) if source_is_leader else random.randint(1, 4)

        # We try to create 'num_rels' relationships, picking targets smartly
        attempts = 0
        created = 0
        while created < num_rels and attempts < 20:
            attempts += 1
            target = random.choice(characters)
            if source == target: continue

            target_faction = target["profile"]["faction"]["name"]
            target_is_leader = target["profile"].get("is_leader", False)

            stance = get_faction_stance(source_faction, target_faction)

            rel_type = []
            strength = 5
            desc = ""

            should_create = False

            if stance == "SAME":
                # Intra-faction: Highly cohesive
                if source_is_leader or target_is_leader:
                    if random.random() < 0.9: # Very high chance of knowing leader/leader knowing member
                        rel_type = ["MENTOR", "APPRENTICE"] if source_is_leader else ["APPRENTICE", "MENTOR"]
                        if source_is_leader and target_is_leader: rel_type = ["ALLY"]
                        strength = random.randint(7, 10)
                        desc = "절대적인 충성"
                        if random.random() < 0.5:
                             rel_type = ["ALLY"]
                             desc = "신뢰하는 동료"
                        should_create = True
                else:
                    # Member to Member: High chance of being friends
                    if random.random() < 0.8: # Increased from 0.4
                        rel_type = ["FRIEND", "COWORKER"]
                        strength = random.randint(6, 9)
                        desc = "동료"
                        should_create = True

            elif stance == "HOSTILE":
                # Inter-faction Hostile
                if source_is_leader and target_is_leader:
                    # Leader vs Leader = Strong Enemy
                    rel_type = ["ENEMY"]
                    strength = 10
                    desc = "숙적"
                    should_create = True # Always create if picked
                elif source_is_leader or target_is_leader:
                    # One is leader = Enemy
                    rel_type = ["ENEMY"]
                    strength = random.randint(7, 9)
                    desc = "적대적 관계"
                    should_create = True
                else:
                    # Member vs Member = Rival/Enemy
                    if random.random() < 0.3:
                        rel_type = ["RIVAL"]
                        strength = random.randint(4, 7)
                        desc = "대립 관계"
                        should_create = True

            elif stance == "FRIENDLY":
                if source_is_leader and target_is_leader:
                     rel_type = ["ALLY"]
                     strength = 8
                     desc = "동맹 수장"
                     should_create = True
                elif random.random() < 0.3:
                    rel_type = ["FRIEND"]
                    strength = 6
                    desc = "우호적 관계"
                    should_create = True

            # NEUTRAL and others: Very low chance (Unknown)
            elif random.random() < 0.05: # Decreased from 0.1
                rel_type = ["BUSINESS_PARTNER"]
                strength = 3
                desc = "비즈니스"
                should_create = True

            if should_create:
                add_rel(source, target, rel_type, strength, desc)
                created += 1

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
    print(f"Generated {NUM_CHARACTERS} characters with faction logic for project {PROJECT_ID}")

if __name__ == "__main__":
    generate_data()
