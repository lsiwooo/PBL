from openai import AsyncOpenAI

from app.config import settings


class TranscriptionUnavailable(Exception):
    pass


async def transcribe_audio(audio_file) -> str:
    if not settings.openai_api_key:
        raise TranscriptionUnavailable("음성 인식 서버 설정이 완료되지 않았어요.")
    client = AsyncOpenAI(api_key=settings.openai_api_key)
    try:
        response = await client.audio.transcriptions.create(
            model=settings.openai_transcription_model,
            file=audio_file,
            language="ko",
            prompt="한국어 가계부 음성 기록입니다. 금액, 통화 단위, 상호명을 정확히 받아 적으세요.",
        )
        return response.text.strip()
    finally:
        await client.close()
