"""
Marvo AI — Image Generation Agent
==================================
Dual-stage image generation pipeline:
Step 1: High-fidelity Hugging Face Inference API (Stable Diffusion XL / FLUX).
Step 2: Instant resilient fallback to Pollinations.ai direct URL.
"""

import os
import re
import base64
import random
import logging
import urllib.parse
import requests

# Securely load environment variables from .env
try:
    from dotenv import load_dotenv
    _env_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env')
    if os.path.isfile(_env_file):
        load_dotenv(_env_file)
    else:
        load_dotenv()
except Exception:
    pass

logger = logging.getLogger("marvo.agents.image")

# Hugging Face Inference Endpoint for high-grade visual generation
HF_INFERENCE_URL = "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0"
HF_REQUEST_TIMEOUT = 14  # seconds timeout before graceful fallback


def clean_image_prompt(raw_prompt: str) -> str:
    """
    Extracts the core visual description by stripping trigger verbs and command phrases.
    """
    if not raw_prompt:
        return "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    cleaned = raw_prompt.strip()

    # Regex patterns to strip leading generation commands
    patterns = [
        r"^(?:please\s+)?(?:generate|create|make|produce)\s+(?:an?\s+)?(?:image|picture|photo|illustration|render|wallpaper)\s+(?:of\s+|about\s+|showing\s+)?",
        r"^(?:please\s+)?(?:draw|paint|sketch|illustrate)(?:\s+me)?(?:\s+an?)?\s+(?:image|picture|photo\s+of\s+|of\s+)?",
        r"^(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|of\s+)?",
        r"^(?:photo|picture|wallpaper|render)\s+of\s+",
    ]

    for pat in patterns:
        matched = re.sub(pat, "", cleaned, flags=re.IGNORECASE).strip()
        if matched and matched != cleaned:
            cleaned = matched
            break

    # Strip extraneous punctuation at start or end
    cleaned = cleaned.strip(' :,-"\'.')
    if not cleaned:
        cleaned = "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    return cleaned


def generate_image(prompt: str, hf_api_key: str = None) -> dict:
    """
    Generates an image from a text prompt.
    Tries Hugging Face SDXL first; on any failure/timeout, falls back to Pollinations.ai.
    Returns:
        dict: {
            "type": "image",
            "content": "<base64_or_url>",
            "prompt": "<clean_prompt>",
            "source": "huggingface" | "pollinations",
            "response": "<text_description>",
            "state": "state-amazed"
        }
    """
    clean_prompt = clean_image_prompt(prompt)
    api_key = (hf_api_key or os.environ.get("HF_API_KEY", "")).strip()

    # ──────────────────────────────────────────────────────────────────
    # Step 1: Hugging Face Inference API
    # ──────────────────────────────────────────────────────────────────
    if api_key:
        try:
            logger.info(f"[ImageAgent] Attempting Hugging Face SDXL for prompt: {clean_prompt[:60]}...")
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": "Marvo-AI-Agent/1.0"
            }
            payload = {
                "inputs": clean_prompt,
                "parameters": {
                    "negative_prompt": "blurry, low quality, distorted, watermark, deformed",
                    "num_inference_steps": 30,
                    "guidance_scale": 7.5
                }
            }

            response = requests.post(
                HF_INFERENCE_URL,
                headers=headers,
                json=payload,
                timeout=HF_REQUEST_TIMEOUT
            )

            # If successful and returned image bytes
            if response.status_code == 200 and response.content:
                content_type = response.headers.get("Content-Type", "image/jpeg")
                if "image" in content_type or len(response.content) > 1024:
                    base64_img = base64.b64encode(response.content).decode("utf-8")
                    data_uri = f"data:{content_type};base64,{base64_img}"
                    logger.info("[ImageAgent] Successfully generated image via Hugging Face SDXL!")
                    return {
                        "type": "image",
                        "content": data_uri,
                        "prompt": clean_prompt,
                        "source": "huggingface",
                        "response": f"Here is the generated image for: \"{clean_prompt}\"",
                        "state": "state-amazed"
                    }
                else:
                    logger.warning(f"[ImageAgent] HF returned non-image content: {content_type}")
            else:
                logger.warning(f"[ImageAgent] HF request status {response.status_code}: {response.text[:120]}")

        except Exception as hf_err:
            logger.warning(f"[ImageAgent] Hugging Face generation error, falling back to Pollinations: {hf_err}")

    # ──────────────────────────────────────────────────────────────────
    # Step 2: Instant Fallback to Pollinations.ai Direct URL
    # ──────────────────────────────────────────────────────────────────
    logger.info(f"[ImageAgent] Using Pollinations.ai fallback for prompt: {clean_prompt[:60]}")
    encoded_prompt = urllib.parse.quote(clean_prompt)
    seed = random.randint(100000, 999999)
    pollinations_url = (
        f"https://image.pollinations.ai/prompt/{encoded_prompt}"
        f"?width=1024&height=1024&nologo=true&seed={seed}"
    )

    return {
        "type": "image",
        "content": pollinations_url,
        "prompt": clean_prompt,
        "source": "pollinations",
        "response": f"Here is the generated image for: \"{clean_prompt}\"",
        "state": "state-amazed"
    }
