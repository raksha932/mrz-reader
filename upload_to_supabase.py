import os
import glob
from dotenv import load_dotenv
from supabase import create_client, Client

# Load environment variables from .env file if present
load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL", "https://your-project-id.supabase.co")
SUPABASE_KEY = os.getenv("SUPABASE_KEY", "your-anon-or-service-role-key")
BUCKET_NAME = os.getenv("SUPABASE_BUCKET", "passport-images")

# Candidate directories for passport images
POSSIBLE_DIRS = [
    r"D:\Passport\images\lva_passport",
    r"D:\Passport\synthpass_samples",
    r"C:\Users\acer\OneDrive\Desktop\Passport\images\lva_passport",
    r"C:\Users\acer\OneDrive\Desktop\Passport\synthpass_samples",
    r"C:\Users\acer\OneDrive\Desktop\Passport",
]

def get_supabase_client() -> Client:
    global SUPABASE_URL
    if "your-project-id" in SUPABASE_URL or "your-anon" in SUPABASE_KEY:
        print("[!] Error: Please set your SUPABASE_URL and SUPABASE_KEY in .env or at the top of upload_to_supabase.py.")
        exit(1)
    
    # Ensure clean base URL without /rest/v1 suffix
    cleaned_url = SUPABASE_URL.split("/rest/v1")[0].rstrip("/")
    return create_client(cleaned_url, SUPABASE_KEY)

def upload_all_images():
    supabase = get_supabase_client()

    # Collect images
    image_paths = []
    for d in POSSIBLE_DIRS:
        if os.path.exists(d):
            # Only pick .jpg and .jpeg files
            files = glob.glob(os.path.join(d, "*.jpg")) + glob.glob(os.path.join(d, "*.jpeg"))
            image_paths.extend(files)

    # Deduplicate by filename
    seen = set()
    unique_images = []
    for p in image_paths:
        fname = os.path.basename(p)
        if fname not in seen:
            seen.add(fname)
            unique_images.append(p)

    print(f"[*] Found {len(unique_images)} unique passport images to upload.")

    success_count = 0
    for p in sorted(unique_images):
        filename = os.path.basename(p)
        storage_path = f"passports/{filename}"

        # 1. Upload to Supabase Storage Bucket
        with open(p, "rb") as f:
            file_bytes = f.read()
            try:
                supabase.storage.from_(BUCKET_NAME).upload(
                    path=storage_path,
                    file=file_bytes,
                    file_options={"content-type": "image/jpeg", "upsert": "true"}
                )
                print(f"[Uploaded] {filename} -> {storage_path}")
            except Exception as e:
                print(f"[Storage Warning] {filename}: {e}")

        # 2. Get Public URL
        public_url = supabase.storage.from_(BUCKET_NAME).get_public_url(storage_path)

        # 3. Insert record into Postgres Table
        record = {
            "filename": filename,
            "storage_path": storage_path,
            "image_url": public_url,
            "verification_status": "PENDING"
        }

        try:
            supabase.table("passports").upsert(record, on_conflict="filename").execute()
            print(f"[DB Saved] {filename}")
            success_count += 1
        except Exception as e:
            print(f"[DB Warning] {filename}: {e}")

    print(f"\n[+] Complete! Successfully processed {success_count}/{len(unique_images)} images.")

if __name__ == "__main__":
    upload_all_images()
