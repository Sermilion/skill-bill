package skillbill.infrastructure.launcher.launcher

import skillbill.ports.agentrun.ExecutableLookup

internal val ALL_EXECUTABLES_AVAILABLE = ExecutableLookup { it != "codegraph" }

internal fun executablesAvailable(vararg names: String) = ExecutableLookup { it in names }
