package skillbill.workflow.taskruntime.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureTaskRuntimeProjectionCanonicalizationSchemaParityTest {
  private val schema: JsonNode by lazy {
    YAMLMapper().readTree(Files.readString(planningProjectionsSchemaPath()))
  }

  @Test
  fun `every declared key set equals its schema properties`() {
    FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS.forEach { (schemaLocation, declaredKeys) ->
      val node = resolveSchemaObject(schemaLocation)
      val schemaKeys = node.path("properties").fieldNames().asSequence().toSet()

      assertEquals(
        schemaKeys,
        declaredKeys,
        "Declared key set for '$schemaLocation' drifted from the schema. Mirror the schema's properties " +
          "in FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS, or the canonicalizer will discard a " +
          "governed field as unknown.",
      )
    }
  }

  @Test
  fun `every pruned object is closed in the schema`() {
    FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS.keys.forEach { schemaLocation ->
      val node = resolveSchemaObject(schemaLocation)
      assertTrue(
        node.path("additionalProperties").isBoolean && !node.path("additionalProperties").asBoolean(),
        "'$schemaLocation' must declare additionalProperties:false to be eligible for the unknown-key discard",
      )
    }
  }

  private fun resolveSchemaObject(schemaLocation: String): JsonNode {
    val defs = schema.path("\$defs")
    val segments = schemaLocation.split('.')
    val root = defs.path(segments.first())
    assertTrue(!root.isMissingNode, "schema \$defs has no entry named '${segments.first()}'")
    val resolved = if (segments.size == 1) root else root.path("properties").path(segments[1])
    assertTrue(!resolved.isMissingNode, "schema has no object at '$schemaLocation'")
    return resolved
  }
}

private fun planningProjectionsSchemaPath(): Path {
  val relative = "orchestration/contracts/feature-task-runtime-planning-projections-schema.yaml"
  var current: Path? = Path.of("").toAbsolutePath().normalize()
  while (current != null) {
    val candidate = current.resolve(relative)
    if (Files.isRegularFile(candidate)) return candidate
    current = current.parent
  }
  error("Could not locate '$relative' from ${Path.of("").toAbsolutePath().normalize()}")
}
