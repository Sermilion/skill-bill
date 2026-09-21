package codegraphfixture

class Caller(private val greeter: GreeterPort) {
  fun call(): String = greeter.greet("fixture")

  fun callExtension(): String = "fixture".greet("prefix")
}
