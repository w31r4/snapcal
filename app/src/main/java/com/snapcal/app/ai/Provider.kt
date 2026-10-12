package com.snapcal.app.ai

enum class Provider(
    val label: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    /** Shown as one-tap suggestions in Settings; any model id can be typed in. */
    val suggestedModels: List<String>,
    val keyHint: String,
) {
    ANTHROPIC(
        label = "Anthropic (Claude)",
        defaultModel = "claude-opus-5-5",
        defaultBaseUrl = "https://api.anthropic.com",
        suggestedModels = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1"),
        keyHint = "sk-ant-…  (console.anthropic.com)",
    ),
    OPENAI(
        label = "OpenAI (GPT)",
        defaultModel = "gpt-6.1-sol",
        defaultBaseUrl = "https://api.openai.com/v1",
        suggestedModels = listOf("gpt-6.1-sol", "gpt-6-luna", "gpt-5.6-luna", "gpt-5.6-sol"),
        keyHint = "sk-…  (platform.openai.com)",
    ),
    GEMINI(
        label = "Google Gemini",
        defaultModel = "gemini-3.8-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        suggestedModels = listOf("gemini-3.8-flash", "gemini-3.1-pro-preview", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
        keyHint = "AIza…  (aistudio.google.com)",
    ),
    MISTRAL(
        label = "Mistral",
        defaultModel = "mistral-medium-latest",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        suggestedModels = listOf("mistral-medium-latest", "mistral-large-latest", "mistral-small-latest"),
        keyHint = "console.mistral.ai → API Keys",
    ),
    DEEPSEEK(
        label = "DeepSeek",
        defaultModel = "deepseek-flash",
        defaultBaseUrl = "https://api.deepseek.com",
        suggestedModels = listOf("deepseek-flash"),
        keyHint = "sk-…  (platform.deepseek.com)",
    ),
}
