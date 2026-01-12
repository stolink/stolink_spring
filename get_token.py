import urllib.request
import urllib.error
import json

API_BASE = "http://localhost:8080/api"

def login():
    url = f"{API_BASE}/auth/login"
    data = {
        "email": "dongha@example.com",
        "password": "password"
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={
            "Content-Type": "application/json",
            "Origin": "http://localhost:3000"
        }
    )

    try:
        with urllib.request.urlopen(req) as response:
            print("Headers:", response.headers)
            result = json.loads(response.read().decode('utf-8'))
            if result.get("success"):
                print("Login successful!")
                print(f"Token: {result['data']['accessToken']}")
                return result['data']['accessToken']
            else:
                print(f"Login failed: {result}")
    except urllib.error.HTTPError as e:
        print(f"Login failed: {e.code} - {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Login error: {e}")

if __name__ == "__main__":
    login()
