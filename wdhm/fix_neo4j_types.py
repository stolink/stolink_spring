import json
import urllib.request

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
            else:
                print("Update successful")
                print(json.dumps(result, indent=2))
    except Exception as e:
        print(f"HTTP Error: {e}")

def fix_types():
    print("Fixing MASTER -> MENTOR...")
    # Update types list where it contains MASTER
    cypher = """
    MATCH ()-[r:RELATED_TO]->()
    WHERE 'MASTER' IN r.types
    SET r.types = [x IN r.types | CASE WHEN x='MASTER' THEN 'MENTOR' ELSE x END]
    RETURN count(r)
    """
    run_cypher(cypher)

    print("Fixing SERVANT -> APPRENTICE...")
    cypher = """
    MATCH ()-[r:RELATED_TO]->()
    WHERE 'SERVANT' IN r.types
    SET r.types = [x IN r.types | CASE WHEN x='SERVANT' THEN 'APPRENTICE' ELSE x END]
    RETURN count(r)
    """
    run_cypher(cypher)

if __name__ == "__main__":
    fix_types()
