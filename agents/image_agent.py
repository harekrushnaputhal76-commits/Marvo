"""
Marvo AI — Image Generation Agent
==================================
Multi-Layer Free Image Generation Pipeline:
- Aggressive Prompt Sanitization: Strips bracketed system instructions,
  timestamps, and command prefixes to extract pure visual intent.
- Step 1 (Primary): Hugging Face Inference API with HF_API_KEY (appends "masterpiece, 4k, highly detailed").
- Step 2 (Free Fallback): Instant bulletproof fallback to Pollinations.ai (free, no auth required).
  URL: https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true
- Returns {"type": "image", "content": "<url_or_base64>", "prompt": "<cleaned_prompt>"}
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
QUALITY_MODIFIERS = "masterpiece, 4k, highly detailed"


def sanitize_image_prompt(raw_prompt: str) -> str:
    """
    Aggressively strips injected metadata, system instructions, brackets,
    and command prefixes to extract ONLY the pure visual intent.
    Example:
      Input: '[System Instructions...] [User Local Time...] User Question: CREAT DOG IMAGE'
      Output: 'DOG'
    """
    if not raw_prompt:
        return "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    text = str(raw_prompt)

    # 1. Strip ANY text enclosed in brackets: [...] across multiple lines
    text = re.sub(r"\[.*?\]", "", text, flags=re.DOTALL)

    # 2. Strip leading labels like "User Question:", "User:", "Question:", "Prompt:"
    text = re.sub(r"(?i)^\s*(?:user\s+question|user|question|prompt|input)\s*:\s*", "", text.strip())

    # If multiple lines remain, take the last non-empty line (where the actual question sits)
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if lines:
        text = lines[-1]
    else:
        text = text.strip()

    # Re-check for any label prefix on the single line
    text = re.sub(r"(?i)^\s*(?:user\s+question|user|question|prompt|input)\s*:\s*", "", text).strip()

    # 3. Strip trigger verbs and command phrases
    patterns = [
        r"(?i)^\s*(?:please\s+)?(?:generate|create|creat|make|produce|render)\s+(?:an?\s+)?(?:image|picture|pic|photo|illustration|render|wallpaper|thumbnail|drawing|artwork|poster)\s+(?:of\s+|about\s+|showing\s+|for\s+)?",
        r"(?i)^\s*(?:please\s+)?(?:draw|paint|sketch|illustrate|design)(?:\s+me)?(?:\s+an?)?\s+(?:image|picture|pic|photo\s+of\s+|of\s+|a\s+|an\s+)?",
        r"(?i)^\s*(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|of\s+)?",
        r"(?i)^\s*(?:photo|picture|pic|wallpaper|render|thumbnail)\s+of\s+",
        r"(?i)^\s*(?:make|design)\s+(?:a\s+|an\s+)?thumbnail\s+(?:for|of)?\s*",
        r"(?i)\b(?:image|photo|picture|pic|drawing|tasveer)\s+banao\b",
        r"(?i)^\s*(?:generate|creat|create|draw|paint|sketch|photo|image|pic)\s+",
    ]

    for pat in patterns:
        matched = re.sub(pat, "", text).strip()
        if matched and matched != text:
            text = matched
            break

    # 4. Strip stray punctuation and whitespace
    text = text.strip(' :,-"\'`\n\r\t.')

    # 5. Strip trailing generic nouns (e.g. "DOG IMAGE" -> "DOG")
    text = re.sub(r"(?i)\s+(?:image|picture|photo|pic|drawing|wallpaper)$", "", text).strip()

    # Final cleanup
    text = text.strip(' :,-"\'`\n\r\t.')
    if not text:
        text = "a futuristic cyberpunk cityscape at twilight with glowing neon, 8k resolution"

    return text


def enhance_prompt(base_prompt: str) -> str:
    """
    Appends high-quality visual modifiers to the sanitized prompt.
    """
    cleaned = sanitize_image_prompt(base_prompt)
    lower = cleaned.lower()
    if "4k" not in lower and "masterpiece" not in lower:
        return f"{cleaned}, {QUALITY_MODIFIERS}"
    return cleaned


def generate_image(prompt: str, hf_api_key: str = None) -> dict:
    """
    Bulletproof multi-layer image generation:
    - Step 1: Try Hugging Face API with HF_API_KEY
    - Step 2: Instant resilient fallback to Pollinations.ai with strict urllib quoting
    Returns:
        dict: {
            "type": "image",
            "content": "<url_or_base64>",
            "prompt": "<cleaned_prompt>",
            "enhanced_prompt": "<enhanced_prompt>",
            "source": "huggingface" | "pollinations",
            "response": "<text_description>",
            "state": "state-amazed"
        }
    """
    clean_prompt = sanitize_image_prompt(prompt)
    enhanced_prompt = enhance_prompt(clean_prompt)
    api_key = (hf_api_key or os.environ.get("HF_API_KEY", "")).strip()

    logger.info(f"[ImageAgent] Sanitized prompt: '{clean_prompt}' | Enhanced: '{enhanced_prompt}'")

    # ──────────────────────────────────────────────────────────────────
    # Step 1: Hugging Face Inference API (Primary)
    # ──────────────────────────────────────────────────────────────────
    if api_key:
        try:
            logger.info(f"[ImageAgent] Step 1: Attempting Hugging Face SDXL for: {enhanced_prompt[:60]}...")
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
    # Step 2: Pollinations.ai Free Fallback (Strict URL Quoting)
    # ──────────────────────────────────────────────────────────────────
    logger.info(f"[ImageAgent] Step 2: Falling back to Pollinations.ai for: {enhanced_prompt}")
    # Strict URL encoding of the sanitized, enhanced prompt
    encoded_prompt = urllib.parse.quote(enhanced_prompt, safe='')
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
