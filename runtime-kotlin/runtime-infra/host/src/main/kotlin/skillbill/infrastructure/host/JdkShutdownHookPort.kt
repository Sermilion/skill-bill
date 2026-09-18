package skillbill.infrastructure.host

import me.tatarka.inject.annotations.Inject
import skillbill.ports.process.ShutdownHookPort
import skillbill.ports.process.ShutdownHookRegistration

@Inject
class JdkShutdownHookPort : ShutdownHookPort {
  override fun register(action: () -> Unit): ShutdownHookRegistration {
    val hook = Thread(action)
    Runtime.getRuntime().addShutdownHook(hook)
    return JdkShutdownHookRegistration(hook)
  }

  private class JdkShutdownHookRegistration(
    private val hook: Thread,
  ) : ShutdownHookRegistration {
    override fun unregister(): Boolean = Runtime.getRuntime().removeShutdownHook(hook)
  }
}
