package codegraphfixture

interface GreeterPort {
  fun greet(name: String): String
}

class DefaultGreeter : GreeterPort {
  override fun greet(name: String): String = name.greet()
}
