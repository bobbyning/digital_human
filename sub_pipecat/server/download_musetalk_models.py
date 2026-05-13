"""
Download missing MuseTalk model weights.

Run this script when network is available to download the face parsing model.
Some models can only be downloaded from Google Drive or specific HuggingFace repos.

Usage:
    E:/github_project/digital_human/sub_pipecat/server/venv/Scripts/python.exe download_musetalk_models.py
"""
import os
import sys

MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "MuseTalk", "models")


def download_face_parse_bisent():
    """Download the face parsing model (79999_iter.pth)."""
    target = os.path.join(MODEL_DIR, "face-parse-bisent", "79999_iter.pth")
    if os.path.exists(target) and os.path.getsize(target) > 40000000:
        print(f"79999_iter.pth exists ({os.path.getsize(target)} bytes), verifying...")
        try:
            import torch
            torch.load(target, map_location="cpu", weights_only=False)
            print("  OK - file is valid")
            return
        except Exception:
            print("  CORRUPTED - re-downloading")

    os.makedirs(os.path.dirname(target), exist_ok=True)

    # Try HuggingFace first
    try:
        from huggingface_hub import hf_hub_download
        print("Downloading from HuggingFace (zllrunning/face-makeup.PyTorch)...")
        hf_hub_download(
            repo_id="zllrunning/face-makeup.PyTorch",
            filename="79999_iter.pth",
            local_dir=os.path.dirname(target),
        )
        print(f"Downloaded to {target}")
        return
    except Exception as e:
        print(f"HuggingFace download failed: {e}")

    # Try Google Drive
    try:
        import gdown
        print("Downloading from Google Drive...")
        url = "https://drive.google.com/uc?id=154JgKpzCPW82qINcVieuPH3fZ2e0P812"
        gdown.download(url, target, quiet=False)
        print(f"Downloaded to {target}")
        return
    except Exception as e:
        print(f"Google Drive download failed: {e}")

    print("ERROR: Could not download 79999_iter.pth from any source.")
    print("Please download manually from:")
    print("  https://drive.google.com/file/d/154JgKpzCPW82qINcVieuPH3fZ2e0P812/view")
    print(f"  and save to: {target}")


if __name__ == "__main__":
    download_face_parse_bisent()
