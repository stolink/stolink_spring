import json
import uuid
import psycopg2
from psycopg2.extras import Json

# Configuration (from .env)
DB_HOST = "localhost"
DB_NAME = "stolink"
DB_USER = "stolink"
DB_PASS = "stolink123"
DB_PORT = "5432"

PROJECT_ID = "e2a08b38-9049-4647-b9f0-1cac7792a2d7"

def load_data():
    with open("dummy_data.json", "r") as f:
        return json.load(f)

def seed_postgres():
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASS,
            port=DB_PORT
        )
        cur = conn.cursor()
        print("Connected to PostgreSQL")

        data = load_data()
        sections = data.get("sections", [])

        if not sections:
            print("No sections found in dummy_data.json")
            return

        # 1. Ensure a Document exists to hold these sections
        # Check for existing document or create one
        doc_title = "Three Kingdoms Timeline"

        # Check if project exists first? Assuming yes from other seeds.

        # Find or Create Root Document
        # We need a document ID. Let's start fresh for this run or find it.
        # Let's search for a document with this title in this project.
        cur.execute(
            "SELECT id FROM documents WHERE project_id = %s AND title = %s",
            (PROJECT_ID, doc_title)
        )
        row = cur.fetchone()

        if row:
            doc_id = row[0]
            print(f"Found existing document: {doc_id}")
            # Clear existing sections and events for this document to avoid duplicates
            cur.execute("DELETE FROM sections WHERE document_id = %s", (doc_id,))
            cur.execute("DELETE FROM events WHERE document_id = %s", (doc_id,))
            print(f"Cleared existing sections and events for {doc_id}")
        else:
            doc_id = str(uuid.uuid4())
            print(f"Creating new document: {doc_id}")
            # Insert Document
            # status 'draft', type 'TEXT' (enum?) - Check entity definition if possible, usually string in DB from enum
            # type is likely 'TEXT' or 'FOLDER'. API said 'text'.
            cur.execute("""
                INSERT INTO documents (id, project_id, title, type, status, include_in_compile, "order", word_count, is_published, created_at, updated_at)
                VALUES (%s, %s, %s, 'TEXT', 'DRAFT', FALSE, 0, 0, FALSE, NOW(), NOW())
            """, (doc_id, PROJECT_ID, doc_title))

        # 2. Insert Sections
        print(f"Inserting {len(sections)} sections...")

        for section in sections:
            sec_id = str(uuid.uuid4())
            seq = section["sequence_order"]
            title = section["nav_title"]
            content = section["content"]
            embedding = section["embedding"] # List of floats

            # nav_title maps to navTitle in Entity
            # content maps to content
            # embedding maps to vector column

            # embedding is float[] in Java, usually mapped to pgvector which takes string array format or binary?
            # psycopg2 handles lists as arrays. pgvector might need specific casting or string format '[1,2,3]'
            # safely formatting as string for vector type
            embedding_str = str(embedding)

            cur.execute("""
                INSERT INTO sections (id, document_id, sequence_order, nav_title, content, embedding, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s::vector, NOW(), NOW())
            """, (sec_id, doc_id, seq, title, content, embedding_str))

            # --- ALSO Insert into 'events' table for frontend visualizer ---
            evt_id = str(uuid.uuid4())

            # Map participants list to JSON array string
            participants_list = section.get("participants", [])
            participants_json = json.dumps(participants_list, ensure_ascii=False)

            # Use nav_title as event Name
            # Use content as description
            # Use separate summary for narrative_summary
            event_type = section.get("event_type", "Narrative")
            summary = section.get("summary", content) # Fallback to content if missing
            importance = section.get("importance", 8.0)

            cur.execute("""
                INSERT INTO events (
                    id, project_id, event_id, name, description, event_type,
                    participants, sequence_order, document_id, importance_score,
                    narrative_summary, created_at, updated_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
            """, (
                evt_id, PROJECT_ID, f"evt_{seq:03d}", title, content, event_type,
                participants_json, seq, doc_id, importance,
                summary
            ))

        conn.commit()
        print("Seeding complete.")

    except Exception as e:
        print(f"Error seeding Postgres: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn:
            cur.close()
            conn.close()

if __name__ == "__main__":
    seed_postgres()
