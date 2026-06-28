package io.github.yrashid.actualschema

internal class PgDumpNormalizer {
    fun normalize(dump: String): String {
        val withoutVolatileLines = dump
            .lineSequence()
            .filterNot { it.startsWith("-- Dumped from database version") }
            .filterNot { it.startsWith("-- Dumped by pg_dump version") }
            .filterNot { it.startsWith("\\restrict ") }
            .filterNot { it.startsWith("\\unrestrict ") }
            .joinToString("\n")
            .trimEnd() + "\n"

        return groupIndexLikeBlocksByTable(withoutVolatileLines)
    }

    private fun groupIndexLikeBlocksByTable(dump: String): String {
        val blocks = dump.split(pgDumpBlockStart).mapIndexed { index, text ->
            val type = pgDumpType.find(text)?.groupValues?.get(1)
            val relationName = when (type) {
                "TABLE", "FOREIGN TABLE", "MATERIALIZED VIEW" -> parseCreateRelationName(text)
                else -> null
            }
            val indexLikeRelationName = when (type) {
                "INDEX" -> parseCreateIndexRelationName(text)
                "CONSTRAINT" -> parseIndexBackedConstraintRelationName(text)
                else -> null
            }
            PgDumpBlock(
                text = text,
                type = type,
                relationName = relationName,
                indexLikeRelationName = indexLikeRelationName,
                index = index
            )
        }
        val indexLikeBlocks = blocks.filter { it.indexLikeRelationName != null }
        if (indexLikeBlocks.size < 2 || blocks.any { it.type == "INDEX" && it.indexLikeRelationName == null }) {
            return dump
        }

        val relationOrder = blocks
            .asSequence()
            .mapNotNull { it.relationName ?: it.indexLikeRelationName }
            .distinct()
            .withIndex()
            .associate { (index, relationName) -> relationName to index }
        val sortedIndexText = indexLikeBlocks
            .sortedWith(
                compareBy<PgDumpBlock> { relationOrder[it.indexLikeRelationName] ?: Int.MAX_VALUE }
                    .thenBy { it.indexLikeRelationName }
                    .thenBy { it.index }
            )
            .joinToString("") { it.text }
        var insertedIndexLikeBlocks = false

        return buildString(dump.length) {
            blocks.forEach { block ->
                if (block.indexLikeRelationName != null) {
                    if (!insertedIndexLikeBlocks) {
                        append(sortedIndexText)
                        insertedIndexLikeBlocks = true
                    }
                } else {
                    append(block.text)
                }
            }
        }
    }

    private fun parseCreateIndexRelationName(block: String): String? {
        val statement = block.substringFrom(createIndexStart) ?: return null
        val tokens = SqlTokens(statement)
        if (!tokens.consumeKeyword("CREATE")) return null
        tokens.consumeKeyword("UNIQUE")
        if (!tokens.consumeKeyword("INDEX")) return null
        tokens.consumeKeyword("CONCURRENTLY")
        if (tokens.consumeKeyword("IF") && (!tokens.consumeKeyword("NOT") || !tokens.consumeKeyword("EXISTS"))) {
            return null
        }
        tokens.readQualifiedIdentifier() ?: return null
        if (!tokens.consumeKeyword("ON")) return null
        tokens.consumeKeyword("ONLY")
        return tokens.readQualifiedIdentifier()
    }

    private fun parseIndexBackedConstraintRelationName(block: String): String? {
        val statement = block.substringFrom(alterTableStart) ?: return null
        val tokens = SqlTokens(statement)
        if (!tokens.consumeKeyword("ALTER") || !tokens.consumeKeyword("TABLE")) return null
        tokens.consumeKeyword("ONLY")
        val relationName = tokens.readQualifiedIdentifier() ?: return null
        if (!tokens.consumeKeyword("ADD") || !tokens.consumeKeyword("CONSTRAINT")) return null
        tokens.readQualifiedIdentifier() ?: return null
        return if (
            tokens.peekKeyword("PRIMARY") ||
            tokens.peekKeyword("UNIQUE") ||
            tokens.peekKeyword("EXCLUDE")
        ) {
            relationName
        } else {
            null
        }
    }

