package io.github.djfdyuruiry.gatling.azure

import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import AppInsights.RequestBuilderExtensions

class AppInsightsSuite extends AnyFunSuite with BeforeAndAfter {
  private val testInstrumentationKey: String = "test-instrumentation-key"

  private var recorderConfig: RecorderConfig = _
  private var recorder: AppInsightsResponseRecorder = _

  before {
    recorderConfig = RecorderConfig(testInstrumentationKey)
    recorder = new AppInsightsResponseRecorder()
  }

  after {
    AppInsights.reset()
  }

  test("when useAppInsightsConfig is called then config is passed to recorder") {
    AppInsights.useAppInsightsConfig(recorderConfig, () => recorder)

    assert(recorder.config === recorderConfig)
  }

  test("when useAppInsightsConfig is called and recordToAppInsights is called for request builder then exception is not thrown") {
    AppInsights.useAppInsightsConfig(recorderConfig, () => recorder)

    http("test").get("http://no.where/")
      .recordToAppInsights
  }

  test("when useAppInsightsConfig is not called and recordToAppInsights is called for request builder then exception is thrown") {
    assertThrows[AppInsightsRecorderConfigException] {
      http("test").get("http://no.where/")
        .recordToAppInsights
    }
  }

  test("appInsightsIsEnabled default value is true") {
    assert(AppInsights.appInsightsIsEnabled === true)
  }

  test("when disableAppInsights is called then appInsightsIsEnabled is false") {
    AppInsights.disableAppInsights()

    assert(AppInsights.appInsightsIsEnabled === false)
  }

  test("when enableAppInsights is called then appInsightsIsEnabled is true") {
    AppInsights.enableAppInsights()

    assert(AppInsights.appInsightsIsEnabled === true)
  }

  test("when disableAppInsights is called and then enableAppInsights is called then appInsightsIsEnabled is true") {
    AppInsights.disableAppInsights()
    AppInsights.enableAppInsights()

    assert(AppInsights.appInsightsIsEnabled === true)
  }
}
