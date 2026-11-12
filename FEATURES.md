# Feature status

| Feature | Status |
|---|---|
| Project structure, Compose + Material 3 theme | Implemented |
| Home screen, animated NOVA core (avatar placeholder) | Implemented |
| Settings (language, speed, pitch, voice choice, encrypted keys) | Implemented |
| Permissions dashboard (mic, notifications) | Implemented |
| Speech-to-text (Hinglish/Hindi/English, needs internet) | Implemented |
| Human-sounding voice (Gemini text-to-speech, 10 voices to choose, emotion-based style, speed control) | Implemented (untested), needs Gemini key and internet |
| Phone (Android) voice as automatic backup | Implemented (voice list removed, uses phone default) |
| Interrupt NOVA (tap mic while she speaks or thinks) | Implemented |
| Gemini AI brain | Implemented (needs your API key) |
| OpenRouter, NVIDIA and custom OpenAI-compatible brains | Implemented (need your keys and model names) |
| Automatic provider fallback, ending in offline brain | Implemented |
| Offline brain: time, date, simple calculator, greeting | Implemented (very basic) |
| NOVA personality + emotion tag per reply | Implemented (tag used by avatar in Phase 4) |
| Conversation memory | Partially: last 6 turns kept in RAM only |
| Smart router: coding questions vs general chat pick different provider order | Implemented (keyword based) |
| Offline local LLM (llama.cpp) | Planned, heavy for a 32-bit 4 GB phone |
| Backend proxy for API keys | Planned |
| Conversation history (Room database) | Planned |
| Wake word, continuous conversation, emotional voice | Planned |
| Animated anime avatar (drawn in code): blink, breathing, 8 expressions from AI emotion tag | Implemented (stylised, first draft) |
| Lip-sync | Partially: mouth moves while NOVA speaks, not synced word by word |
| Avatar from your own artwork / 3D model | Planned |
| Master switch "Phone control" (off by default) | Implemented |
| Open apps by name (Hinglish/Hindi/English) | Implemented |
| YouTube: open, search (opens real results in the YouTube app) | Implemented |
| YouTube: play Nth search result | Partially: uses Accessibility, may break with YouTube updates |
| Play / pause / next / previous, volume | Implemented (media keys) |
| Google search, open websites | Implemented |
| Alarm and timer (Android clock app) | Implemented |
| Play Store search (user installs manually) | Implemented |
| Accessibility: Back, Home, Recents, scroll, tap by text, read screen | Implemented, untested; blocked on payment/bank/password/installer apps |
| Fast replies: streaming answers, NOVA speaks sentence by sentence, minimal Gemini thinking, shorter timeouts, quicker end-of-speech | Implemented (Phase 5a, untested) |
| Multi-step tasks with AI planning | Planned |
| Floating NOVA bubble to give commands while inside other apps | Planned (Phase 5b) |
| Scanner: photo of a product / text / anything -> Gemini describes it (camera or gallery) | Implemented (Phase 6, untested). Prices are estimates only; needs Gemini key; photo is sent to Google |
| Coding assistant screen: streaming long answers, code blocks with Copy | Implemented (Phase 6, untested) |
| Routines: saved command lists, run by saying "<name> routine chalao" | Implemented (Phase 6, untested); needs Phone control on |
| Routines on a timer / automatic triggers | Planned |
| Barcode scanning, live product prices | Planned (needs extra libraries / a price source) |
