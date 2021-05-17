package io.github.djfdyuruiry.gatling.azure

import java.util.Date

import scala.jdk.CollectionConverters.MapHasAsJava

import io.gatling.core.session.Session
import io.gatling.http.response.Response

import com.microsoft.applicationinsights.telemetry.{Duration, RequestTelemetry}
import com.microsoft.applicationinsights.{TelemetryClient, TelemetryConfiguration}

class AppInsightsResponseRecorder {
  var telemetryClientFactory: TelemetryConfiguration => TelemetryClient = c => new TelemetryClient(c)
  var config: RecorderConfig = RecorderConfig()

  lazy val telemetryClient: TelemetryClient = {
    val config = TelemetryConfiguration.createDefault();

    telemetryClientFactory.apply(config)
  }

  private def getSessionValOrDefault(session: Session, key: String): String = {
    if (session.contains(key)) {
      return session(key).as[String]
    }

    config.defaultValue
  }

  //noinspection ScalaDeprecation
  private def mapResponseAndTrackInAppInsights(session: Session, response: Response): Unit = {
    val request = response.request
    val requestUrl = request.getUri.toUrlWithoutQuery
    val requestMethod = s"${request.getMethod}"
    val requestName = config.requestNameProvider.apply(session, response)
    val durationInMs = response.endTimestamp - response.startTimestamp

    val insightsRequest = new RequestTelemetry

    insightsRequest.setName(requestName)
    insightsRequest.setUrl(requestUrl)
    insightsRequest.setHttpMethod(requestMethod)
    insightsRequest.setResponseCode(s"${response.status.code()}")
    insightsRequest.setTimestamp(new Date(response.startTimestamp))
    insightsRequest.setDuration(new Duration(durationInMs))

    var queryString = request.getUri.getQuery

    if (queryString == null) {
      queryString = ""
    }

    val customProperties = Map[String, String](
      "GatlingScenario" -> session.scenario,
      "HttpUrl" -> requestUrl,
      "HttpQueryString" -> queryString,
      "HttpMethod" -> requestMethod,
      "StartTime" -> s"${response.startTimestamp}",
      "EndTime" -> s"${response.endTimestamp}",
    ) ++ config.sessionFieldMappings.map(kvp =>
      (kvp._1, getSessionValOrDefault(session, kvp._2))
    ) ++ config.customMappings.map(kvp =>
      (kvp._1, kvp._2.apply(session, response))
    )

    insightsRequest.getProperties.putAll(customProperties.asJava)

    telemetryClient.trackRequest(insightsRequest)
  }

  def recordResponse(session: Session, response: Response): Response = {
    try {
      mapResponseAndTrackInAppInsights(session, response)
    } catch {
      case e: Throwable => e.printStackTrace()
    }

    response
  }

  def flushAppInsightRequests(): Unit =
    telemetryClient.flush()
}
