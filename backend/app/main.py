from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers.transcriptions import router as transcriptions_router

app = FastAPI(title="Moa Voice Transcription API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["POST", "OPTIONS"],
    allow_headers=["Content-Type"],
)
app.include_router(transcriptions_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
