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

        # Avoid dupes, but allow multi-type if it's a new type for existing pair
        for r in relationships:
            if (r["source"] == src_char["profile"]["name"] and r["target"] == tgt_char["profile"]["name"]) or \
               (r["source"] == tgt_char["profile"]["name"] and r["target"] == src_char["profile"]["name"]):
                if rel_type not in r["relation_types"]:
                    r["relation_types"].append(rel_type)
                    # Update description to reflect complexity
                    types_kor = {
                        "ALLY": "동매/아군",
                        "FRIEND": "우정",
                        "RIVAL": "경쟁",
                        "ENEMY": "적대",
                        "MENTOR": "스승과 제자",
                        "FAMILY": "가족",
                        "ROMANTIC": "애정"
                    }
                    kor_labels = [types_kor.get(t, t) for t in r["relation_types"]]
                    r["description"] = "복합적 관계 (" + ", ".join(kor_labels) + ")"
                    # Increase strength if multiple types exist
                    r["strength"] = min(10, r["strength"] + 1)
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
                add_rel(leader_id, my_id, "ALLY", 10, "군신 관계")

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

    # 1.1 Specific Complex Relationship for Test: Liu Bei & Zhuge Liang
    # (ALLY/Subordinate is already added in Intra-Faction loop)
    # Adding 4 more from predefined set to make it 5 total.
    add_rel("Liu_Bei", "Zhuge_Liang", "ROMANTIC", 10, "각별한 애착")
    add_rel("Liu_Bei", "Zhuge_Liang", "MENTOR", 10, "스승과 제자")
    add_rel("Liu_Bei", "Zhuge_Liang", "FAMILY", 9, "친밀함")
    add_rel("Liu_Bei", "Zhuge_Liang", "RIVAL", 6, "지적 경쟁")

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

    # 3. Generate Events (Narrative Sections) with Dummy Embeddings
    def generate_events(joined_chars):
        events = []

        # Templates for dynamic event generation
        templates = [
            {
                "title": "도원결의",
                "type": "decision",
                "summary": "유비, 관우, 장비가 의형제를 맺고 난세 평정을 다짐하다.",
                "content": "\"{char1}, {char2}, {char3}! 우리 셋은 성은 다르지만 형제가 되기로 맹세했소.\"\n\n복숭아 꽃이 만발한 정원에서 {char1}이(가) 술잔을 높이 들었다. {char2}와(과) {char3}도 결연한 표정으로 잔을 채웠다.\n\n\"우린 한날한시에 태어나진 못했지만, 한날한시에 죽기를 원하오!\"\n\n세 사람의 목소리가 복숭아 밭에 울려 퍼졌다. 난세의 시작을 알리는 영웅들의 다짐이었다."
            },
            {
                "title": "삼고초려",
                "type": "encounter",
                "summary": "{char1}이(가) {char3}을(를) 얻기 위해 세 번이나 찾아가다.",
                "content": "\"{char1} 님, 벌써 세 번째 방문입니다. 이번에도 안 계시면 어쩌시렵니까?\"\n\n{char2}이(가) 불만 섞인 목소리로 물었다. 눈보라가 치는 와중에도 {char1}은(는) 묵묵히 초가집 앞을 지켰다.\n\n\"현인을 얻으려면 이 정도 정성은 보여야지.\"\n\n그때 닫혀 있던 문이 열리고, 부채를 든 {char3}이(가) 모습을 드러냈다. 천하를 논할 거대한 만남의 시작이었다."
            },
            {
                "title": "적벽대전 - 동남풍",
                "type": "action",
                "summary": "동남풍을 이용한 화공으로 {char3}의 대군을 격파하다.",
                "content": "강 위로 거센 바람이 불기 시작했다. {char1}은(는) 제단을 쌓고 하늘을 우러러보았다. 깃발이 동남쪽으로 펄럭이기 시작했다.\n\n\"{char2} 장군! 지금이오! 화공을 시작하시오!\"\n\n{char3}의 대군을 향해 불화살이 비처럼 쏟아졌다. 수천 척의 배가 화염에 휩싸였고, 장강은 붉게 물들었다."
            },
            {
                "title": "장판파의 호통",
                "type": "conflict",
                "summary": "{char1}이(가) 단기필마로 다리 위에서 대군을 막아서다.",
                "content": "\"{char1}이(가) 여기 있다! 죽고 싶은 자는 덤벼라!\"\n\n다리 위에 홀로 선 {char1}의 고함 소리는 천둥과 같았다. {char2}의 백만 대군은 그 기세에 눌려 감히 다가오지 못했다.\n\n말 한 필과 장창 하나로 대군을 막아선 그의 모습은 전설이 되어 후세에 전해졌다."
            },
            {
                "title": "미인계",
                "type": "dialogue",
                "summary": "연회장에서 {char2}이(가) {char3}을(를) 유혹하며 계략을 꾸미다.",
                "content": "\"{char1} 님, 이 술 한 잔 받으시지요.\"\n\n화려한 연회장에서 무희들의 춤사위가 이어지는 가운데, 은밀한 계략이 진행되고 있었다. {char2}은(는) {char3}의 눈치를 살피며 의미심장한 미소를 지었다.\n\n천하의 영웅들도 아름다움 앞에서는 흔들리는 법. 칼보다 무서운 것이 때로는 부드러운 속삭임이다."
            },
            {
                "title": "칠종칠금",
                "type": "resolution",
                "summary": "{char2}이(가) {char3}을(를) 일곱 번 잡고 일곱 번 놓아주어 마음을 얻다.",
                "content": "\"{char1}, 또 잡혔구나. 이번엔 항복하겠느냐?\"\n\n{char2}은(는) 일곱 번째로 잡힌 {char3}의 포승줄을 풀어주었다. {char3}은(는) 땅에 머리를 조아리며 눈물을 흘렸다.\n\n\"공의 덕망에 감복했습니다. 이제 다시는 반역하지 않겠습니다.\"\n\n마음으로 사람을 얻는 것이 진정한 승리임을 보여주는 순간이었다."
            },
             {
                "title": "관도대전",
                "type": "discovery",
                "summary": "오소의 군량미 창고를 습격하여 {char3}의 군대를 무너뜨리다.",
                "content": "\"{char1}의 군량미가 오소에 쌓여있다는 정보가 들어왔습니다.\"\n\n{char2}의 조언에 {char3}은(는) 눈을 번뜩였다. 소수의 기병을 이끌고 밤길을 달린 기습 작전.\n\n불타오르는 군량미와 함께 전세는 역전되었고, 중원의 주인은 바뀌게 되었다."
            },
            {
                "title": "출사표",
                "type": "confession",
                "summary": "{char2}이(가) 북벌을 앞두고 충정을 담은 글을 올리다.",
                "content": "\"{char1}, 신 {char2}이(가) 아뢰옵니다. 선제께서 창업을 반도 못 이루시고 붕어하시니...\"\n\n눈물로 써 내려간 이 글은 읽는 이의 심금을 울렸다. 북벌을 향한 굳은 의지와 충정이 글자마다 서려 있었다. {char3}은(는) 말없이 고개를 끄덕였다."
            },
             {
                "title": "백제성 탁고",
                "type": "departure",
                "summary": "{char2}이(가) 죽음을 앞두고 {char1}에게 후사를 부탁하다.",
                "content": "\"{char1}, 내 아들이 재목이 아니라면 자네가 황제의 자리에 오르게.\"\n\n죽음을 앞둔 {char2}의 파격적인 유언에 {char1}은(는) 바닥에 엎드려 통곡했다.\n\n\"신은 죽을 때까지 충성을 다할 뿐입니다!\"\n\n군신의 신뢰가 이토록 깊을 수 있음을 보여주는 마지막 당부였다."
            },
            {
                "title": "오장원의 별",
                "type": "death",
                "summary": "오장원에서 큰 별이 지고 {char2}의 탄식이 울려 퍼지다.",
                "content": "가을바람이 차갑게 부는 오장원. {char1}의 병세는 깊어만 갔다. {char2}은(는) 밤하늘의 떨어지는 별을 보며 탄식했다.\n\n\"하늘이시여, 어찌하여 저분을 데려가시나이까!\"\n\n지략의 화신이라 불리던 그도 천명은 거스를 수 없었다."
            }
        ]

        generated_sections = []

        # Target Pair: Liu_Bei (유비) & Zhuge_Liang (제갈량) - existing 'Subordinate' relationship
        c1, c1_kor = "Liu_Bei", "유비"
        c2, c2_kor = "Zhuge_Liang", "제갈량"

        pai_events = [
            {
                "title": "삼고초려: 첫 만남",
                "type": "encounter",
                "importance": 10.0,
                "summary": "유비가 제갈량을 얻기 위해 세 번 찾아가 마음을 전하다.",
                "content": "눈보라가 치는 겨울, 유비는 제갈량의 초려를 찾았다. \"선생을 뵙기 위해 세 번이나 왔습니다.\" 제갈량은 유비의 정성에 감복하여 문을 열었다. 천하를 논하는 두 사람의 첫 만남이었다."
            },
            {
                "title": "융중대: 천하삼분지계",
                "type": "dialogue",
                "importance": 9.5,
                "summary": "제갈량이 유비에게 천하를 셋으로 나누는 거대한 전략을 제시하다.",
                "content": "제갈량은 지도를 펼치며 말했다. \"조조는 북을, 손권은 남을 차지하고 있으니, 주공께서는 형주와 익주를 취하여 솥발처럼 세력의 균형을 이루십시오.\" 유비는 무릎을 치며 감탄했다."
            },
            {
                "title": "박망파 전투: 첫 승리",
                "type": "action",
                "importance": 8.5,
                "summary": "제갈량의 화공 계략으로 유비군이 조조군을 격파하다.",
                "content": "제갈량의 지시대로 유비군은 거짓 패한 척 후퇴했다. 숲 속 깊이 적을 유인하자, 사방에서 불길이 치솟았다. \"공명의 계략이 적중했다!\" 유비군은 첫 승리의 기쁨을 만끽했다."
            },
             {
                "title": "적벽대전 연합 논의",
                "type": "decision",
                "importance": 10.0,
                "summary": "조조에 맞서기 위해 유비와 제갈량이 손권과의 동맹을 결단하다.",
                "content": "조조의 백만 대군이 남하하자 유비는 근심했다. 제갈량이 나섰다. \"제가 동오로 가서 손권과 손을 잡겠습니다.\" 유비는 제갈량의 손을 잡고 깊은 신뢰를 보냈다."
            },
            {
                "title": "형주 입성",
                "type": "arrival",
                "importance": 8.0,
                "summary": "적벽 대전 이후 유비와 제갈량이 형주에 입성하여 기반을 다지다.",
                "content": "연합군의 승리 후, 유비와 제갈량은 형주에 깃발을 꽂았다. \"이제야 우리만의 터전이 생겼습니다.\" 제갈량의 말에 유비는 감격스러운 표정으로 성을 바라보았다."
            },
            {
                "title": "익주 평정",
                "type": "conflict",
                "importance": 8.5,
                "summary": "유비가 제갈량의 지원을 받아 익주를 평정하고 입촉하다.",
                "content": "험준한 촉의 산세를 넘어 제갈량의 지원군이 도착했다. 유비와 제갈량은 성도에서 합류했다. \"주공, 이제 패업의 기반이 완성되었습니다.\" 두 사람은 서로를 마주보며 웃었다."
            },
            {
                "title": "한중 공방전 승리",
                "type": "victory",
                "importance": 9.0,
                "summary": "조조와의 치열한 공방 끝에 한중을 점령하고 승기를 잡다.",
                "content": "법정과 제갈량의 조언 아래, 유비는 황충을 보내 하후연을 베었다. 조조가 물러가자 유비는 한중왕에 올랐다. 제갈량은 백성들과 함께 만세를 불렀다."
            },
            {
                "title": "황제 등극",
                "type": "coronation",
                "importance": 10.0,
                "summary": "유비가 황제에 오르고 제갈량이 승상이 되어 보좌를 맹세하다.",
                "content": "조비가 헌제를 폐위하자, 제갈량은 유비에게 황제에 오를 것을 간청했다. \"한실의 명맥을 이으셔야 합니다.\" 유비는 제단에 올라 촉한의 황제가 되었고, 제갈량을 승상으로 임명했다."
            },
            {
                "title": "이릉 대전의 반대",
                "type": "conflict",
                "importance": 9.5,
                "summary": "관우의 복수를 위한 동오 정벌을 두고 유비와 제갈량이 대립하다.",
                "content": "\"폐하, 지금은 위를 칠 때이지 오를 칠 때가 아닙니다.\" 제갈량의 간곡한 만류에도 유비는 분노를 거두지 않았다. \"아우의 원수를 갚지 않고 어찌 황제라 하겠느냐!\" 제갈량은 탄식하며 물러났다."
            },
            {
                "title": "백제성 탁고: 마지막 유언",
                "type": "death",
                "importance": 10.0,
                "summary": "병든 유비가 백제성에서 제갈량에게 후사를 맡기고 눈을 감다.",
                "content": "이릉 패배 후 병든 유비가 제갈량의 손을 잡았다. \"내 아들이 재목이 아니면 그대가 황제가 되시오.\" 제갈량은 바닥에 머리를 찧으며 통곡했다. \"신은 죽을 때까지 충성을 다할 뿐입니다.\" 유비는 편안히 눈을 감았다."
            }
        ]

        for i, ev_data in enumerate(pai_events):
            # Generate dummy embedding
            embedding = [random.uniform(-0.1, 0.1) for _ in range(3072)]

            generated_sections.append({
                "sequence_order": i + 1,
                "nav_title": ev_data["title"],
                "content": ev_data["content"],
                "summary": ev_data["summary"],
                "event_type": ev_data["type"],
                "importance": ev_data["importance"],
                "participants": [c1_kor, c2_kor], # Fixed Pair (using Korean names)
                "embedding": embedding
            })

        return generated_sections

    sections = generate_events(characters)

    return {
        "project_id": PROJECT_ID,
        "characters": characters,
        "relationships": relationships,
        "sections": sections
    }

if __name__ == "__main__":
    data = generate_three_kingdoms_data()
    with open("dummy_data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Generated {len(data['characters'])} characters and {len(data['relationships'])} relationships.")
