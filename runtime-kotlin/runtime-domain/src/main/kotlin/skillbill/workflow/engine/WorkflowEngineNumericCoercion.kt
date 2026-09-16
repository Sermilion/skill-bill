package skillbill.workflow.engine

import skillbill.workflow.taskruntime.model.asExactIntOrNull

internal fun Any?.toStringOrEmpty(): String = this?.toString().orEmpty()
