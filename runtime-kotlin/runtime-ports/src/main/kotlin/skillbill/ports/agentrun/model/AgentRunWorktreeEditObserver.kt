package skillbill.ports.agentrun.model

fun interface AgentRunWorktreeEditObserver {
  fun observe()

  companion object {
    val NONE: AgentRunWorktreeEditObserver = AgentRunWorktreeEditObserver { }
  }
}
