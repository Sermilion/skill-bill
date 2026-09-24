package skillbill.infrastructure.contracts

import skillbill.contracts.workflow.goal.GOAL_PROGRESS_EVENT_CONTRACT_VERSION
import skillbill.error.shellcontent.InvalidGoalProgressEventSchemaError
import skillbill.infrastructure.contracts.locator.GoalProgressEventSchemaPaths
import skillbill.infrastructure.contracts.workflow.goal.GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ClasspathContractSchemaLoaderTest {
  @Test
  fun `missing goal progress event schema names classpath resource and does not read cwd`() {
    val emptyDir = Files.createTempDirectory("classpath-schema-cwd")
    val previous = System.getProperty("user.dir")
    System.setProperty("user.dir", emptyDir.toString())
    val classLoader =
      object : ClassLoader(ClasspathContractSchemaLoaderTest::class.java.classLoader) {
        override fun getResourceAsStream(name: String) =
          if (name == GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE) null else super.getResourceAsStream(name)
      }
    try {
      val error =
        assertFailsWith<InvalidGoalProgressEventSchemaError> {
          ClasspathContractSchemaLoader.compiledSchema(
            CompiledSchemaRequest(
              cacheKey = "test-missing-${GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE}",
              classLoader = classLoader,
              classpathResource = GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE,
              missingResource = {
                InvalidGoalProgressEventSchemaError(
                  sourceLabel = GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE,
                  fieldPath = "",
                  reason =
                    "Canonical goal progress event schema is missing. Expected classpath resource " +
                      "'$GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE'.",
                )
              },
              processingFailure = { cause ->
                InvalidGoalProgressEventSchemaError(
                  sourceLabel = GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE,
                  fieldPath = "",
                  reason = cause.message ?: cause::class.simpleName.orEmpty(),
                  cause = cause,
                )
              },
              loadFailureLogger = {},
              expectedSchemaId = GoalProgressEventSchemaPaths.EXPECTED_SCHEMA_ID,
              expectedContractVersion = GOAL_PROGRESS_EVENT_CONTRACT_VERSION,
              identityFailure = { reason ->
                InvalidGoalProgressEventSchemaError(
                  sourceLabel = GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE,
                  fieldPath = "<schema>",
                  reason = reason,
                )
              },
            ),
          )
        }
      assertContains(error.reason, GOAL_PROGRESS_EVENT_SCHEMA_CLASSPATH_RESOURCE)
      assertContains(error.reason, GoalProgressEventSchemaPaths.CLASSPATH_RESOURCE)
      val yamlInCwd = Files.walk(emptyDir).anyMatch { it.fileName.toString().endsWith(".yaml") }
      assertFalse(yamlInCwd)
    } finally {
      System.setProperty("user.dir", previous)
    }
  }
}
