"""
Marvo AI — Image Generation Agent
==================================
High-Quality Visual Generation Pipeline:
- Enhances user prompts with ultra-realistic, 4K, masterpiece visual tokens.
- Step 1: High-fidelity Hugging Face Inference API (Stable Diffusion XL Base 1.0).
- Step 2: Resilient instant fallback to Pollinations.ai direct URL.
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

# High-Quality Modifiers to enrich prompts for flagship aesthetics
QUALITY_MODIFIERS = "masterpiece, highly detailed, 4k resolution, HD, ultra-realistic, sharp focus, cinematic lighting"


def clean_image_prompt(raw_prompt: str) -> str:
    """
    Extracts the core visual description by stripping trigger verbs and command phrases.
    """
    if not raw_prompt:
        return "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    cleaned = raw_prompt.strip()

    # Regex patterns to strip leading generation commands
    patterns = [
        r"^(?:please\s+)?(?:generate|create|make|produce|render)\s+(?:an?\s+)?(?:image|picture|photo|illustration|render|wallpaper|thumbnail|drawing|artwork|poster)\s+(?:of\s+|about\s+|showing\s+|for\s+)?",
        r"^(?:please\s+)?(?:draw|paint|sketch|illustrate|design)(?:\s+me)?(?:\s+an?)?\s+(?:image|picture|photo\s+of\s+|of\s+|a\s+|an\s+)?",
        r"^(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|of\s+)?",
        r"^(?:photo|picture|wallpaper|render|thumbnail)\s+of\s+",
        r"^(?:make|design)\s+(?:a\s+|an\s+)?thumbnail\s+(?:for|of)?\s*",
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


def enhance_prompt(base_prompt: str) -> str:
    """
    Appends high-quality visual modifiers to elevate the generated output.
    """
    cleaned = clean_image_prompt(base_prompt)
    # Check if quality modifiers already present
    lower = cleaned.lower()
    if "4k" not in lower and "masterpiece" not in lower and "ultra-realistic" not in lower:
        return f"{cleaned}, {QUALITY_MODIFIERS}"
    return cleaned


def generate_image(prompt: str, hf_api_key: str = None) -> dict:
    """
    Generates an image from a text prompt.
    Automatically enriches the prompt with high-quality modifiers.
    Tries Hugging Face SDXL first; on any failure/timeout, falls back to Pollinations.ai.
    Returns:
        dict: {
            "type": "image",
            "content": "<base64_or_url>",
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
    # Step 1: Hugging Face Inference API
    # ──────────────────────────────────────────────────────────────────
    if api_key:
        try:
            logger.info(f"[ImageAgent] Attempting Hugging Face SDXL with enhanced prompt: {enhanced_prompt[:70]}...")
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": "Marvo-AI-Agent/1.0"
            }
            payload = {
                "inputs": enhanced_prompt,
                "parameters": {
                    "negative_prompt": "blurry, low quality, distorted, deformed, watermark, text, grainy, low resolution, bad anatomy",
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
                    logger.info("[ImageAgent] Successfully generated high-quality image via Hugging Face SDXL!")
                    return {
                        "type": "image",
                        "content": data_uri,
                        "prompt": clean_prompt,
                        "enhanced_prompt": enhanced_prompt,
                        "source": "huggingface",
                        "response": f"Here is the high-definition image for: \"{clean_prompt}\"",
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
    logger.info(f"[ImageAgent] Using Pollinations.ai fallback with enhanced prompt: {enhanced_prompt[:70]}")
    encoded_prompt = urllib.parse.quote(enhanced_prompt)
    seed = random.randint(100000, 999999)
    pollinations_url = (
        f"https://image.pollinations.ai/prompt/{encoded_prompt}"
        f"?width=1024&height=1024&nologo=true&seed={seed}"
    )

    return {
        "type": "image",
        "content": pollinations_url,
        "prompt": clean_prompt,
        "enhanced_prompt": enhanced_prompt,
        "source": "pollinations",
        "response": f"Here is the high-definition image for: \"{clean_prompt}\"",
        "state": "state-amazed"
    }
