package skillbill.infrastructure.fs.launcher

import skillbill.ports.agentrun.ExecutableLookup

internal val ALL_EXECUTABLES_AVAILABLE = ExecutableLookup { true }

internal fun executablesAvailable(vararg names: String) = ExecutableLookup { it in names }
