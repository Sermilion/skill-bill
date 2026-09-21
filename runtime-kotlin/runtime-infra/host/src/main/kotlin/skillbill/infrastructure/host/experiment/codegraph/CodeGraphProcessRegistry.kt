package skillbill.infrastructure.host.experiment.codegraph

import java.util.concurrent.ConcurrentHashMap

object CodeGraphProcessRegistry {
  private val processesByPair = ConcurrentHashMap<String, MutableSet<Process>>()

  fun register(pairId: String, process: Process) {
    processesByPair.computeIfAbsent(pairId) { ConcurrentHashMap.newKeySet() }.add(process)
  }

  fun unregister(pairId: String, process: Process) {
    processesByPair[pairId]?.let { processes ->
      processes.remove(process)
      if (processes.isEmpty()) processesByPair.remove(pairId, processes)
    }
  }

  fun destroyOwned(pairId: String) {
    val processes = processesByPair.remove(pairId) ?: return
    processes.forEach { process ->
      if (process.isAlive) {
        process.destroyForcibly()
      }
    }
  }
}
