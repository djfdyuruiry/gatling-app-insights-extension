package io.github.djfdyuruiry.gatling.azure

import java.util.Date

import scala.jdk.CollectionConverters.MapHasAsJava

import io.gatling.core.session.Session
import io.gatling.http.response.Response

import com.microsoft.applicationinsights.core.dependencies.apachecommons.lang3.StringUtils.isBlank
import com.microsoft.applicationinsights.telemetry.{Duration, RequestTelemetry}
import com.microsoft.applicationinsights.{TelemetryClient, TelemetryConfiguration}

class AppInsightsResponseRecorder {
  private lazy val telemetryClient: TelemetryClient = {
    if (config == null) {
      throw new AppInsightsRecorderConfigException(
        "App Insights recorder config must be set before recording requests"
      )
    }

    if (isBlank(config.instrumentationKey)) {
      throw new AppInsightsRecorderConfigException(
        "App Insights recorder config must have a non-blank instrumentation key"
      )
    }

    val telemetryConfig = TelemetryConfiguration.createDefault();

    telemetryConfig.setInstrumentationKey(config.instrumentationKey)

    telemetryClientFactory.apply(telemetryConfig)
  }

  var telemetryClientFactory: TelemetryConfiguration => TelemetryClient = c => new TelemetryClient(c)
  var config: RecorderConfig = _

  private def runRequestHooks(session: Session, response: Response, insightsRequest: RequestTelemetry): Unit =
    config.requestHooks.foreach(_.apply(telemetryClient, session, response, insightsRequest))

  private def getSessionValOrDefault(session: Session, key: String): String = {
    if (!session.contains(key)) {
      return config.defaultValue
    }

    session(key).as[String]
  }

  private def buildCustomProperties(session: Session,
                                    response: Response,
                                    requestUrl: String,
                                    requestMethod: String,
                                    queryString: String): Map[String, String] =
    Map[String, String](
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

  //noinspection ScalaDeprecation
  private def mapResponseAndTrackInAppInsights(session: Session, response: Response): Unit = {
    val appInsightsClient = telemetryClient

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

    val customProperties = buildCustomProperties(session, response, requestUrl, requestMethod, queryString)

    insightsRequest.getProperties.putAll(customProperties.asJava)

    runRequestHooks(session, response, insightsRequest)

    appInsightsClient.trackRequest(insightsRequest)
  }

  def recordResponse(session: Session, response: Response): Response = {
    try {
      mapResponseAndTrackInAppInsights(session, response)
    } catch {
      case e: AppInsightsRecorderConfigException => throw e
      case e: Throwable => e.printStackTrace()
    }

    response
  }

  def flushAppInsightRequests(): Unit =
    telemetryClient.flush()
}
