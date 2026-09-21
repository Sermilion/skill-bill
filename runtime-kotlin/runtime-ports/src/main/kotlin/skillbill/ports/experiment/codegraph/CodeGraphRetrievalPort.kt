package skillbill.ports.experiment.codegraph
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryResult

interface CodeGraphRetrievalPort {
  fun query(request: CodeGraphQueryRequest): CodeGraphQueryResult
}
