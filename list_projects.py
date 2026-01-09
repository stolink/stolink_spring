import urllib.request
import urllib.error
import json

API_BASE = "http://localhost:8080/api"
TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiI1OWMzOWZlZC00MDRjLTQ4MjUtYjJiYi1iNDk5NmE1YzliNmQiLCJzdWIiOiJjODM0MDM0OC0wM2U5LTQzNDYtYjJmMi1kNmM4MzFiZTcyOGUiLCJpYXQiOjE3Njc5NjAwODcsImV4cCI6MTc2Nzk2MTg4NywidHlwZSI6ImFjY2VzcyJ9.40b5F93IB16xokH1ELLUj0e9tC0L3KBx6hkf9yLg7g4wyCMU_X00b7n6IjcQb6JbXRY1KFQPZzso0x2jsAJVLQ"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json",
    "Origin": "http://localhost:3000"
}

def list_projects():
    url = f"{API_BASE}/projects"
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            print(json.dumps(result, indent=2, ensure_ascii=False))
    except urllib.error.HTTPError as e:
        print(f"Error: {e.code} - {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    list_projects()
