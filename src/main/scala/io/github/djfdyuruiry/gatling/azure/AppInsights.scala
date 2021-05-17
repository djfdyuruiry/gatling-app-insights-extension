package io.github.djfdyuruiry.gatling.azure

import scala.language.implicitConversions

import io.gatling.core.Predef._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.request.builder.HttpRequestBuilder

object AppInsights {
  private lazy val recorder: AppInsightsResponseRecorder = {
    new AppInsightsResponseRecorder()
  }

  implicit class RequestBuilderExtensions(builder: HttpRequestBuilder) {
    def recordToAppInsights: HttpRequestBuilder =
      builder.transformResponse(recorder.recordResponse)
  }

  implicit class ScenarioExtensions(scenario: ScenarioBuilder) {
    def withAppInsightsRecording(builder: ActionBuilder): ScenarioBuilder =
      scenario.exec(builder)
        .exec(s => {
          recorder.flushAppInsightRequests()
          s
        })

    def flushAppInsightsRecordings: ScenarioBuilder =
      scenario
        .exec(s => {
          recorder.flushAppInsightRequests()
          s
        })
  }
}
