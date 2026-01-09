import urllib.request
import urllib.error
import json

API_BASE = "http://localhost:8080/api"
PROJECT_ID = "6e686700-34cf-4b23-ae2d-f673743dc4ce"
TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiI1OWMzOWZlZC00MDRjLTQ4MjUtYjJiYi1iNDk5NmE1YzliNmQiLCJzdWIiOiJjODM0MDM0OC0wM2U5LTQzNDYtYjJmMi1kNmM4MzFiZTcyOGUiLCJpYXQiOjE3Njc5NjAwODcsImV4cCI6MTc2Nzk2MTg4NywidHlwZSI6ImFjY2VzcyJ9.40b5F93IB16xokH1ELLUj0e9tC0L3KBx6hkf9yLg7g4wyCMU_X00b7n6IjcQb6JbXRY1KFQPZzso0x2jsAJVLQ"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json",
    "Origin": "http://localhost:3000"
}

def make_request(url, method="GET", data=None):
    if data:
        data = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            if response.status >= 200 and response.status < 300:
                if method != "DELETE":
                    return json.loads(response.read().decode('utf-8'))
                return {"success": True}
    except urllib.error.HTTPError as e:
        print(f"Error {method} {url}: {e.code} - {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Error {method} {url}: {e}")
    return None

def main():
    print("Fetching relationships...")
    rel_data = make_request(f"{API_BASE}/projects/{PROJECT_ID}/relationships")
    if rel_data and rel_data.get("success"):
        rels = rel_data.get("data", [])
        print(f"Found {len(rels)} relationships. Deleting...")
        for rel in rels:
            make_request(f"{API_BASE}/relationships/{rel['id']}", method="DELETE")

    print("Fetching characters...")
    char_data = make_request(f"{API_BASE}/projects/{PROJECT_ID}/characters")
    if char_data and char_data.get("success"):
        chars = char_data.get("data", [])
        print(f"Found {len(chars)} characters. Deleting...")
        for char in chars:
            make_request(f"{API_BASE}/characters/{char['id']}", method="DELETE")

    print("Cleanup complete.")

if __name__ == "__main__":
    main()
