"""
Marvo AI — Image Generation Agent
==================================
Multi-Layer Free Image Generation Pipeline:
- Step 1 (Primary): Hugging Face Inference API with HF_API_KEY (appends "masterpiece, 4k, highly detailed, HD").
- Step 2 (Free Fallback): Instant bulletproof fallback to Pollinations.ai (free, no auth required).
  URL: https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true
- Returns {"type": "image", "content": "<url_or_base64>"}
"""

import os
import re
import base64
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
HF_REQUEST_TIMEOUT = 12  # seconds timeout before graceful fallback

# High-Quality Modifiers specified by user
QUALITY_MODIFIERS = "masterpiece, 4k, highly detailed, HD"


def clean_image_prompt(raw_prompt: str) -> str:
    """
    Extracts the core visual description by stripping trigger verbs and command phrases.
    """
    if not raw_prompt:
        return "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    cleaned = raw_prompt.strip()

    patterns = [
        r"^(?:please\s+)?(?:generate|create|creat|make|produce|render)\s+(?:an?\s+)?(?:image|picture|pic|photo|illustration|render|wallpaper|thumbnail|drawing|artwork|poster)\s+(?:of\s+|about\s+|showing\s+|for\s+)?",
        r"^(?:please\s+)?(?:draw|paint|sketch|illustrate|design)(?:\s+me)?(?:\s+an?)?\s+(?:image|picture|pic|photo\s+of\s+|of\s+|a\s+|an\s+)?",
        r"^(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|of\s+)?",
        r"^(?:photo|picture|pic|wallpaper|render|thumbnail)\s+of\s+",
        r"^(?:make|design)\s+(?:a\s+|an\s+)?thumbnail\s+(?:for|of)?\s*",
    ]

    for pat in patterns:
        matched = re.sub(pat, "", cleaned, flags=re.IGNORECASE).strip()
        if matched and matched != cleaned:
            cleaned = matched
            break

    cleaned = cleaned.strip(' :,-"\'.')
    if not cleaned:
        cleaned = "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    return cleaned


def enhance_prompt(base_prompt: str) -> str:
    """
    Appends high-quality visual modifiers to elevate the generated output.
    """
    cleaned = clean_image_prompt(base_prompt)
    lower = cleaned.lower()
    if "4k" not in lower and "masterpiece" not in lower:
        return f"{cleaned}, {QUALITY_MODIFIERS}"
    return cleaned


def generate_image(prompt: str, hf_api_key: str = None) -> dict:
    """
    Bulletproof multi-layer image generation:
    - Step 1: Try Hugging Face API with HF_API_KEY
    - Step 2: Instant resilient fallback to Pollinations.ai
    Returns:
        dict: {
            "type": "image",
            "content": "<url_or_base64>",
            "prompt": "<clean_prompt>",
            "enhanced_prompt": "<enhanced_prompt>",
            "source": "huggingface" | "pollinations",
            "response": "<text_description>",
            "state": "state-amazed"
        }
    """
    clean_prompt = clean_image_prompt(prompt)
    enhanced_prompt = enhance_prompt(clean_prompt)
    api_key = (hf_api_key or os.environ.get("HF_API_KEY", "")).strip()

    # ──────────────────────────────────────────────────────────────────
    # Step 1: Hugging Face Inference API (Primary)
    # ──────────────────────────────────────────────────────────────────
    if api_key:
        try:
            logger.info(f"[ImageAgent] Step 1: Attempting Hugging Face SDXL for prompt: {enhanced_prompt[:60]}...")
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": "Marvo-AI-Agent/1.0"
            }
            payload = {
                "inputs": enhanced_prompt,
                "parameters": {
                    "negative_prompt": "blurry, low quality, distorted, deformed, watermark, text, grainy",
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

            if response.status_code == 200 and response.content:
                content_type = response.headers.get("Content-Type", "image/jpeg")
                if "image" in content_type or len(response.content) > 1024:
                    base64_img = base64.b64encode(response.content).decode("utf-8")
                    data_uri = f"data:{content_type};base64,{base64_img}"
                    logger.info("[ImageAgent] Step 1 Success: Generated image via Hugging Face SDXL!")
                    return {
                        "type": "image",
                        "content": data_uri,
                        "prompt": clean_prompt,
                        "enhanced_prompt": enhanced_prompt,
                        "source": "huggingface",
                        "response": f"Here is the generated image for: \"{clean_prompt}\"",
                        "state": "state-amazed"
                    }
                else:
                    logger.warning(f"[ImageAgent] HF returned non-image content type: {content_type}")
            else:
                logger.warning(f"[ImageAgent] HF returned status code {response.status_code}: {response.text[:100]}")

        except Exception as hf_err:
            logger.warning(f"[ImageAgent] HF request failed or timed out: {hf_err}")

    # ──────────────────────────────────────────────────────────────────
    # Step 2: Pollinations.ai Free Fallback (No Auth Needed)
    # ──────────────────────────────────────────────────────────────────
    logger.info(f"[ImageAgent] Step 2: Falling back to Pollinations.ai for prompt: {enhanced_prompt[:60]}")
    encoded_prompt = urllib.parse.quote(enhanced_prompt)
    url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true"

    return {
        "type": "image",
        "content": url,
        "prompt": clean_prompt,
        "enhanced_prompt": enhanced_prompt,
        "source": "pollinations",
        "response": f"Here is the generated image for: \"{clean_prompt}\"",
        "state": "state-amazed"
    }
