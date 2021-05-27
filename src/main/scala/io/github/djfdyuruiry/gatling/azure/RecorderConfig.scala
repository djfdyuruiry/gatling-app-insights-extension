package io.github.djfdyuruiry.gatling.azure

import io.gatling.core.session.Session
import io.gatling.http.response.Response

import com.microsoft.applicationinsights.TelemetryClient
import com.microsoft.applicationinsights.telemetry.RequestTelemetry

case class RecorderConfig(instrumentationKey: String,
                          defaultValue: String = "unknown",
                          requestNameProvider: (Session, Response) => String =
                            (_, r) => s"${r.request.getMethod} ${r.request.getUri.toUrlWithoutQuery}",
                          sessionFieldMappings: Map[String, String] = Map(),
                          customMappings: Map[String, (Session, Response) => String] = Map(),
                          requestHooks: Seq[(TelemetryClient, Session, Response, RequestTelemetry) => Unit] = Seq())
