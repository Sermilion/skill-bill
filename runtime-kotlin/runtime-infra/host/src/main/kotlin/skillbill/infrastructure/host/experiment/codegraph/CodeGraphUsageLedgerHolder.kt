package skillbill.infrastructure.host.experiment.codegraph

import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort

object CodeGraphUsageLedgerHolder : CodeGraphUsageLedgerPort by InMemoryCodeGraphUsageLedger()
