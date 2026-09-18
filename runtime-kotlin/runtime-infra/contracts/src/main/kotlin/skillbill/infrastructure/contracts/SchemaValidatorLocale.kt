package skillbill.infrastructure.contracts

import com.networknt.schema.PathType
import com.networknt.schema.SchemaValidatorsConfig
import java.util.Locale

internal val LOCALE_STABLE_SCHEMA_CONFIG: SchemaValidatorsConfig =
  SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).pathType(PathType.LEGACY).build()
