
import json
import urllib.request

# Configuration
NEO4J_URL = "http://localhost:7474/db/neo4j/tx/commit"
NEO4J_AUTH = ("neo4j", "stolink123")

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

def check_relationship():
    cypher = """
    MATCH (a:Character)-[r:RELATED_TO]-(b:Character)
    WHERE a.name = '유비' AND b.name = '제갈량'
    RETURN r.types AS types, r.description AS description, r.strength AS strength
    """

    result = run_cypher(cypher)

    if result and result.get("results"):
        data = result["results"][0].get("data")
        if data:
            print(f"Relationship Found: {len(data)} path(s)")
            for row in data:
                print(f"Row: {row['row']}")
        else:
            print("No relationship found between 유비 and 제갈량")
    else:
        print("Query returned no results")

if __name__ == "__main__":
    check_relationship()
