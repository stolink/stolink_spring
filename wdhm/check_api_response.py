import urllib.request
import urllib.error
import json

API_BASE = "http://localhost:8080/api"
PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7"
TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiI1OWMzOWZlZC00MDRjLTQ4MjUtYjJiYi1iNDk5NmE1YzliNmQiLCJzdWIiOiJjODM0MDM0OC0wM2U5LTQzNDYtYjJmMi1kNmM4MzFiZTcyOGUiLCJpYXQiOjE3Njc5NjAwODcsImV4cCI6MTc2Nzk2MTg4NywidHlwZSI6ImFjY2VzcyJ9.40b5F93IB16xokH1ELLUj0e9tC0L3KBx6hkf9yLg7g4wyCMU_X00b7n6IjcQb6JbXRY1KFQPZzso0x2jsAJVLQ"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json",
    "Origin": "http://localhost:3000"
}

def check_api():
    print(f"Checking API for project {PROJECT_ID}...")

    # Check Characters
    print("\n[GET] /characters")
    try:
        url = f"{API_BASE}/projects/{PROJECT_ID}/characters"
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req) as response:
            res = json.loads(response.read().decode('utf-8'))
            print(f"Status: {res['status']}")
            if res.get('data'):
                print(f"Count: {len(res['data'])}")
                print("First item:", json.dumps(res['data'][0], indent=2, ensure_ascii=False))
            else:
                print("Data is empty/null")
    except urllib.error.HTTPError as e:
        print(f"Error: {e.code} - {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Error: {e}")

    # Check Relationships
    print("\n[GET] /relationships")
    try:
        url = f"{API_BASE}/projects/{PROJECT_ID}/relationships"
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req) as response:
            res = json.loads(response.read().decode('utf-8'))
            print(f"Status: {res['status']}")
            if res.get('data'):
                 print(f"Count: {len(res['data'])}")
            else:
                 print("Data is empty/null")
    except urllib.error.HTTPError as e:
        print(f"Error: {e.code} - {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    check_api()
