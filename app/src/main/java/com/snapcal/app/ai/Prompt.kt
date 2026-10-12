package com.snapcal.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object Prompt {
    /** Bump whenever SYSTEM or the schema changes, so stored estimates can be compared across prompt versions. */
    const val VERSION = 2

    /**
     * Design notes (see docs/SCIENCE.md §3):
     *  - portion size, not identification, is the main error source, and models under-estimate large portions
     *    (O'Hara 2025; Fridolfsson 2025) → ask for grams per item with explicit scale cues;
     *  - Chinese dishes average ~8 g cooking oil per 100 g ingredients (Pu 2019); adding typical-recipe fat cut
     *    error ~30 kcal/dish (Coburn 2026) → itemise hidden oil/sugar/sauce;
     *  - user hints give the largest measured gain (MAPE 30.5 % → 24.4 %, Rodríguez-Jiménez 2025) → the model
     *    returns one optional clarifying question the user can answer in a tap.
     */
    val SYSTEM = """
你是一名注册营养师，根据食物照片估算这一顿的能量和宏量营养素。

估算步骤：
1. 识别每一种食物。照片可能有多张：可能是不同的菜，也可能是同一盘的不同角度——同一份食物不要重复计算。
2. 估分量（这是最大的误差来源）：用盘子（常见直径 20–26 cm）、碗（常见 300–500 ml）、筷子（约 23 cm）、勺子、手、包装等参照物，先估体积再换算成克数，写进 portion，例如"米饭约 180 g（一满碗）"。
   研究表明 AI 普遍低估大份量：分量看起来大时不要往小了估。
3. 计算能量：按《中国食物成分表》/ USDA 的常见数值，用 克数 × 能量密度 计算每一项。
4. 隐形能量要单独列项：中式炒菜、红烧、干锅、油泼等按常见做法估计烹调用油（家常炒菜每 100 g 食材约 6–10 g 油），以及糖、勾芡、酱汁、沙拉酱、汤里的油。列为单独的 item，名称如"烹调用油（估）"。
5. 饮料、主食、酱料都单独列出。
6. 用户的补充说明优先（例如"只吃了一半""少油""米饭半碗"）。

不确定时给出最可能的估计，不要拒绝。
confidence：high = 分量和做法都清楚；medium = 分量或做法有一项不确定；low = 两者都不确定或食物被遮挡。
question：如果有一个简短问题能明显提高准确度（例如"米饭吃完了吗？""是油炸的还是烤的？"），给出一个问题；否则给空字符串。
notes：一句话说明最主要的不确定点。
名称用简体中文。只输出符合 schema 的 JSON。
""".trimIndent()

    fun user(note: String, imageCount: Int): String = buildString {
        append("请估算这")
        append(if (imageCount > 1) " $imageCount 张照片里" else "张照片里")
        append("这一顿的热量。")
        if (note.isNotBlank()) append("\n用户补充说明：").append(note.trim())
    }

    /** JSON Schema shared by every provider (strict-mode compatible: all fields required, no extras). */
    val SCHEMA_JSON = """
{
  "type": "object",
  "properties": {
    "title": {"type": "string", "description": "这一顿的简短名称，如 午餐：牛肉面"},
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "portion": {"type": "string", "description": "估计分量，含克数"},
          "grams": {"type": "number", "description": "估计克数"},
          "kcal": {"type": "integer"},
          "proteinG": {"type": "number"},
          "carbsG": {"type": "number"},
          "fatG": {"type": "number"}
        },
        "required": ["name", "portion", "grams", "kcal", "proteinG", "carbsG", "fatG"],
        "additionalProperties": false
      }
    },
    "totalKcal": {"type": "integer"},
    "confidence": {"type": "string", "enum": ["high", "medium", "low"]},
    "notes": {"type": "string"},
    "question": {"type": "string", "description": "可选的一个追问；不需要时为空字符串"}
  },
  "required": ["title", "items", "totalKcal", "confidence", "notes", "question"],
  "additionalProperties": false
}
""".trimIndent()

    val schema: JsonObject by lazy { Json.parseToJsonElement(SCHEMA_JSON) as JsonObject }
}
