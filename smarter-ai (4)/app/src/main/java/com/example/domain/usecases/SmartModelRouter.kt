package com.example.domain.usecases

import com.example.domain.models.AiModelCatalog
import com.example.domain.models.AiModelDefinition
import com.example.domain.models.AvailabilityStatus
import com.example.domain.models.ChatMessage
import com.example.domain.models.MessageAttachment

/**
 * Classification of user intent used by [SmartModelRouter] to select the optimal
 * internal Apinex model while keeping the public identity unified as "Smarter AI 1.0".
 */
enum class SmartTaskCategory(val displayName: String) {
    VISION_MULTIMODAL("Vision & Image Understanding"),
    CODING_ENGINEERING("Software Engineering & Debugging"),
    COMPLEX_REASONING_MATH("Deep Reasoning & Mathematics"),
    LONG_CONTEXT_DOCUMENT("Long Document & Context Analysis"),
    CREATIVE_PROMPT_WRITING("Creative Writing & Prompt Engineering"),
    FAST_GENERAL_CHAT("Fast Everyday Intelligence")
}

data class SmartRoutingDecision(
    val taskCategory: SmartTaskCategory,
    val primaryModelId: String,
    val fallbackModelIds: List<String>,
    val publicDisplayName: String = SmartModelRouter.PUBLIC_MODEL_IDENTITY,
    val requiresCopyableBlockFormatting: Boolean = false
)

/**
 * Centralized Intelligent Model Router for Smarter AI 1.0.
 *
 * Automatically routes every request to the best internal Apinex model based on:
 * - Image/vision attachments
 * - Coding, SQL, debugging, and terminal tasks
 * - Mathematical, logical, and analytical complexity
 * - Long document / large conversation context
 * - Creative writing, prompt generation, and copywriting
 * - Fast everyday conversational queries
 *
 * If the primary model is rate-limited, times out, or is temporarily unavailable,
 * [fallbackModelIds] provides an ordered list of capable backup models for seamless failover.
 */
object SmartModelRouter {

    const val PUBLIC_MODEL_IDENTITY = "Smarter AI 1.0"
    const val PUBLIC_ASSISTANT_NAME = "Smarter AI"

    // Exact Identity & Developer Privacy Responses
    const val IDENTITY_NAME_RESPONSE = "I am Smarter AI."
    const val IDENTITY_MODEL_RESPONSE = "I am Smarter AI 1.0, an AI assistant powered by Smarter AI's technology."
    const val IDENTITY_DEVELOPER_RESPONSE = "I was developed by the Smarter AI technical team."

    private val CODING_KEYWORDS = setOf(
        "code", "coding", "program", "programming", "debug", "bug", "fix", "error", "exception",
        "stacktrace", "kotlin", "java", "python", "javascript", "typescript", "react", "compose",
        "jetpack", "android", "swift", "rust", "golang", "c++", "csharp", "sql", "postgresql",
        "mysql", "sqlite", "query", "database", "schema", "api", "rest", "graphql", "json",
        "yaml", "xml", "html", "css", "tailwind", "dockerfile", "kubernetes", "bash", "shell",
        "terminal", "command", "git", "regex", "function", "class", "interface", "coroutine",
        "flow", "viewmodel", "refactor", "compile", "gradle", "algorithm", "data structure",
        "leetcode", "binary search", "linked list", "dynamic programming"
    )

    private val REASONING_MATH_KEYWORDS = setOf(
        "prove", "proof", "theorem", "calculus", "integral", "derivative", "matrix", "eigenvalue",
        "probability", "statistics", "equation", "solve", "step-by-step", "step by step",
        "derive", "logic puzzle", "riddle", "trade-offs", "tradeoffs", "architecture analysis",
        "compare and contrast", "in-depth analysis", "time complexity", "space complexity",
        "asymptotic", "big-o", "differential", "trigonometry", "physics", "quantum",
        "zero-knowledge", "cryptography", "distributed systems", "consensus", "induction"
    )

    private val CREATIVE_PROMPT_KEYWORDS = setOf(
        "write a prompt", "image generation prompt", "video generation prompt", "midjourney prompt",
        "flux prompt", "sana prompt", "stable diffusion prompt", "google ai studio prompt",
        "system prompt", "prompt template", "copy-paste format", "copy format", "ready-to-use prompt",
        "ready to use prompt", "essay", "story", "poem", "video script", "movie script", "blog post", "article",
        "social media caption", "instagram caption", "linkedin post", "tweet", "email template",
        "cover letter", "resume", "announcement memo", "marketing copy", "tagline", "brainstorm"
    )

