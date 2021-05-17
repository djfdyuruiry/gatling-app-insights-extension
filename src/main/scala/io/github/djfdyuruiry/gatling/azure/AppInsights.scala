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
      throw new IllegalStateException("Please initialize app " +
        "insights config by callingAppInsights.useAppInsightsConfig")
    }

    recorderInstance
  }

  def enableAppInsights(): Unit =
    appInsightsEnabled = true

  def disableAppInsights(): Unit =
    appInsightsEnabled = false

  def useAppInsightsConfig(recorderConfig: RecorderConfig): Unit = {
    recorderInstance = new AppInsightsResponseRecorder()

    recorderInstance.config = recorderConfig
  }

  implicit class RequestBuilderExtensions(builder: HttpRequestBuilder) {
    def recordToAppInsights: HttpRequestBuilder = {
      if (!appInsightsEnabled) {
        return builder
      }

      builder.transformResponse(getRecorder.recordResponse)
    }
  }

  implicit class ScenarioExtensions(scenario: ScenarioBuilder) {
    def withAppInsightsRecording(builder: ChainBuilder): ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario.exec(builder)
        .exec(s => {
          getRecorder.flushAppInsightRequests()
          s
        })
    }

    def withAppInsightsRecording(builder: ActionBuilder): ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario.exec(builder)
        .exec(s => {
          getRecorder.flushAppInsightRequests()
          s
        })
    }

    def flushAppInsightsRecordings: ScenarioBuilder = {
      if (!appInsightsEnabled) {
        return scenario
      }

      scenario
        .exec(s => {
          getRecorder.flushAppInsightRequests()
          s
        })
    }
  }
}
