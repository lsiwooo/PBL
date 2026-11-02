from google import genai
from google.genai import types

from app.config import settings


class TranscriptionUnavailable(Exception):
    pass


async def transcribe_audio(audio_file: tuple[str, bytes, str]) -> str:
    if not settings.gemini_api_key:
        raise TranscriptionUnavailable("Gemini API 키 설정이 필요해요. backend/.env를 확인해 주세요.")

    _, audio_data, content_type = audio_file
    mime_type = "audio/m4a" if content_type == "audio/mp4" else content_type
    client = genai.Client(api_key=settings.gemini_api_key)
    async_client = client.aio
    try:
        response = await async_client.models.generate_content(
            model=settings.gemini_transcription_model,
            contents=[types.Part.from_bytes(data=audio_data, mime_type=mime_type)],
            config=types.GenerateContentConfig(
                audio_transcription_config=types.AudioTranscriptionConfig(
                    mode="VERBATIM",
                    language_codes=["ko-KR"],
                ),
            ),
        )
        text_parts: list[str] = []
        audio_words: list[str] = []
        for candidate in response.candidates or []:
            if candidate.content is None:
                continue
            for part in candidate.content.parts or []:
                part_text = (part.text or "").strip()
                if part_text:
                    text_parts.append(part_text)
                transcription = getattr(part, "audio_transcription", None)
                transcription_text = (getattr(transcription, "text", "") or "").strip()
                if transcription_text:
                    text_parts.append(transcription_text)
                    continue
                for word in getattr(transcription, "words", []) or []:
                    value = (getattr(word, "word", "") or "").strip()
                    if value:
                        audio_words.append(value)

        if text_parts:
            return "\n".join(text_parts)
        if audio_words:
            return " ".join(audio_words)
        return (response.text or "").strip()
    finally:
        await async_client.aclose()
