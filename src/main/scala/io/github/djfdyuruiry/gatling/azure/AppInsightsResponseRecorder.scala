package io.github.djfdyuruiry.gatling.azure

import java.util.Date

import scala.jdk.CollectionConverters.MapHasAsJava

import io.gatling.core.session.Session
import io.gatling.http.response.Response

import com.microsoft.applicationinsights.core.dependencies.apachecommons.lang3.StringUtils.{EMPTY, isBlank}
import com.microsoft.applicationinsights.telemetry.{Duration, RequestTelemetry}
import com.microsoft.applicationinsights.{TelemetryClient, TelemetryConfiguration}

class AppInsightsResponseRecorder {
  private val defaultConfig: RecorderConfig = RecorderConfig(EMPTY)
  private var activeTelemetryClient: TelemetryClient = _

  var telemetryClientFactory: TelemetryConfiguration => TelemetryClient = c => new TelemetryClient(c)
  var config: RecorderConfig = defaultConfig

  def buildActiveTelemetryClient(telemetryConfig: TelemetryConfiguration): TelemetryClient = {
    if (telemetryClientFactory == null) {
      throw new AppInsightsRecorderConfigException(
        "Custom telemetryClientFactory was null"
      )
    }

    activeTelemetryClient = telemetryClientFactory.apply(telemetryConfig)

    if (activeTelemetryClient == null) {
      throw new AppInsightsRecorderConfigException(
        "Custom telemetryClientFactory returned null"
      )
    }

    activeTelemetryClient
  }

  private def buildActiveTelemetryClientIfRequired: TelemetryClient = {
    if (activeTelemetryClient != null) {
      return activeTelemetryClient
    }

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

    val telemetryConfig = TelemetryConfiguration.getActive;

    telemetryConfig.setInstrumentationKey(config.instrumentationKey)

    buildActiveTelemetryClient(telemetryConfig)
  }

  private def getConfigValueOrDefault[T](valueExtractor: (RecorderConfig) => T): T = {
    var value = valueExtractor.apply(config)

    if (value == null) {
      value = valueExtractor.apply(defaultConfig)
    }

    value
  }

  private def runRequestHooks(session: Session, response: Response, insightsRequest: RequestTelemetry): Unit =
    getConfigValueOrDefault(_.requestHooks).foreach(
      _.apply(activeTelemetryClient, session, response, insightsRequest)
    )

  private def getSessionValOrDefault(session: Session, key: String): String = {
    if (!session.contains(key)) {
      return getConfigValueOrDefault(_.defaultValue)
    }

    session(key).as[String]
  }

  private def buildCustomProperties(session: Session,
                                    response: Response,
                                    requestUrl: String,
                                    requestMethod: String,
                                    queryString: String): Map[String, String] = {
    var queryStringToRecord = ""

    if (queryString != null) {
      queryStringToRecord = queryString
    }

    Map[String, String](
      "GatlingScenario" -> session.scenario,
      "HttpUrl" -> requestUrl,
      "HttpQueryString" -> queryStringToRecord,
      "HttpMethod" -> requestMethod,
      "StartTime" -> s"${response.startTimestamp}",
      "EndTime" -> s"${response.endTimestamp}",
    ) ++ getConfigValueOrDefault(_.sessionFieldMappings).map(kvp =>
      (kvp._1, getSessionValOrDefault(session, kvp._2))
    ) ++ getConfigValueOrDefault(_.customMappings).map(kvp =>
      (kvp._1, kvp._2.apply(session, response))
    )
  }

  //noinspection ScalaDeprecation
  private def mapResponseAndTrackInAppInsights(session: Session, response: Response): Unit = {
    val request = response.request
    val requestUrl = request.getUri.toUrlWithoutQuery
    val requestMethod = s"${request.getMethod}"
    val requestName = getConfigValueOrDefault(_.requestNameProvider).apply(session, response)
    val durationInMs = response.endTimestamp - response.startTimestamp
    val statusCode = response.status.code();

    val insightsRequest = new RequestTelemetry(
      requestName,
      new Date(response.startTimestamp),
      new Duration(durationInMs),
      s"${statusCode}",
      statusCode < 400
    );

    insightsRequest.getContext
      .getOperation
      .setName(requestName)

    insightsRequest.setUrl(requestUrl)
    insightsRequest.setHttpMethod(requestMethod)
    insightsRequest.setResponseCode(s"${statusCode}")

    insightsRequest.getProperties
      .putAll(
        buildCustomProperties(
          session,
          response,
          requestUrl,
          requestMethod,
          request.getUri.getQuery
        ).asJava
      )

    runRequestHooks(session, response, insightsRequest)

    activeTelemetryClient.trackRequest(insightsRequest)
  }

  def recordResponse(session: Session, response: Response): Response = {
    try {
      buildActiveTelemetryClientIfRequired

      mapResponseAndTrackInAppInsights(session, response)
    } catch {
      case e: AppInsightsRecorderConfigException => throw e
      case e: Throwable => getConfigValueOrDefault(_.exceptionHandler).apply(e)
    }

    response
  }

  def flushAppInsightRequests(): Unit =
    if (activeTelemetryClient != null) {
      activeTelemetryClient.flush()
    }
}
