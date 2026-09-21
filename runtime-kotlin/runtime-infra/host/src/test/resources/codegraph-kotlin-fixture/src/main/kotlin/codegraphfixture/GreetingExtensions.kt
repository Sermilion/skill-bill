package codegraphfixture

fun String.greet(): String = "hello $this"

fun String.greet(prefix: String): String = "$prefix $this"
