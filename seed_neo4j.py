import json
import urllib.request
import urllib.error

# Configuration
API_BASE = "http://localhost:8080/api"
PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7"
TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiIzNjE1ZjcxYi0zZTMwLTQxYTktOGY1Mi03MzkzNDFiY2NlY2QiLCJzdWIiOiI0NTcwNjA5OC1kZTY0LTQ1NzgtODZiZS0zOTI2ZDE4NTM2MmYiLCJpYXQiOjE3Njc4ODY0NzAsImV4cCI6MTc2Nzg4ODI3MCwidHlwZSI6ImFjY2VzcyJ9.yQOBwl0JyAAYjjqANURxNsJ1iHYV7HEuUi2Eb7xsIBEas73_TcTVpMr2QPsl70iY-6ZiSeDKmgigJmHIWjY2eA"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json",
    "Origin": "http://localhost:3000"
}

def load_data():
    with open("dummy_data.json", "r") as f:
        return json.load(f)

def post_json(url, data):
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=HEADERS)
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        print(f"HTTP Error: {e.code} - {e.read().decode('utf-8')}")
        return e.code, None
    except Exception as e:
        print(f"Error: {e}")
        return 0, None

def create_character(char_data):
    # Map DTO to Entity fields
    profile = char_data.get("profile", {})

    payload = {
        "projectId": PROJECT_ID,
        "characterId": char_data.get("id"), # Generic ID
        "name": profile.get("name"),
        "role": char_data.get("role"),
        "status": char_data.get("status"),
        "age": profile.get("age"),
        "gender": profile.get("gender"),
        "race": profile.get("race"),
        "mbti": profile.get("mbti"),
        "backstory": profile.get("backstory"),
        # JSON fields
        "profileJson": json.dumps(profile),
        "aliasesJson": json.dumps(char_data.get("aliases", [])),
        "appearanceJson": json.dumps(char_data.get("appearance", {})),
        "relationsJson": json.dumps(char_data.get("relations", {})),
        "currentMoodJson": json.dumps(char_data.get("currentMood", {})),
        "imageUrl": char_data.get("imageUrl") # If present
    }

    # Faction extraction
    if "faction" in profile:
        payload["faction"] = profile["faction"].get("name")

    url = f"{API_BASE}/projects/{PROJECT_ID}/characters"

    print(f"Creating character: {payload['name']}...")
    status, data = post_json(url, payload)

    if status in [200, 201] and data:
        data_body = data.get("data", {})
        print(f"Success: {data_body.get('id')} ({data_body.get('name')})")
        return data_body.get("name"), data_body.get("id")
    else:
        print(f"Failed to create character")
        return None, None

def create_relationship(rel_data, name_to_id):
    source_name = rel_data.get("source")
    target_name = rel_data.get("target")

    source_id = name_to_id.get(source_name)
    target_id = name_to_id.get(target_name)

    if not source_id or not target_id:
        print(f"Skipping relationship {source_name} -> {target_name}: ID not found")
        return

    payload = {
        "sourceId": source_id,
        "targetId": target_id,
        "types": rel_data.get("relation_types", ["related"]),
        "strength": rel_data.get("strength", 5),
        "description": rel_data.get("description", "")
    }

    url = f"{API_BASE}/relationships"

    print(f"Creating relationship: {source_name} -> {target_name}...")
    status, data = post_json(url, payload)

    if status in [200, 201]:
        print("Success")
    else:
        print(f"Failed relationship creation")

def main():
    data = load_data()
    result = data.get("result", {})
    characters = result.get("characters", [])
    relationships = result.get("relationships", [])

    name_to_id = {}

    # Create Characters
    for char in characters:
        name, char_id = create_character(char)
        if name and char_id:
            name_to_id[name] = char_id

    # Create Relationships
    for rel in relationships:
        create_relationship(rel, name_to_id)

if __name__ == "__main__":
    main()
