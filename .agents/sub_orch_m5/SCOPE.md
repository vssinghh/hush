# Scope: Milestone 5 (Chat UI + Voice)

## Architecture
Milestone 5 implements the voice integration and chat user interface enhancements.
The architecture spans these layers:
- **UI**: `ChatScreen` and `ChatViewModel` integrated with `SpeechRecognizerWrapper`. Waveform canvas visualization drawing audio amplitudes.
- **Data**: `SpeechRecognizerWrapperImpl` wrapping the system-level Android `SpeechRecognizer` and dispatching flow updates on the main thread/coroutines.
- **Domain**: `SpeechState` defining the voice recording lifecycle.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | SpeechRecognizer Integration | Complete `SpeechRecognizerWrapperImpl` using Android system `SpeechRecognizer`, handling permissions and `RecognitionListener` callbacks | none | DONE |
| 2 | Waveform Visualization UI | Draw dynamic canvas-based or animated waveform line visualizations mapping amplitude levels in `ChatScreen` | M1 | DONE |
| 3 | Voice permission & lifecycle | Integrate microphone permission checking, requesting, lifecycle-aware release of SpeechRecognizer instances, and dynamic error messages | M2 | DONE |
| 4 | Test Coverage | Implement tests verifying voice state transitions, waveform UI rendering, error handling, and Hilt test bindings | M3 | DONE |
| 5 | Verification & Audit | Verify compiling, pass E2E tests, and secure a CLEAN Forensic Auditor verdict | M4 | DONE |

## Interface Contracts

### SpeechRecognizer State Updates
The `SpeechRecognizerWrapper` flow must dispatch these state updates accurately:
- `SpeechState.Idle` — Initial or complete.
- `SpeechState.Listening` — Active voice input session.
- `SpeechState.WaveformUpdate(amplitude)` — Real-time audio amplitude updates (obtained via `onRmsChanged`).
- `SpeechState.PartialResult(text)` — Intermediate transcription updates.
- `SpeechState.FinalResult(text)` — Final complete transcription text.
- `SpeechState.Error(code)` — Speech error codes.

### UI Requirements
- The chat microphone button must toggle speech listening sessions.
- During active listening, a waveform visualization panel must render dynamic feedback (e.g. animated bars or custom drawing based on the `amplitude` from `WaveformUpdate`).
- Silent recordings should close gracefully without querying AI.
- Transcription errors must output a visible `chat_error_message` card or bubble.
