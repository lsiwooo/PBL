export type RecordingPhase = 'idle' | 'recording' | 'transcribing'

export type TranscriptionResult = {
  text: string
  language?: string | null
}

const MAX_RECORDING_SECONDS = 120
const MAX_FILE_BYTES = 25 * 1024 * 1024

export function getPreferredMimeType(): string {
  if (typeof MediaRecorder === 'undefined') return ''
  return ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']
    .find((type) => MediaRecorder.isTypeSupported(type)) ?? ''
}

export async function transcribeAudio(blob: Blob, signal?: AbortSignal): Promise<TranscriptionResult> {
  if (blob.size === 0) throw new Error('녹음된 음성이 비어 있어요. 다시 녹음해 주세요.')
  if (blob.size > MAX_FILE_BYTES) throw new Error('음성 파일은 25MB 이하로 녹음해 주세요.')

  const form = new FormData()
  const extension = blob.type.includes('mp4') ? 'm4a' : blob.type.includes('ogg') ? 'ogg' : 'webm'
  form.append('file', blob, `recording.${extension}`)

  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1/transcriptions`, {
    method: 'POST',
    body: form,
    signal,
  })
  const result: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = typeof result === 'object' && result !== null && 'detail' in result && typeof result.detail === 'string'
      ? result.detail
      : '음성을 글로 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.'
    throw new Error(message)
  }
  if (typeof result !== 'object' || result === null || !('text' in result) || typeof result.text !== 'string') {
    throw new Error('음성 인식 서버에서 올바른 결과를 받지 못했어요.')
  }
  const language = 'language' in result && typeof result.language === 'string' ? result.language : null
  return { text: result.text, language }
}

export const maxRecordingSeconds = MAX_RECORDING_SECONDS
