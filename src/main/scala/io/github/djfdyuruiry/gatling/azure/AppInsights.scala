package io.github.djfdyuruiry.gatling.azure

import scala.language.implicitConversions

import io.gatling.core.Predef._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
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
    recorderInstance = null
  }

  implicit class RequestBuilderExtensions(builder: HttpRequestBuilder) {
    def recordToAppInsights: HttpRequestBuilder = {
      if (!appInsightsEnabled) {
        return builder
      }

      builder.transformResponse(getRecorder.recordResponse)
    }
  }

  implicit class ChainExtensions(chain: ChainBuilder) {
    def flushAppInsightsRecordings: ChainBuilder = {
      if (!appInsightsEnabled) {
        return chain
      }

      chain
        .exec(s => {
          getRecorder.flushAppInsightRequests()
          s
        })
    }
  }

  implicit class ScenarioExtensions(scenario: ScenarioBuilder) {
    def flushAppInsightsRecordings: ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario
        .exec(s => {
          getRecorder.flushAppInsightRequests()
          s
        });
    }

    def withAppInsightsRecording(builder: ChainBuilder): ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario.exec(builder)
        .flushAppInsightsRecordings
    }

    def withAppInsightsRecording(builder: ActionBuilder): ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario.exec(builder)
        .flushAppInsightsRecordings
    }
  }
}
