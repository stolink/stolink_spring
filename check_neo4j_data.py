import json
import urllib.request

NEO4J_URL = "http://localhost:7474/db/neo4j/tx/commit"
NEO4J_AUTH = ("neo4j", "stolink123")
PROJECT_ID = "cd250a32-e05d-4752-9795-8364674e7859"

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

def check_data():
    print(f"Checking data for project {PROJECT_ID}...")

    res = run_cypher("MATCH (n:Character) RETURN n.id, n.name, n.projectId")
    print(f"All Characters: {res['results'][0]['data']}")

    # Inspect Character properties
    res = run_cypher("MATCH (n:Character) UNWIND keys(n) as k RETURN distinct k")
    print(f"Distinct Keys: {res['results'][0]['data']}")

    # Check link between Javert and Yoon Seo-jun
    res = run_cypher("MATCH (a:Character {name: '자베르'})-[r]-(b:Character {name: '윤서준'}) RETURN r")
    print("Link Javert-Yoon:", res)

if __name__ == "__main__":
    check_data()
