import json
import random
import uuid

PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7" # User's current project

# Data Source
THREE_KINGDOMS = {
    "Wei": [
        ("Cao_Cao", "조조", 10), ("Sima_Yi", "사마의", 9), ("Xiahou_Dun", "하후돈", 8), ("Xiahou_Yuan", "하후연", 8),
        ("Zhang_Liao", "장료", 9), ("Xu_Huang", "서황", 8), ("Zhang_He", "장합", 8), ("Yue_Jin", "악진", 7),
        ("Yu_Jin", "우금", 7), ("Guo_Jia", "곽가", 9), ("Xun_Yu", "순욱", 9), ("Xun_You", "순유", 8),
        ("Cheng_Yu", "정욱", 8), ("Jia_Xu", "가후", 9), ("Cao_Pi", "조비", 8), ("Cao_Ren", "조인", 8),
        ("Cao_Hong", "조홍", 7), ("Dian_Wei", "전위", 8), ("Xu_Chu", "허저", 8), ("Yang_Xiu", "양수", 6)
    ],
    "Shu": [
        ("Liu_Bei", "유비", 10), ("Guan_Yu", "관우", 9), ("Zhang_Fei", "장비", 9), ("Zhuge_Liang", "제갈량", 10),
        ("Zhao_Yun", "조운", 9), ("Ma_Chao", "마초", 9), ("Huang_Zhong", "황충", 8), ("Wei_Yan", "위연", 8),
        ("Pang_Tong", "방통", 9), ("Fa_Zheng", "법정", 8), ("Jiang_Wei", "강유", 9), ("Ma_Su", "마다", 6),
        ("Mi_Zhu", "미축", 6), ("Sun_Qian", "손건", 6), ("Liu_Shan", "유선", 4)
    ],
    "Wu": [
        ("Sun_Quan", "손권", 10), ("Zhou_Yu", "주유", 9), ("Lu_Su", "노숙", 9), ("Lu_Meng", "여몽", 9),
        ("Lu_Xun", "육손", 9), ("Sun_Ce", "손책", 9), ("Sun_Jian", "손견", 9), ("Gan_Ning", "감녕", 8),
        ("Tai_Shi_Ci", "태사자", 9), ("Huang_Gai", "황개", 8), ("Cheng_Pu", "정보", 7), ("Zhou_Tai", "주태", 8),
        ("Ling_Tong", "능통", 7), ("Zhuge_Jin", "제갈근", 8), ("Zhang_Zhao", "장소", 8)
    ]
}

