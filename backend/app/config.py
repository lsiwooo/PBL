from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    openai_api_key: str = ""
    openai_transcription_model: str = "gpt-4o-mini-transcribe"
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
