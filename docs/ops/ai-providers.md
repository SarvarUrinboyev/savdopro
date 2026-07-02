# AI yordamchini prod'da yoqish (provayder kalitlari)

AI chat allaqachon kodda to'liq ishlaydi (AiChatService: 24+ read-only tool,
agentik tool-calling, ACTION tugmalari). Prod'da faqat **kalit yetishmaydi** —
kalitsiz u degraded rejimda (KPI-snapshot) javob beradi.

## Zanjir qanday ishlaydi

`ai.providers` tartibi (default): `gemini → nvidia-deepseek → nvidia-kimi →
openrouter`. Har so'rovda birinchi sozlangan provayder chaqiriladi; timeout /
rate-limit / 5xx bo'lsa keyingisiga o'tadi. Kalitsiz slot shunchaki tashlab
yuboriladi. Ya'ni **bitta bepul Gemini kaliti bilan ham tizim to'liq AI'li
bo'ladi**; qolganlari zaxira.

| Slot | Model (default) | Kalit env | Qayerdan | Narx |
|---|---|---|---|---|
| gemini | gemini-2.0-flash-exp | `GEMINI_API_KEY` | aistudio.google.com/apikey | bepul tier |
| nvidia-deepseek | deepseek-ai/deepseek-v4-flash | `NVIDIA_DEEPSEEK_KEY` | build.nvidia.com | bepul NIM |
| nvidia-kimi | moonshotai/kimi-k2.6 | `NVIDIA_KIMI_KEY` | build.nvidia.com (o'sha kalit) | bepul NIM |
| openrouter | anthropic/claude-3.5-haiku | `OPENROUTER_API_KEY` | openrouter.ai/keys | pullik (oxirgi zaxira) |

Model/tartibni env orqali o'zgartirish: `ai.providers`, `ai.gemini.model`,
`ai.openrouter.model` va h.k. (application.properties'dagi placeholderlarga
mos env nomlari bilan).

## Yoqish (droplet, systemd)

```bash
# /etc/savdopro/backend.env ga qo'shing (kamida bittasi):
GEMINI_API_KEY=AIza...
# NVIDIA_DEEPSEEK_KEY=nvapi-...
# NVIDIA_KIMI_KEY=nvapi-...
# OPENROUTER_API_KEY=sk-or-...

systemctl restart savdopro-backend
```

## Tekshirish

1. Startup logida: `journalctl -u savdopro-backend | grep "AI chain"` →
   `AI chain (1 ready of 4 configured): [gemini, nvidia-deepseek[off], ...]`
   — "N ready" 0 dan katta bo'lsin.
2. UI'da: istalgan sahifadagi 🤖 tugma → "Bugun foyda qancha?" deb so'rang.
   Javob pastida qaysi provayder javob bergani ko'rinadi (`provider` maydoni).
3. Degraded tekshiruv: barcha kalitlarni olib tashlasangiz ham chat ishlashi
   (KPI snapshot qaytarishi) kerak — bu xato emas, dizayn.

## Xavfsizlik eslatmalari

- Kalitlar faqat env faylda (0600) — kod/git'da emas.
- AI tool'lari READ-ONLY va tenant-scoped: model hech qachon o'zi yozmaydi,
  faqat ACTION taklif qiladi; bajarish foydalanuvchi tasdig'i + odatiy
  permission gate orqali.
- Gemini'ga faqat so'rov matni + tool natijalari (do'kon KPI raqamlari)
  ketadi; mijoz shaxsiy ma'lumotlarini so'ramaslik uchun system-prompt
  cheklangan. Maxfiylik talab qilinsa openrouter/self-host slotini birinchi
  qiling.