def generate_three_kingdoms_data():
    characters = []
    relationships = []

    char_map = {} # To store generated character dicts for reference

    all_chars_flat = []

    # 1. Generate Characters
    for faction, char_list in THREE_KINGDOMS.items():
        for char_data in char_list:
            eng_id, kor_name, importance = char_data

            # Map importance to role
            role = "supporting"
            if importance == 10: role = "protagonist" # Monarch
            elif importance >= 8: role = "antagonist" # General/Strategist
            elif importance <= 5: role = "other"

            char_id = str(uuid.uuid4()) # Use UUID for DB compatibility

            # Additional visual variation
            seed = eng_id

            char_obj = {
                "_id": char_id,
                "role": role,
                "status": "active",
                "aliases": [kor_name, eng_id],
                "projectId": PROJECT_ID,
                "profile": {
                    "character_id": char_id,
                    "name": kor_name,
                    "english_name": eng_id, # Custom field
                    "age": random.randint(30, 60),
                    "gender": "남성", # Mostly male in this dataset
                    "race": "인간",
                    "faction": {"name": faction},
                    "importance": importance, # For visualization size
                    "group": faction, # For visualization color
                },
                "appearance": {
                   "physique": "장군" if importance >= 8 else "문관"
                },
                 "imageUrl": f"https://api.dicebear.com/7.x/notionists/svg?seed={seed}"
            }
            characters.append(char_obj)
            char_map[eng_id] = char_obj
            all_chars_flat.append((eng_id, faction, importance))

    # 2. Generate Relationships

    # Helper
    def add_rel(src_id, tgt_id, rel_type, strength, desc):
        src_char = char_map[src_id]
        tgt_char = char_map[tgt_id]

        # Avoid dupes
        for r in relationships:
            if (r["source"] == src_char["profile"]["name"] and r["target"] == tgt_char["profile"]["name"]) or \
               (r["source"] == tgt_char["profile"]["name"] and r["target"] == src_char["profile"]["name"]):
                return

        relationships.append({
            "source": src_char["profile"]["name"], # Seeder uses name to lookup
            "target": tgt_char["profile"]["name"],
            "relation_types": [rel_type],
            "strength": strength, # 1-10
            "description": desc,
            "bidirectional": True
        })

    # Intra-Faction (Reduced Density, Mixed Types)
    for faction, char_list in THREE_KINGDOMS.items():
        leader_id = char_list[0][0] # First one is leader

        for i, char_data in enumerate(char_list):
            my_id = char_data[0]
            my_imp = char_data[2]

            # 1. Leader Centrality (One-way or Mutual Strong)
            if my_id != leader_id:
                add_rel(leader_id, my_id, "Subordinate", 10, "군신 관계")

            # 2. Member-Member (Sparse & Mixed)
            # Iterate forward to avoid duplicates
            for other_data in char_list[i+1:]:
                other_id = other_data[0]
                other_imp = other_data[2]

                # Only significant characters tend to have specific relations
                if my_imp >= 6 and other_imp >= 6:
                    # Low probability (Sparse)
                    if random.random() < 0.3:
                        dice = random.random()
                        if dice < 0.2: # 20% Internal Rivalry/Tension
                            add_rel(my_id, other_id, "Rival", random.randint(3, 6), "정치적 견제")
                        elif dice < 0.9: # 70% Professional/Friendly
                            add_rel(my_id, other_id, "Trust", random.randint(5, 8), "동료")
                        # Remaining 10% no relation (gap)

    # Inter-Faction (Complex Web: Hostile, Rival, Friend)

    # 1. Key Historical Rivalries (Hardcoded)
    rivals = [
        ("Cao_Cao", "Liu_Bei", "Hostile", 10, "숙적"),
        ("Cao_Cao", "Sun_Quan", "Hostile", 8, "적대"),
        ("Liu_Bei", "Sun_Quan", "Rival", 5, "동맹이자 경쟁"),
        ("Zhuge_Liang", "Sima_Yi", "Rival", 9, "최대의 라이벌"),
        ("Zhuge_Liang", "Zhou_Yu", "Rival", 8, "지략 대결"),
        ("Guan_Yu", "Lu_Meng", "Hostile", 9, "복수"),
    ]

    for r in rivals:
        if r[0] in char_map and r[1] in char_map:
            add_rel(r[0], r[1], r[2], r[3], r[4])

    # 2. Random Inter-faction Connections (The "Mix")
    for _ in range(40): # Try 40 attempts for diversity
        c1 = random.choice(all_chars_flat)
        c2 = random.choice(all_chars_flat)

        # Different faction only
        if c1[1] != c2[1]:
             dice = random.random()
             rel_type = "Hostile"
             strength = random.randint(1, 4) # Weak by default
             desc = "적대"

             if dice < 0.1: # 10% Chance of cross-faction Friendship/Respect
                 rel_type = "Friend"
                 strength = random.randint(4, 7)
                 desc = "존경하는 사이"
             elif dice < 0.3: # 20% Weak Rivalry
                 rel_type = "Rival"
                 strength = random.randint(2, 5)
                 desc = "경쟁"
             else: # 70% Hostile
                 strength = random.randint(1, 3) # Weak force for graph spacing
                 desc = "전쟁"

             add_rel(c1[0], c2[0], rel_type, strength, desc)

    return {
        "project_id": PROJECT_ID,
        "characters": characters,
        "relationships": relationships
    }

if __name__ == "__main__":
    data = generate_three_kingdoms_data()
    with open("dummy_data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Generated {len(data['characters'])} characters and {len(data['relationships'])} relationships.")
