from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    gemini_api_key: str = ""
    gemini_transcription_model: str = "gemini-3.5-transcribe"
    gemini_ledger_model: str = "gemini-3.6-flash"
    model_config = SettingsConfigDict(env_file=("backend/.env", ".env"), extra="ignore")


settings = Settings()
