package dev.comon.fsp.core.network

/** §5.7 TokenPair JSON with 43-char tokens derived from [prefix] (access `…A`, refresh `…B`). */
fun tokenPairJson(prefix: String): String = """
    {"tokenType":"Bearer","accessToken":"${prefix.padEnd(43, 'A')}","expiresIn":900,
     "accessExpiresAt":"2026-09-30T00:20:10.000Z","refreshToken":"${prefix.padEnd(43, 'B')}",
     "refreshExpiresIn":2591000,"refreshExpiresAt":"2026-10-29T23:48:30.000Z",
     "sessionId":"00000000-0000-4000-8000-00000000000b","credentialVersion":1}
""".trimIndent()
