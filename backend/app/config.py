from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    gemini_api_key: str = ""
    gemini_transcription_model: str = "gemini-3.5-transcribe"
    # 자연어 가계부 분석 모델. 일시적으로 서버가 바쁘면(503 등) 아래 순서대로 다음 모델로 전환합니다.
    # 순서: 기본 -> fallback 1 -> fallback 2 -> fallback 3
    gemini_ledger_model: str = "gemini-3.8-flash"
    gemini_ledger_fallback_model: str = "gemini-3.7-flash"
    gemini_ledger_fallback_model_2: str = "gemini-3.6-flash"
    gemini_ledger_fallback_model_3: str = "gemini-3.5-flash-lite"
    model_config = SettingsConfigDict(env_file=("backend/.env", ".env"), extra="ignore")


settings = Settings()
