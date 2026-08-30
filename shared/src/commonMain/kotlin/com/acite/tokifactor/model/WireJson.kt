package com.acite.tokifactor.model

internal object WireJson {
    fun encodeObject(vararg pairs: Pair<String, String>): String = buildString {
        append('{')
        pairs.forEachIndexed { i, (key, value) ->
            if (i > 0) append(',')
            appendJsonString(key)
            append(':')
            appendJsonString(value)
        }
        append('}')
    }

    fun extractString(json: String, key: String): String? {
        val needle = "\"$key\""
        val keyIdx = json.indexOf(needle)
        if (keyIdx < 0) return null
        val colon = json.indexOf(':', keyIdx + needle.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (val n = json[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    else -> out.append(n)
                }
                i += 2
            } else if (c == '"') {
                return out.toString()
            } else {
                out.append(c)
                i++
            }
        }
        return null
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }
}