    private val LONG_CONTEXT_KEYWORDS = setOf(
        "summarize this document", "analyze this transcript", "read the following",
        "full document", "comprehensive summary", "extract all key points", "entire text"
    )

    private val COPY_FORMAT_INTENT_PATTERNS = listOf(
        Regex("""\b(copy[- ]paste\s+format|in\s+copy\s+format|ready[- ]to[- ]use\s+prompt|copyable\s+format|for\s+copying)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(image\s+generation\s+prompt|video\s+generation\s+prompt|ai\s+studio\s+prompt|system\s+prompt\s+for|prompt\s+for\s+generating)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(write\s+a\s+prompt|give\s+me\s+a\s+prompt|create\s+a\s+prompt|generate\s+a\s+prompt)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(sql\s+query|json\s+config|terminal\s+command|bash\s+script|professional\s+template|email\s+template)\b""", RegexOption.IGNORE_CASE)
    )

    private val NAME_QUERY_REGEX = Regex(
        """^\s*(?:what\s+is\s+your\s+name|what's\s+your\s+name|whats\s+your\s+name|who\s+are\s+you|tell\s+me\s+your\s+name|tumhara\s+naam\s+kya\s+hai|aapka\s+naam\s+kya\s+hai)\s*[?.!]*\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val MODEL_QUERY_REGEX = Regex(
        """^\s*(?:which\s+(?:ai\s+)?model\s+are\s+you|what\s+(?:ai\s+)?model\s+are\s+you|which\s+model\s+is\s+this|what\s+llm\s+are\s+you|are\s+you\s+gpt|are\s+you\s+gemini|are\s+you\s+qwen|are\s+you\s+deepseek|are\s+you\s+claude|kaunsa\s+model\s+ho)\s*[?.!]*\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val DEVELOPER_QUERY_REGEX = Regex(
        """^\s*(?:who\s+developed\s+you|who\s+is\s+your\s+developer|who\s+created\s+you|who\s+made\s+you|who\s+built\s+you|who\s+owns\s+you|tell\s+me\s+(?:the\s+|your\s+)?developer'?s?\s+real\s+name|what\s+is\s+(?:the\s+|your\s+)?developer'?s?\s+name|who\s+is\s+behind\s+smarter\s+ai|tumhe\s+kisne\s+banaya(?:\s+hai)?|aapko\s+kisne\s+banaya(?:\s+hai)?)\s*[?.!]*\s*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Checks if the user's message is a direct identity or developer-privacy question
     * and returns the canonical Smarter AI 1.0 response.
     */
    fun resolveDeterministicIdentityReply(userPrompt: String): String? {
        val cleaned = userPrompt.trim()
        if (cleaned.isEmpty()) return null
        return when {
            DEVELOPER_QUERY_REGEX.matches(cleaned) -> IDENTITY_DEVELOPER_RESPONSE
            MODEL_QUERY_REGEX.matches(cleaned) -> IDENTITY_MODEL_RESPONSE
            NAME_QUERY_REGEX.matches(cleaned) -> IDENTITY_NAME_RESPONSE
            else -> null
        }
    }

    /**
     * Detects whether the user is asking for a reusable prompt, template, script, or
     * explicit "copy-paste format" artifact that should be rendered inside an editable Smart Copy Block.
     */
    fun isCopyableFormatRequested(userPrompt: String): Boolean {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) return false
        return COPY_FORMAT_INTENT_PATTERNS.any { it.containsMatchIn(trimmed) }
    }

    /**
     * Selects the optimal internal Apinex model and ordered fallback chain for the given turn.
     */
    fun routeRequest(
        promptText: String,
        hasImageAttachments: Boolean = false,
        conversationHistoryCount: Int = 0,
        catalog: List<AiModelDefinition> = AiModelCatalog.APINEX_CHAT_MODELS,
        preferredModelId: String? = null,
        excludeModelIds: Set<String> = emptySet()
    ): SmartRoutingDecision {
        val lower = promptText.trim().lowercase()
        val copyFormatRequested = isCopyableFormatRequested(promptText)

        val category = when {
            hasImageAttachments -> SmartTaskCategory.VISION_MULTIMODAL
            copyFormatRequested || CREATIVE_PROMPT_KEYWORDS.any { lower.contains(it) } ->
                SmartTaskCategory.CREATIVE_PROMPT_WRITING
            containsCodingIntent(lower, promptText) -> SmartTaskCategory.CODING_ENGINEERING
            containsReasoningOrMathIntent(lower) -> SmartTaskCategory.COMPLEX_REASONING_MATH
            promptText.length > 2_500 || conversationHistoryCount > 12 || LONG_CONTEXT_KEYWORDS.any { lower.contains(it) } ->
                SmartTaskCategory.LONG_CONTEXT_DOCUMENT
            else -> SmartTaskCategory.FAST_GENERAL_CHAT
        }

        val candidateOrder = when (category) {
            SmartTaskCategory.VISION_MULTIMODAL -> listOf(
                "free/gemini-3.8-flash",
                "free/qwen-3.8-max",
                "free/gpt-6-luna",
                "free/kimi-k3",
                "free/muse-spark-1.3",
                "free/glm-5.3-flash"
            )
            SmartTaskCategory.CODING_ENGINEERING -> listOf(
                "free/qwen-3.8-max",
                "free/deepseek-v4.1-flash",
                "free/mimo-v2.6-flash",
                "free/kimi-k3",
                "free/glm-5.3-flash",
                "free/gpt-6-luna"
            )
            SmartTaskCategory.COMPLEX_REASONING_MATH -> listOf(
                "free/deepseek-v4.1-flash",
                "free/kimi-k3",
                "free/qwen-3.8-max",
                "free/glm-5.3",
                "free/glm-5.3-flash",
                "free/gpt-6-luna"
            )
            SmartTaskCategory.LONG_CONTEXT_DOCUMENT -> listOf(
                "free/kimi-k3",
                "free/minimax-m3.1-flash",
                "free/qwen-3.8-max",
                "free/gemini-3.8-flash",
                "free/glm-5.3-flash"
            )
            SmartTaskCategory.CREATIVE_PROMPT_WRITING -> listOf(
                "free/muse-spark-1.3",
                "free/gpt-6-luna",
                "free/qwen-3.8-max",
                "free/glm-5.3-flash",
                "free/minimax-m3.1-flash"
            )
            SmartTaskCategory.FAST_GENERAL_CHAT -> listOf(
                "free/glm-5.3-flash",
                "free/minimax-m3.1-flash",
                "free/hy4",
                "free/gpt-6-luna",
                "free/glm-5.3",
                "free/jev-1.13",
                "free/qwen-3.8-max"
            )
        }

        val validCatalogIds = catalog
            .filter { it.availabilityStatus != AvailabilityStatus.UNAVAILABLE }
            .map { it.providerModelId }
            .toSet()
            .ifEmpty { AiModelCatalog.APINEX_CHAT_MODELS.map { it.providerModelId }.toSet() }

        val orderedWithPreferred = if (!preferredModelId.isNullOrBlank() && preferredModelId in validCatalogIds) {
            listOf(preferredModelId) + candidateOrder.filter { it != preferredModelId }
        } else {
            candidateOrder
        }

        val filteredCandidates = orderedWithPreferred
            .filter { it in validCatalogIds && it !in excludeModelIds }
            .ifEmpty {
                AiModelCatalog.APINEX_CHAT_MODELS
                    .map { it.providerModelId }
                    .filter { it !in excludeModelIds }
            }
            .ifEmpty {
                listOf(AiModelCatalog.DEFAULT_MODEL_ID)
            }

        val primary = filteredCandidates.first()
        val fallbacks = filteredCandidates.drop(1)

        return SmartRoutingDecision(
            taskCategory = category,
            primaryModelId = primary,
            fallbackModelIds = fallbacks,
            publicDisplayName = PUBLIC_MODEL_IDENTITY,
            requiresCopyableBlockFormatting = copyFormatRequested
        )
    }

    private fun containsCodingIntent(lower: String, raw: String): Boolean {
        if (raw.contains("```") || raw.contains("fun ") || raw.contains("def ") || raw.contains("SELECT ") || raw.contains("import ")) {
            return true
        }
        return CODING_KEYWORDS.any { keyword ->
            if (keyword.length <= 4) {
                Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            } else {
                lower.contains(keyword)
            }
        }
    }

    private fun containsReasoningOrMathIntent(lower: String): Boolean {
        if (Regex("""\b(\d+\s*[\+\-\*\/\^=]\s*\d+|solve\s+for\s+[a-z]|integral|derivative|matrix)\b""").containsMatchIn(lower)) {
            return true
        }
        return REASONING_MATH_KEYWORDS.any { lower.contains(it) }
    }
}
