import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowDownLeft, ArrowLeft, ArrowUpRight, Check, ChevronRight, CircleHelp, Clock3, Mic, MicOff, Pencil, Sparkles, X } from 'lucide-react'
import { getPreferredMimeType, maxRecordingSeconds, transcribeAudio } from './lib/voice'
import type { RecordingPhase } from './lib/voice'

type Entry = { id: number; title: string; amount: string; category: string; icon: string; color: string; type: 'expense' | 'income' }

const initialEntries: Entry[] = [
  { id: 1, title: '점심 · 샐러디', amount: '₩ 12,500', category: '식비', icon: '🥗', color: 'mint', type: 'expense' },
  { id: 2, title: '커피 · 블루보틀', amount: '₩ 6,300', category: '카페', icon: '☕', color: 'peach', type: 'expense' },
  { id: 3, title: '월급', amount: '+ ₩ 2,800,000', category: '급여', icon: '💼', color: 'lavender', type: 'income' },
]

const examples = ['점심에 김치찌개 9천 원', '월급 280만 원 들어왔어', '택시비 15,400원 결제했어']

function parseSpokenEntry(text: string): Entry | null {
  const amountMatch = text.replace(/,/g, '').match(/(\d+(?:\.\d+)?)\s*(만\s*원|천\s*원|원|만원|천원)/)
  if (!amountMatch) return null
  const unit = amountMatch[2].replace(/ /g, '')
  const amount = Math.round(Number(amountMatch[1]) * (unit.includes('만') ? 10000 : unit.includes('천') ? 1000 : 1))
  if (!Number.isFinite(amount) || amount <= 0) return null

  const isIncome = /월급|급여|입금|들어왔|받았|수입/.test(text)
  const category = /커피|카페/.test(text) ? '카페' : /택시|버스|지하철|교통/.test(text) ? '교통' : /월급|급여|입금/.test(text) ? '급여' : /밥|점심|저녁|식사|김치찌개|치킨|식비/.test(text) ? '식비' : '기타'
  const title = text.replace(/\s*(\d[\d,.]*\s*(?:만\s*원|천\s*원|원|만원|천원)).*/, '').replace(/^(오늘|어제)\s*/, '').trim() || category
  return { id: Date.now(), title, amount: `${isIncome ? '+' : '−'} ₩ ${amount.toLocaleString('ko-KR')}`, category, icon: isIncome ? '💼' : category === '카페' ? '☕' : category === '교통' ? '🚕' : category === '식비' ? '🍲' : '✨', color: category === '카페' ? 'peach' : isIncome ? 'lavender' : 'mint', type: isIncome ? 'income' : 'expense' }
}

function formatTime(seconds: number): string {
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}

