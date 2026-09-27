# Chat Translation

Minecraft chat translation mod for Fabric.

## Features

- Google Cloud Translation API
- Bing Translator
- Bing Free API
- MyMemory
- LibreTranslate
- Caiyun
- Unified AI mode

## AI Mode

Select `ai` in the translation service dropdown, then choose an AI format:

- OpenAI Compatible
- Gemini
- Anthropic

Then fill in:

- API URL
- API Key
- Model ID

## Google Cloud

Use `google_cloud` and fill:

- Google Cloud API URL
- Google Cloud API Key

Default URL:

`https://translation.googleapis.com/language/translate/v2`

## Bing Free

Use `bing_free` for the free Bing endpoint.

## Notes

- `show_original` controls whether the original message is shown in the translated output.
- `single_line_display` controls compact output mode.
- If a provider returns an error string, the mod falls back to the original message.

