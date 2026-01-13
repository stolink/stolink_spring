import json
import urllib.request
import uuid
import random

# Configuration
NEO4J_URL = "http://localhost:7474/db/neo4j/tx/commit"
NEO4J_AUTH = ("neo4j", "stolink123")
PROJECT_ID = "6e686700-34cf-4b23-ae2d-f673743dc4ce"

def run_cypher(statement, params={}):
    payload = {
        "statements": [{
            "statement": statement,
            "parameters": params
        }]
    }
    data = json.dumps(payload).encode("utf-8")

    password_mgr = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    password_mgr.add_password(None, NEO4J_URL, NEO4J_AUTH[0], NEO4J_AUTH[1])
    handler = urllib.request.HTTPBasicAuthHandler(password_mgr)
    opener = urllib.request.build_opener(handler)

    req = urllib.request.Request(NEO4J_URL, data=data, headers={"Content-Type": "application/json"})

    try:
        with opener.open(req) as response:
            result = json.loads(response.read().decode("utf-8"))
            if result.get("errors"):
                print(f"Error executing Cypher: {result['errors']}")
                return None
            return result
    except Exception as e:
        print(f"HTTP Error: {e}")
        return None

def populate():
    print(f"Clearing existing data for project {PROJECT_ID}...")
    run_cypher("MATCH (n) WHERE n.projectId = $projectId DETACH DELETE n", {"projectId": PROJECT_ID})

    # 1. Define Characters (Latest request: 20 characters total)
    characters = [
        {"name": "장 발장", "role": "protagonist", "charId": "char-valjean-001"},
        {"name": "팡틴", "role": "supporting", "charId": "char-fantine-001"},
        {"name": "자베르", "role": "antagonist", "charId": "char-javert-001"},
        {"name": "코제트", "role": "supporting", "charId": "char-cosette-001"},
        {"name": "미리엘 주교", "role": "mentor", "charId": "char-myriel-001"},
        {"name": "테나르디에", "role": "antagonist", "charId": "char-thenardier-001"},
        {"name": "테나르디에 부인", "role": "antagonist", "charId": "char-mme-thenardier-001"},
        {"name": "에포닌", "role": "supporting", "charId": "char-eponine-001"},
        {"name": "아젤마", "role": "extra", "charId": "char-azelma-001"},
        {"name": "포슐르방", "role": "ally", "charId": "char-fauchelevent-001"},
        {"name": "바티스틴", "role": "extra", "charId": "char-baptistine-001"},
        {"name": "마글루아르 부인", "role": "extra", "charId": "char-mme-magloire-001"},
        {"name": "샴마티외", "role": "extra", "charId": "char-champmathieu-001"},
        {"name": "브레베", "role": "extra", "charId": "char-brevet-001"},
        {"name": "슈니디외", "role": "extra", "charId": "char-chenildieu-001"},
        {"name": "코슈파유", "role": "extra", "charId": "char-cochepaille-001"},
        {"name": "앙졸라스", "role": "supporting", "charId": "char-enjolras-001"},
        {"name": "마리우스", "role": "supporting", "charId": "char-marius-001"},
        {"name": "가브로슈", "role": "supporting", "charId": "char-gavroche-001"},
        {"name": "그랑테르", "role": "supporting", "charId": "char-grantaire-001"}
    ]

    node_ids = {} # name -> uuid

    print("Creating characters...")
    for char in characters:
        u_id = str(uuid.uuid4())
        node_ids[char["name"]] = u_id
        props = {
            "id": u_id,
            "projectId": PROJECT_ID,
            "characterId": char["charId"],
            "name": char["name"],
            "role": char["role"],
            "status": "alive"
        }
        run_cypher("CREATE (n:Character $props)", {"props": props})

    # 2. Define Events for Jean Valjean (6 events as per previous logic for "Finale" theme)
    events = [
        {"eventId": "E1", "name": "빵을 훔치다", "type": "BACKSTORY", "imp": 80, "desc": "조카들을 위해 빵을 훔쳐 수감됨"},
        {"eventId": "E2", "name": "미리엘 주교의 용서", "type": "TURNING_POINT", "imp": 100, "desc": "주교의 은촛대 선물로 개과천선함"},
        {"eventId": "E3", "name": "마들렌 시장이 되다", "type": "PROGRESSION", "imp": 70, "desc": "신분을 숨기고 성공하여 시장이 됨"},
        {"eventId": "E4", "name": "포슐르방을 구하다", "type": "ACTION", "imp": 85, "desc": "마차 아래 깔린 포슐르방을 괴력으로 구함"},
        {"eventId": "E5", "name": "법정에서 정체를 밝히다", "type": "CLIMAX", "imp": 100, "desc": "무고한 샴마티외를 위해 자신의 정체를 폭로함"},
        {"eventId": "E6", "name": "팡틴에게 약속하다", "type": "DECISION", "imp": 95, "desc": "임종 전의 팡틴에게 코제트를 지킬 것을 맹세함"}
    ]

    print("Creating events...")
    for evt in events:
        e_id = str(uuid.uuid4())
        props = {
            "id": e_id,
            "projectId": PROJECT_ID,
            "eventId": evt["eventId"],
            "eventType": evt["type"],
            "narrativeSummary": evt["name"],
            "description": evt["desc"],
            "importance": evt["imp"]
        }
        run_cypher("CREATE (e:Event $props)", {"props": props})
        run_cypher("""
            MATCH (c:Character {id: $cId})
            MATCH (e:Event {id: $eId})
            MERGE (c)-[:PARTICIPATED_IN]->(e)
        """, {"cId": node_ids["장 발장"], "eId": e_id})

    # 3. Random Relationships for everyone (0-2 as requested)
    print("Creating random relationships (0-2 per character)...")
    char_names = list(node_ids.keys())
    for char_name in char_names:
        count = random.randint(0, 2)
        # Avoid self-relationship
        potential_targets = [n for n in char_names if n != char_name]
        targets = random.sample(potential_targets, min(count, len(potential_targets)))

        for target in targets:
            # Determine relationship type
            r_type = "NEUTRAL"
            if char_name == "장 발장" and target == "자베르": r_type = "ENEMY"
            elif char_name == "장 발장" and target == "미리엘 주교": r_type = "ALLY"
            elif char_name == "장 발장" and target == "팡틴": r_type = "ALLY"

            # Create as RELATED_TO label which the Repository expects
            # Set properties 'id', 'type', 'strength', 'description', 'bidirectional'
            run_cypher("""
                MATCH (a:Character {id: $aId})
                MATCH (b:Character {id: $bId})
                MERGE (a)-[r:RELATED_TO {projectId: $projectId}]->(b)
                ON CREATE SET
                    r.id = $relId,
                    r.type = $type,
                    r.description = $desc,
                    r.strength = 10,
                    r.bidirectional = true
            """, {
                "aId": node_ids[char_name],
                "bId": node_ids[target],
                "type": r_type,
                "desc": f"{char_name} -> {target} 관계",
                "projectId": PROJECT_ID,
                "relId": str(uuid.uuid4())
            })

    print("Done!")

    print("Done!")

if __name__ == "__main__":
    populate()