export default function App() {
  const [phase, setPhase] = useState<RecordingPhase>('idle')
  const [seconds, setSeconds] = useState(0)
  const [script, setScript] = useState('')
  const [entries, setEntries] = useState(initialEntries)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [today] = useState(() => new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()))
  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<number | null>(null)
  const requestRef = useRef<AbortController | null>(null)
  const recognitionRef = useRef<{ start: () => void; stop: () => void; onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>>; resultIndex: number }) => void) | null; onerror: ((event: { error: string }) => void) | null; onend: (() => void) | null } | null>(null)

  const stopTimer = useCallback(() => {
    if (timerRef.current !== null) window.clearInterval(timerRef.current)
    timerRef.current = null
  }, [])

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
  }, [])

  const finishTranscription = useCallback(async (blob: Blob) => {
    if (blob.size === 0) return
    setPhase('transcribing')
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    try {
      const result = await transcribeAudio(blob, controller.signal)
      setScript((current) => [current.trim(), result.text.trim()].filter(Boolean).join(' '))
      setError(result.text.trim() ? '' : '음성을 알아듣지 못했어요. 주변이 조용한 곳에서 다시 시도해 주세요.')
    } catch (cause) {
      if (cause instanceof Error && cause.name !== 'AbortError') setError(cause.message)
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null
        setPhase('idle')
      }
    }
  }, [])

  const startBrowserRecognition = useCallback(() => {
    type BrowserSpeechRecognition = new () => {
      lang: string
      continuous: boolean
      interimResults: boolean
      onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>>; resultIndex: number }) => void) | null
      onerror: ((event: { error: string }) => void) | null
      onend: (() => void) | null
      start: () => void
      stop: () => void
    }
    const browser = window as Window & { SpeechRecognition?: BrowserSpeechRecognition; webkitSpeechRecognition?: BrowserSpeechRecognition }
    const Recognition = browser.SpeechRecognition ?? browser.webkitSpeechRecognition
    if (!Recognition) return false
    const recognition = new Recognition()
    recognition.lang = 'ko-KR'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.onresult = (event) => {
      const transcript = Array.from({ length: event.results.length - event.resultIndex }, (_, index) => event.results[event.resultIndex + index]?.[0]?.transcript ?? '').join('')
      if (transcript.trim()) setScript((current) => `${current.trim()} ${transcript.trim()}`.trim())
    }
    recognition.onerror = (event) => {
      if (event.error !== 'no-speech' && event.error !== 'aborted') setError('실시간 음성 인식이 중단됐어요. 녹음 파일을 서버에서 변환하고 있어요.')
    }
    recognition.onend = () => { recognitionRef.current = null }
    recognitionRef.current = recognition
    try { recognition.start(); return true } catch { recognitionRef.current = null; return false }
  }, [])

  const startRecording = async () => {
    setError('')
    setSaved(false)
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('이 브라우저는 음성 녹음을 지원하지 않아요. 최신 Chrome, Edge, Safari를 이용해 주세요.')
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
      streamRef.current = stream
      const mimeType = getPreferredMimeType()
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
      recorderRef.current = recorder
      chunksRef.current = []
      recorder.ondataavailable = (event) => { if (event.data.size > 0) chunksRef.current.push(event.data) }
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || mimeType || 'audio/webm' })
        chunksRef.current = []
        stopStream()
        void finishTranscription(blob)
      }
      recorder.start(1000)
      setSeconds(0)
      setPhase('recording')
      const recognized = startBrowserRecognition()
      if (!recognized) setError('녹음 중이에요. 종료 후 음성을 글로 변환합니다.')
      timerRef.current = window.setInterval(() => setSeconds((current) => {
        if (current + 1 >= maxRecordingSeconds) {
          stopTimer()
          if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
          recognitionRef.current?.stop()
          return maxRecordingSeconds
        }
        return current + 1
      }), 1000)
    } catch (cause) {
      stopStream()
      const message = cause instanceof Error && cause.name === 'NotAllowedError'
        ? '마이크 권한이 필요해요. 브라우저 주소창에서 마이크 사용을 허용해 주세요.'
        : '마이크를 시작할 수 없어요. 연결 상태와 브라우저 권한을 확인해 주세요.'
      setError(message)
    }
  }

  const stopRecording = () => {
    stopTimer()
    recognitionRef.current?.stop()
    if (recorderRef.current?.state === 'recording') {
      recorderRef.current.stop()
    } else {
      stopStream()
      setPhase('idle')
    }
  }

  useEffect(() => () => {
    stopTimer()
    requestRef.current?.abort()
    recognitionRef.current?.stop()
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
    stopStream()
  }, [stopStream, stopTimer])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const entry = parseSpokenEntry(script)
    if (!entry) {
      setError('금액을 찾지 못했어요. “점심에 9천 원 썼어”처럼 금액을 포함해 주세요.')
      return
    }
    setEntries((current) => [entry, ...current])
    setScript('')
    setError('')
    setSaved(true)
    setIsEditing(false)
  }

  const removeScript = () => { setScript(''); setError(''); setSaved(false) }
  const expenseTotal = entries.filter((entry) => entry.type === 'expense').reduce((sum, entry) => sum + Number(entry.amount.replace(/[^\d]/g, '')), 0)
  const incomeTotal = entries.filter((entry) => entry.type === 'income').reduce((sum, entry) => sum + Number(entry.amount.replace(/[^\d]/g, '')), 0)

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#home" aria-label="모아 홈"><span className="brand-mark"><span /><span /><span /></span><span>모아<span className="brand-period">.</span></span></a>
        <nav className="desktop-nav" aria-label="주요 메뉴"><a className="nav-link active" href="#home">오늘 기록</a><a className="nav-link" href="#records">내역</a><a className="nav-link" href="#insight">소비 리포트 <span className="nav-new">NEW</span></a></nav>
        <div className="profile"><span className="profile-avatar">다</span><span>다은님</span><ChevronRight size={15} className="profile-chevron" /></div>
      </header>
      <main className="main-content" id="home">
        <div className="page-heading"><a className="back-link" href="#home"><ArrowLeft size={17} /> 오늘로</a><div className="date-label"><span className="live-dot" />{today}</div></div>
        <section className="intro"><div className="eyebrow"><Sparkles size={13} /> MOA VOICE JOURNAL</div><h1>오늘의 소비,<br className="mobile-break" /> 편하게 말해볼까요?</h1><p>말 한마디면 기록 끝. 금액은 모아가 알아서 정리해요.</p></section>
        <section className="record-card" aria-label="음성으로 소비 기록하기">
          <div className="record-topline"><span className="step-label"><span className="step-number">01</span> 음성 기록</span><button className="help-button" aria-label="음성 기록 도움말" onClick={() => setError('마이크 버튼을 누르고 지출이나 수입을 말한 뒤, 완료 버튼으로 녹음을 끝내세요. 변환된 내용을 확인하고 기록을 저장할 수 있어요.')}><CircleHelp size={18} /></button></div>
          <div className={`voice-stage ${phase === 'recording' ? 'is-recording' : ''}`}>
            <div className="visualizer" aria-hidden="true">{Array.from({ length: 45 }, (_, i) => <span key={i} style={{ '--bar-height': `${9 + Math.abs(Math.sin(i * 1.17) * Math.cos(i * 0.21)) * 37}px`, '--bar-delay': `${(i % 9) * 0.07}s` } as React.CSSProperties} />)}</div>
            <div className="mic-control-wrap"><span className="mic-halo halo-one" /><span className="mic-halo halo-two" /><button className={`mic-button ${phase === 'recording' ? 'recording' : ''}`} onClick={phase === 'recording' ? stopRecording : phase === 'idle' ? () => void startRecording() : undefined} disabled={phase === 'transcribing'} aria-label={phase === 'recording' ? '녹음 완료' : '녹음 시작'}>{phase === 'recording' ? <MicOff size={24} strokeWidth={1.8} /> : <Mic size={24} strokeWidth={1.8} />}</button></div>
            <div className="record-status" aria-live="polite"><strong>{phase === 'recording' ? '듣고 있어요' : phase === 'transcribing' ? '글로 옮기는 중이에요' : '마이크를 눌러 시작하세요'}</strong><span>{phase === 'recording' ? formatTime(seconds) : phase === 'transcribing' ? '잠시만 기다려 주세요' : '편하게 말하면 자동으로 기록돼요'}</span></div>
            {phase === 'recording' && <button className="stop-button" onClick={stopRecording}>완료 <span>⏎</span></button>}
            {phase === 'transcribing' && <span className="transcribing-dot" aria-label="변환 중" />}
          </div>
          <div className="suggestion-row"><span className="suggestion-label">이렇게 말해보세요</span><div className="suggestion-pills">{examples.map((example) => <button key={example} onClick={() => { setScript(example); setError(''); setSaved(false) }}>{example}</button>)}</div></div>
          <div className="privacy-note"><span className="privacy-lock">✳</span> 녹음은 텍스트 변환에만 사용되며 저장되지 않아요.</div>
        </section>
        <section className={`transcript-card ${script ? 'has-script' : ''}`} aria-label="변환된 스크립트">
          <div className="transcript-heading"><span className="step-label"><span className="step-number">02</span> 이렇게 기록할게요</span>{script && <span className="auto-tag"><span /> 자동 변환 완료</span>}</div>
          <form onSubmit={handleSubmit}><div className="transcript-input-wrap"><textarea value={script} onChange={(event) => { setScript(event.target.value); setSaved(false) }} placeholder="음성 기록이 여기에 표시돼요. 직접 입력하거나 수정할 수도 있어요." rows={script ? 2 : 3} aria-label="변환된 음성 스크립트" disabled={phase === 'recording' || phase === 'transcribing'} /><div className="input-actions">{script && <><button type="button" className={`text-edit ${isEditing ? 'editing' : ''}`} onClick={() => setIsEditing(!isEditing)}><Pencil size={14} />{isEditing ? '수정 중' : '내용 수정'}</button><button type="button" className="clear-script" aria-label="스크립트 지우기" onClick={removeScript}><X size={16} /></button></>}</div></div><div className="transcript-footer"><span className="helper-text">{script ? <><Check size={14} /> 금액과 내용을 확인해 주세요.</> : '인식이 잘못됐다면 내용을 직접 고칠 수 있어요.'}<span className="mic-inline"><Mic size={11} /></span></span><button className="save-button" type="submit" disabled={!script.trim() || phase !== 'idle'}>{saved ? <><Check size={16} /> 기록했어요</> : <>이대로 기록하기 <ChevronRight size={16} /></>}</button></div></form>
          {error && <p className="error-message" role="alert"><span>!</span> {error}</p>}{saved && <p className="success-message" role="status"><Check size={14} /> 오늘의 내역에 추가했어요. 챗봇에서 바로 확인할 수 있어요.</p>}
        </section>
        <section className="today-section" id="records">
          <div className="section-heading"><div><span className="eyebrow muted-eyebrow">TODAY AT A GLANCE</span><h2>오늘의 기록 <span className="count-badge">{entries.length}</span></h2></div><a className="all-link" href="#records">전체 내역 <ChevronRight size={15} /></a></div>
          <div className="summary-strip"><div><span>오늘 쓴 돈</span><strong>₩ {expenseTotal.toLocaleString('ko-KR')}</strong></div><span className="summary-divider" /><div><span>오늘 들어온 돈</span><strong className="income-value">+ ₩ {incomeTotal.toLocaleString('ko-KR')}</strong></div><div className="summary-spark" aria-hidden="true"><span /><span /><span /><span /><span /><span /><span /></div></div>
          <div className="entry-list">{entries.map((entry) => <article className="entry-row" key={entry.id}><span className={`entry-icon ${entry.color}`}>{entry.icon}</span><div className="entry-info"><strong>{entry.title}</strong><span>{entry.category} · 방금 전</span></div><span className={`entry-amount ${entry.type === 'income' ? 'positive' : ''}`}>{entry.amount}</span><ChevronRight size={16} className="entry-chevron" /></article>)}</div>
          <div className="insight-nudge" id="insight"><span className="nudge-icon"><Sparkles size={16} /></span><span><strong>이번 달 소비가 궁금하다면?</strong><small>모아가 소비 패턴을 분석해 드려요.</small></span><button aria-label="소비 리포트 보기"><ArrowUpRight size={17} /></button></div>
        </section>
        <footer className="page-footer"><span>오늘도 모아와 차곡차곡 <span className="footer-heart">♥</span></span><span><Clock3 size={12} /> 안전하게 관리되는 나의 기록</span></footer>
      </main>
      <div className="mobile-bottom-nav"><a className="mobile-tab selected" href="#home"><Mic size={18} /><span>기록</span></a><a className="mobile-tab" href="#records"><ArrowDownLeft size={18} /><span>내역</span></a><a className="mobile-tab" href="#insight"><Sparkles size={18} /><span>리포트</span></a><button className="mobile-tab"><span className="profile-avatar small">다</span><span>마이</span></button></div>
      <div className="decor decor-one" aria-hidden="true" /><div className="decor decor-two" aria-hidden="true" />
    </div>
  )
}
