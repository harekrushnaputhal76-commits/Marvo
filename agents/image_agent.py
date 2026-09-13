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
import time
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

# Hugging Face High-Grade Visual Models
HF_SDXL_URL = "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0"
HF_FLUX_URL = "https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-dev"
HF_INFERENCE_URL = HF_SDXL_URL

# CRITICAL: 60-90 second timeout tolerance for Hugging Face cold starts
HF_REQUEST_TIMEOUT = 75

# High-Quality Modifiers per user requirements
PRO_QUALITY_MODIFIERS = "masterpiece, highly detailed, 8k resolution, cinematic lighting, ultra-realistic, professional photography"
FAST_QUALITY_MODIFIERS = "masterpiece, 4k, highly detailed"
QUALITY_MODIFIERS = PRO_QUALITY_MODIFIERS


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


def enhance_prompt(base_prompt: str, mode: str = "Thinking") -> str:
    """
    Appends high-quality visual modifiers according to the active mode.
    """
    cleaned = sanitize_image_prompt(base_prompt)
    lower = cleaned.lower()
    norm_mode = str(mode or "Thinking").strip().lower()
    modifiers = PRO_QUALITY_MODIFIERS if norm_mode in ["thinking", "pro", "high", "medium"] else FAST_QUALITY_MODIFIERS

    if "masterpiece" not in lower and "highly detailed" not in lower and "4k" not in lower and "8k" not in lower:
        return f"{cleaned}, {modifiers}"
    return cleaned


