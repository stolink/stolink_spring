import json
import urllib.request
import uuid

# Configuration
NEO4J_URL = "http://localhost:7474/db/neo4j/tx/commit"
NEO4J_AUTH = ("neo4j", "stolink123")
PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7" # Match generate_dummy_json.py

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

def load_data():
    with open("dummy_data.json", "r") as f:
        return json.load(f)

def clean_project():
    print(f"Clearing existing data for project {PROJECT_ID}...")
    run_cypher("MATCH (n) WHERE n.projectId = $projectId DETACH DELETE n", {"projectId": PROJECT_ID})

def seed():
    data = load_data()
    # Handle both nested 'result' (old format?) and flat structure
    if "characters" in data:
        characters = data["characters"]
        relationships = data.get("relationships", [])
    else:
        result = data.get("result", {})
        characters = result.get("characters", [])
        relationships = result.get("relationships", [])

    clean_project()

    name_to_node_id = {} # Name -> UUID of created node

    print(f"Creating {len(characters)} characters...")
    for char in characters:
        u_id = str(uuid.uuid4())
        name = char["profile"]["name"]

        # Prepare properties matching API Entity
        props = {
            "id": u_id,
            "projectId": PROJECT_ID,
            "characterId": char["_id"],
            "name": name,
            "role": char["role"],
            "status": char["status"],
            "age": char["profile"].get("age"),
            "gender": char["profile"].get("gender"),
            "race": char["profile"].get("race"),
            "mbti": char["profile"].get("mbti"),
            "backstory": char["profile"].get("backstory"),

            # JSON fields stored as strings
            "profileJson": json.dumps(char["profile"], ensure_ascii=False),
            "aliasesJson": json.dumps(char.get("aliases", []), ensure_ascii=False),
            "appearanceJson": json.dumps(char.get("appearance", {}), ensure_ascii=False),
            "relationsJson": json.dumps({}, ensure_ascii=False), # Placeholder
            "imageUrl": char.get("imageUrl")
        }

        # Determine faction property if needed?
        # API "create_character" uses: "faction": profile["faction"].get("name")
        if "faction" in char["profile"]:
            props["faction"] = char["profile"]["faction"]["name"]

        # Add requested attributes for Three Kingdoms viz
        if "group" in char["profile"]:
            props["group"] = char["profile"]["group"]
        if "importance" in char["profile"]:
            props["importance"] = char["profile"]["importance"]
        if "english_name" in char["profile"]:
             props["englishId"] = char["profile"]["english_name"]
             # If user strictly wants 'id' to be English name for visualization logic?
             # But DB needs UUID for @Id. Let's keep 'id' as UUID and add 'nodeId' or similar?
             # User said: "id: 고유 식별자 (영문 표기 권장)"
             # I'll stick to 'englishId' property to be safe.

        # Ensure label matches name (Korean)
        props["label"] = name

        cypher = """
        CREATE (n:Character $props)
        RETURN n.id
        """
        res = run_cypher(cypher, {"props": props})
        if res:
            name_to_node_id[name] = u_id

    print(f"Creating {len(relationships)} relationships...")
    for i, rel in enumerate(relationships):
        if i < 5:
            print(f"DEBUG REL {i}: {rel['source']} -> {rel['target']} Types: {rel['relation_types']}")

        source_name = rel["source"]
        target_name = rel["target"]

        if source_name not in name_to_node_id or target_name not in name_to_node_id:
            print(f"Skipping {source_name}->{target_name}: Node not found")
            continue

        rel_id = str(uuid.uuid4())

        # Relation Types to uppercase for Neo4j Type?
        # API stores types as property 'types': ["FRIEND", "RIVAL"]
        # And usually uses a generic label :RELATED_TO
        # The frontend graph visualization queries :RELATED_TO using 'types' property?
        # Checked seed_neo4j.py: "types": rel_data.get("relation_types")
        # Checked populate_les_mis.py: MERGE (a)-[r:RELATED_TO ...]->(b) SET r.type = ...

        # We will use :RELATED_TO and set 'types' property (list of strings)

        props = {
            "id": rel_id,
            "projectId": PROJECT_ID,
            "sourceId": name_to_node_id[source_name], # Use node UUIDs?
            # Wait, relationships link nodes.
            # API stores sourceId/targetId as properties on the Relationship entity?
            # Neo4j stores physical links.
            # We must CREATE physical links.
            "types": rel["relation_types"],
            "strength": rel["strength"],
            "description": rel["description"],
            "bidirectional": rel["bidirectional"]
        }

        cypher = """
        MATCH (a:Character {id: $sourceId})
        MATCH (b:Character {id: $targetId})
        MERGE (a)-[r:RELATED_TO {projectId: $projectId}]->(b)
        SET r += $props
        """
        # Using MERGE on :RELATED_TO might merge all relationships if not specific.
        # But we want multiple potential edges?
        # 'MERGE' with just label might collapse.
        # Better CREATE if we want unique. But let's use MERGE with ID if we had one.
        # Since we just created nodes, we can just CREATE.

        run_cypher(cypher, {
            "sourceId": name_to_node_id[source_name],
            "targetId": name_to_node_id[target_name],
            "projectId": PROJECT_ID,
            "props": props
        })

    print("Seeding complete.")

if __name__ == "__main__":
    seed()
