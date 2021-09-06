package io.github.djfdyuruiry.gatling.azure

import java.lang.Runtime.getRuntime

import scala.language.implicitConversions

import io.gatling.core.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

object AppInsights {
  private var appInsightsEnabled = true
  private var recorderInstance: AppInsightsResponseRecorder = _

  private def getRecorder: AppInsightsResponseRecorder = {
    if (recorderInstance == null) {
      throw new AppInsightsRecorderConfigException(
        "Please initialize app insights config by calling AppInsights.useAppInsightsConfig"
      )
    }

    recorderInstance
  }

  def appInsightsIsEnabled: Boolean =
    appInsightsEnabled

  def enableAppInsights(): Unit =
    appInsightsEnabled = true

  def disableAppInsights(): Unit =
    appInsightsEnabled = false

  def useAppInsightsConfig(recorderConfig: RecorderConfig,
                           recorderFactory: () => AppInsightsResponseRecorder =
                             () => new AppInsightsResponseRecorder()): Unit = {
    recorderInstance = recorderFactory.apply()

    recorderInstance.config = recorderConfig
  }

  def reset(): Unit = {
    appInsightsEnabled = true

    if (recorderInstance != null) {
      recorderInstance.flushAppInsightRequests()
    }

    recorderInstance = null
  }

  getRuntime.addShutdownHook(
    new Thread(() => reset())
  )

  implicit class RequestBuilderExtensions(builder: HttpRequestBuilder) {
    def recordToAppInsights: HttpRequestBuilder = {
      if (!appInsightsEnabled) {
        return builder
      }

      builder.transformResponse(getRecorder.recordResponse)
    }
  }
}