    private fun parseCreateRelationName(block: String): String? {
        val statement = block.substringFrom(createRelationStart) ?: return null
        val tokens = SqlTokens(statement)
        if (!tokens.consumeKeyword("CREATE")) return null
        tokens.consumeKeyword("UNLOGGED")
        if (tokens.consumeKeyword("GLOBAL") || tokens.consumeKeyword("LOCAL")) {
            tokens.consumeKeyword("TEMPORARY") || tokens.consumeKeyword("TEMP")
        } else {
            tokens.consumeKeyword("TEMPORARY") || tokens.consumeKeyword("TEMP")
        }

        return when {
            tokens.consumeKeyword("TABLE") -> tokens.readQualifiedIdentifier()
            tokens.consumeKeyword("FOREIGN") && tokens.consumeKeyword("TABLE") -> tokens.readQualifiedIdentifier()
            tokens.consumeKeyword("MATERIALIZED") && tokens.consumeKeyword("VIEW") -> tokens.readQualifiedIdentifier()
            else -> null
        }
    }

    private data class PgDumpBlock(
        val text: String,
        val type: String?,
        val relationName: String?,
        val indexLikeRelationName: String?,
        val index: Int
    )

    private class SqlTokens(sql: String) {
        private val tokens = tokenizeSql(sql)
        private var position = 0

        fun consumeKeyword(keyword: String): Boolean {
            if (position >= tokens.size || !tokens[position].equals(keyword, ignoreCase = true)) {
                return false
            }
            position += 1
            return true
        }

        fun peekKeyword(keyword: String): Boolean =
            position < tokens.size && tokens[position].equals(keyword, ignoreCase = true)

        fun readQualifiedIdentifier(): String? {
            val parts = mutableListOf<String>()
            if (position >= tokens.size || !tokens[position].isSqlIdentifier()) {
                return null
            }
            parts += tokens[position]
            position += 1
            while (position + 1 < tokens.size && tokens[position] == "." && tokens[position + 1].isSqlIdentifier()) {
                position += 1
                parts += tokens[position]
                position += 1
            }
            return parts.joinToString(".") { identifier ->
                if (identifier.startsWith("\"")) identifier else identifier.lowercase()
            }
        }
    }

    private companion object {
        val pgDumpBlockStart = Regex("""(?m)(?=^--\n-- Name: )""")
        val pgDumpType = Regex("""(?m)^-- Name: .*; Type: ([^;]+);""")
        val createIndexStart = Regex("""\bCREATE\s+(?:UNIQUE\s+)?INDEX\b""", RegexOption.IGNORE_CASE)
        val createRelationStart = Regex(
            """\bCREATE\s+(?:UNLOGGED\s+)?(?:(?:GLOBAL|LOCAL)\s+)?(?:TEMPORARY\s+|TEMP\s+)?(?:TABLE|FOREIGN\s+TABLE|MATERIALIZED\s+VIEW)\b""",
            RegexOption.IGNORE_CASE
        )
        val alterTableStart = Regex("""\bALTER\s+TABLE\b""", RegexOption.IGNORE_CASE)

        fun tokenizeSql(sql: String): List<String> {
            val tokens = mutableListOf<String>()
            var position = 0
            while (position < sql.length) {
                val character = sql[position]
                when {
                    character.isWhitespace() -> position += 1
                    character == '"' -> {
                        val start = position
                        position += 1
                        while (position < sql.length) {
                            if (sql[position] == '"') {
                                if (position + 1 < sql.length && sql[position + 1] == '"') {
                                    position += 2
                                } else {
                                    position += 1
                                    break
                                }
                            } else {
                                position += 1
                            }
                        }
                        tokens += sql.substring(start, position)
                    }
                    character.isSqlIdentifierStart() -> {
                        val start = position
                        position += 1
                        while (position < sql.length && sql[position].isSqlIdentifierPart()) {
                            position += 1
                        }
                        tokens += sql.substring(start, position)
                    }
                    else -> {
                        tokens += character.toString()
                        position += 1
                    }
                }
            }
            return tokens
        }

        fun String.substringFrom(regex: Regex): String? {
            val match = regex.find(this) ?: return null
            return substring(match.range.first)
        }

        fun String.isSqlIdentifier(): Boolean = startsWith("\"") || firstOrNull()?.isSqlIdentifierStart() == true

        fun Char.isSqlIdentifierStart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

        fun Char.isSqlIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'
    }
}
