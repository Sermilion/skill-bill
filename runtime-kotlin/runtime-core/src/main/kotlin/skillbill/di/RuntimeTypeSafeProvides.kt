package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.http.HttpSystemOneEvaluationClient
import skillbill.ports.typesafe.SystemOneEvaluationPort

internal interface RuntimeTypeSafeProvides {
  @Provides @JvmSynthetic
  fun systemOneEvaluationPort(client: HttpSystemOneEvaluationClient): SystemOneEvaluationPort = client
}
