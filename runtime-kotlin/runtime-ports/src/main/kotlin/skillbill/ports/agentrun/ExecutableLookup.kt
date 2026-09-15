package skillbill.ports.agentrun

fun interface ExecutableLookup {
  fun onPath(executable: String): Boolean
}
