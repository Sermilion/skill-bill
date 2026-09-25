package skillbill.infrastructure.launcher.agentrun

import skillbill.ports.agentrun.ExecutableLookup

internal val ALL_EXECUTABLES_AVAILABLE = ExecutableLookup { true }

internal fun executablesAvailable(vararg names: String) = ExecutableLookup { it in names }
