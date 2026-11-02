from fastapi import APIRouter, File, HTTPException, UploadFile
from pydantic import BaseModel

from app.services.transcription import TranscriptionUnavailable, transcribe_audio

router = APIRouter(prefix="/api/v1/transcriptions", tags=["transcriptions"])
MAX_AUDIO_BYTES = 25 * 1024 * 1024
ALLOWED_AUDIO_TYPES = {"audio/webm", "audio/mp4", "audio/ogg", "audio/mpeg", "audio/wav", "audio/x-wav"}


class TranscriptionResponse(BaseModel):
    text: str
    language: str = "ko"


@router.post("", response_model=TranscriptionResponse)
async def create_transcription(file: UploadFile = File(...)) -> TranscriptionResponse:
    content_type = (file.content_type or "").split(";")[0].lower()
    if content_type not in ALLOWED_AUDIO_TYPES:
        raise HTTPException(status_code=415, detail="지원하지 않는 음성 파일 형식이에요.")

    audio_data = await file.read(MAX_AUDIO_BYTES + 1)
    if not audio_data:
        raise HTTPException(status_code=400, detail="녹음된 음성이 비어 있어요.")
    if len(audio_data) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="음성 파일은 25MB 이하로 녹음해 주세요.")

    try:
        text = await transcribe_audio((file.filename or "recording.webm", audio_data, content_type))
    except TranscriptionUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=502, detail="음성을 글로 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.") from error
    if not text.strip():
        raise HTTPException(status_code=502, detail="Gemini 응답에 전사 문장이 없습니다. 조금 더 길고 또렷하게 녹음해 주세요.")
    return TranscriptionResponse(text=text)