def generate_image(prompt: str, mode: str = "Thinking", hf_api_key: str = None) -> dict:
    """
    Mode-driven multi-layer image generation:
    - IF MODE IS 'Fast': Skip Hugging Face and immediately use Pollinations.ai for instant visual generation.
    - IF MODE IS 'Thinking' or 'Pro':
      - Use high-quality Hugging Face model (SDXL 1.0 or FLUX.1-dev)
      - Enhance with: 'masterpiece, highly detailed, 8k resolution, cinematic lighting, ultra-realistic, professional photography'
      - High timeout (75s) to tolerate Hugging Face cold starts
      - Handle 503 (Model loading) with a retry
      - Detailed print() error logging for Render debugging
      - Only fallback to Pollinations if Hugging Face critically fails
    """
    clean_prompt = sanitize_image_prompt(prompt)
    norm_mode = str(mode or "Thinking").strip()
    is_fast = norm_mode.lower() in ["fast"]

    enhanced_prompt = enhance_prompt(clean_prompt, mode=norm_mode)
    api_key = (hf_api_key or os.environ.get("HF_API_KEY", "")).strip()

    print(f"[ImageAgent] Processing prompt: '{clean_prompt}' | Mode: '{norm_mode}' | Enhanced: '{enhanced_prompt[:70]}...'")

    # ──────────────────────────────────────────────────────────────────
    # Step 1: Fast Mode -> Direct Instant Pollinations Generation
    # ──────────────────────────────────────────────────────────────────
    if is_fast:
        print("[ImageAgent] Mode is 'Fast' -> Skipping Hugging Face and immediately dispatching to Pollinations.ai.")
        encoded_prompt = urllib.parse.quote(enhanced_prompt, safe='')
        seed = int(time.time() * 1000) % 1000000
        url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true&seed={seed}"
        return {
            "type": "image",
            "content": url,
            "prompt": clean_prompt,
            "enhanced_prompt": enhanced_prompt,
            "source": "pollinations",
            "mode": "Fast",
            "response": f"Here is your instant visual generation for: \"{clean_prompt}\"",
            "state": "state-creative"
        }

    # ──────────────────────────────────────────────────────────────────
    # Step 2: Thinking / Pro Mode -> Hugging Face High-Quality Generation
    # ──────────────────────────────────────────────────────────────────
    # Pro mode targets FLUX.1-dev or SDXL base 1.0; Thinking targets SDXL base 1.0
    use_flux = norm_mode.lower() in ["pro", "high"]
    model_url = HF_FLUX_URL if use_flux else HF_SDXL_URL
    model_name = "FLUX.1-dev" if use_flux else "stabilityai/stable-diffusion-xl-base-1.0"

    if api_key:
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": "Marvo-AI-Agent/1.0"
        }
        payload = {
            "inputs": enhanced_prompt,
            "parameters": {
                "negative_prompt": "blurry, low quality, distorted, deformed, watermark, text, grainy, bad anatomy, ugly",
                "num_inference_steps": 35 if use_flux else 30,
                "guidance_scale": 7.5
            }
        }

        print(f"[ImageAgent] Mode is '{norm_mode}' -> Connecting to Hugging Face ({model_name}). Timeout: {HF_REQUEST_TIMEOUT}s...")

        try:
            response = requests.post(
                model_url,
                headers=headers,
                json=payload,
                timeout=HF_REQUEST_TIMEOUT
            )

            # Handle Hugging Face 503 (Model Loading) error with an automated retry
            if response.status_code == 503:
                print(f"[ImageAgent 503] Hugging Face Model is loading: {response.text[:200]}")
                try:
                    est_time = response.json().get("estimated_time", 20)
                except Exception:
                    est_time = 20
                sleep_time = min(max(float(est_time), 5.0), 20.0)
                print(f"[ImageAgent] Waiting {sleep_time:.1f}s for model warm-up before retry...")
                time.sleep(sleep_time)
                response = requests.post(
                    model_url,
                    headers=headers,
                    json=payload,
                    timeout=HF_REQUEST_TIMEOUT
                )

            if response.status_code == 200 and response.content:
                content_type = response.headers.get("Content-Type", "image/jpeg")
                if "image" in content_type or len(response.content) > 1024:
                    base64_img = base64.b64encode(response.content).decode("utf-8")
                    data_uri = f"data:{content_type};base64,{base64_img}"
                    print(f"[ImageAgent Success] Generated high-res visual via Hugging Face ({model_name})!")
                    return {
                        "type": "image",
                        "content": data_uri,
                        "prompt": clean_prompt,
                        "enhanced_prompt": enhanced_prompt,
                        "source": "huggingface",
                        "mode": norm_mode,
                        "response": f"Here is the high-definition image generated with Hugging Face ({model_name}) for: \"{clean_prompt}\"",
                        "state": "state-amazed"
                    }
                else:
                    print(f"[ImageAgent Error] Hugging Face returned non-image content type: {content_type}")
            else:
                # CRITICAL: Detailed print logs for Render dashboard debugging
                print(f"[ImageAgent Error] Hugging Face Generation Failed! Status Code: {response.status_code} | Response Text: {response.text}")

        except requests.Timeout:
            print(f"[ImageAgent Error] Hugging Face request timed out after {HF_REQUEST_TIMEOUT}s! (Cold start or congested)")
        except Exception as hf_err:
            print(f"[ImageAgent Error] Hugging Face unexpected exception: {type(hf_err).__name__} - {hf_err}")
    else:
        print("[ImageAgent Notice] HF_API_KEY is not configured in environment. Using Pollinations fallback.")

    # ──────────────────────────────────────────────────────────────────
    # Step 3: Resilient Fallback to Pollinations.ai
    # ──────────────────────────────────────────────────────────────────
    print(f"[ImageAgent] Falling back to Pollinations.ai for: {enhanced_prompt[:60]}...")
    encoded_prompt = urllib.parse.quote(enhanced_prompt, safe='')
    seed = int(time.time() * 1000) % 1000000
    url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true&seed={seed}"

    return {
        "type": "image",
        "content": url,
        "prompt": clean_prompt,
        "enhanced_prompt": enhanced_prompt,
        "source": "pollinations",
        "mode": norm_mode,
        "response": f"Here is the generated image for: \"{clean_prompt}\"",
        "state": "state-amazed"
    }
