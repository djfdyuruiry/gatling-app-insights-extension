package io.github.djfdyuruiry.gatling.azure

import io.gatling.core.session.Session
import io.gatling.http.response.Response

case class RecorderConfig(instrumentationKey: String,
                          defaultValue: String = "unknown",
                          requestNameProvider: (Session, Response) => String =
                            (_, r) => s"${r.request.getMethod} ${r.request.getUri.toUrlWithoutQuery}",
                          sessionFieldMappings: Map[String, String] = Map(),
                          customMappings: Map[String, (Session, Response) => String] = Map())
