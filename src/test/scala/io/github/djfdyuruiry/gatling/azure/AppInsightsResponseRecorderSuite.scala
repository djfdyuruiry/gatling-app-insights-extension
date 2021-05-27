package io.github.djfdyuruiry.gatling.azure

import java.util
import java.util.Date

import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import org.mockito.verification.VerificationMode
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import io.gatling.commons.stats.OK
import io.gatling.core.session.{Block, Session}
import io.gatling.http.client.Request
import io.gatling.http.client.body.RequestBody
import io.gatling.http.client.realm.Realm
import io.gatling.http.client.uri.Uri
import io.gatling.http.response.{Response, ResponseBody}
import io.netty.channel.EventLoop
import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.{HttpHeaders, HttpMethod, HttpResponseStatus}

import com.microsoft.applicationinsights.telemetry.{Duration, RequestTelemetry}
import com.microsoft.applicationinsights.{TelemetryClient, TelemetryConfiguration}

//noinspection ScalaDeprecation
class AppInsightsResponseRecorderSuite extends AnyFunSuite with BeforeAndAfter with MockitoSugar {
  private val testInstrumentationKey: String = "test-instrumentation-key"

  private var recorderConfig: RecorderConfig = _
  private var telemetryClient: TelemetryClient = _

  private var recorder: AppInsightsResponseRecorder = _

  private var telemetryConfigUsed: TelemetryConfiguration = _
  private var session: Session = _
  private var request: Request = _
  private var response: Response = _

  before {
    val sessionFields = Map[String, Any](
      "MagicNumber" -> 42,
      "CountryCode" -> "en-gb"
    )

    session = new Session(
      "scenario name",
      12345,
      sessionFields,
      OK,
      List[Block](),
      _ => {
        // on exit
      },
      mock[EventLoop]
    )

    request = new Request(
      HttpMethod.GET,
      Uri.create("https://duckduckgo.com?someQueryKey=val"),
      mock[HttpHeaders],
      new util.ArrayList[Cookie](),
      mock[RequestBody],
      30000,
      null,
      null,
      null,
      mock[Realm],
      null,
      null,
      null,
      false,
      false,
      false,
      null
    )

    response = Response(
      request,
      0,
      100,
      HttpResponseStatus.OK,
      mock[HttpHeaders],
      mock[ResponseBody],
      Map[String, String](),
      isHttp2 = false
    )

    recorderConfig = RecorderConfig(testInstrumentationKey)
    telemetryClient = mock[TelemetryClient]

    buildRecorder(recorderConfig, telemetryClient)
  }

  test("when recordResponse is called and config is null then exception is thrown") {
    buildRecorder(null, telemetryClient)

    assertThrows[AppInsightsRecorderConfigException] {
      recorder.recordResponse(session, response)
    }
  }

  test("when recordResponse is called and config has blank instrumentation key then exception is thrown") {
    buildRecorder(RecorderConfig(""), telemetryClient)

    assertThrows[AppInsightsRecorderConfigException] {
      recorder.recordResponse(session, response)
    }
  }

  test("when recordResponse is called then telemetry client is created with correct instrumentation key") {
    recorder.recordResponse(session, response)

    assert(telemetryConfigUsed.getInstrumentationKey === testInstrumentationKey)
  }

  test("when recordResponse is called then telemetry client is called") {
    recorder.recordResponse(session, response)

    verify(telemetryClient, times(1)).trackRequest(any[RequestTelemetry])
  }

  for ((fieldName, (fieldExtractor, expectedValue)) <- Map[String, (RequestTelemetry => Any, Any)](
    "name" -> (_.getName, "GET https://duckduckgo.com"),
    "url" -> (_.getUrl.toString, "https://duckduckgo.com"),
    "method" -> (_.getHttpMethod, "GET"),
    "responseCode" -> (_.getResponseCode, "200"),
    "timestamp" -> (_.getTimestamp, new Date(0)),
    "duration" -> (_.getDuration, new Duration(100)),
    "GatlingScenario" -> (_.getProperties.get("GatlingScenario"), "scenario name"),
    "HttpUrl" -> (_.getProperties.get("HttpUrl"), "https://duckduckgo.com"),
    "HttpQueryString" -> (_.getProperties.get("HttpQueryString"), "someQueryKey=val"),
    "HttpMethod" -> (_.getProperties.get("HttpMethod"), "GET"),
    "StartTime" -> (_.getProperties.get("StartTime"), "0"),
    "EndTime" -> (_.getProperties.get("EndTime"), "100")
  )) {
    test(s"when recordResponse is called then returned response has correct ${fieldName} value") {
      runRecordTest(fieldExtractor, expectedValue)
    }
  }

  test("when config.defaultValue is set and recordResponse is called then returned response has correct default values") {
    recorderConfig = RecorderConfig(
      testInstrumentationKey,
      defaultValue = "special",
      sessionFieldMappings = Map(
        "TestField" -> "MissingField"
      )
    )

    buildRecorder()

    runRecordTest(_.getProperties.get("TestField"), "special")
  }

  test("when config.requestNameProvider is set and recordResponse is called then returned response has correct name") {
    recorderConfig = RecorderConfig(
      testInstrumentationKey,
      requestNameProvider = (_, _) => "some dummy name"
    )

    buildRecorder()

    runRecordTest(_.getName, "some dummy name")
  }

  test("when config.sessionFieldMappings is set and recordResponse is called then returned response has session fields") {
    recorderConfig = RecorderConfig(
      testInstrumentationKey,
      sessionFieldMappings = Map(
        "MagicNumberOut" -> "MagicNumber",
        "CountryCodeOut" -> "CountryCode"
      )
    )

    buildRecorder()

    runRecordTest(_.getProperties.get("MagicNumberOut"), "42", times(1))
    runRecordTest(_.getProperties.get("CountryCodeOut"), "en-gb", times(2))
  }

  test("when config.customMappings is set and recordResponse is called then returned response has custom fields") {
    recorderConfig = RecorderConfig(
      testInstrumentationKey,
      customMappings = Map(
        "FormattedScenario" -> ((s, _) => s"--> ${s.scenario} <--"),
        "LogCode" -> ((_, r) => s"responseCode=${r.status.code()}")
      )
    )

    buildRecorder()

    runRecordTest(_.getProperties.get("FormattedScenario"), "--> scenario name <--", times(1))
    runRecordTest(_.getProperties.get("LogCode"), "responseCode=200", times(2))
  }

  test("when config.requestHooks is set and recordResponse is called then hook is called") {
    var hookWasCalled = false

    runHookTest(
      (_, _, _, _) => hookWasCalled = true
    )

    assert(hookWasCalled === true)
  }

  test("when config.requestHooks is set and recordResponse is called then client is passed to hook") {
    var clientPassed: TelemetryClient = null

    runHookTest(
      (c, _, _, _) => clientPassed = c
    )

    assert(clientPassed === telemetryClient)
  }

  test("when config.requestHooks is set and recordResponse is called then session is passed to hook") {
    var sessionPassed: Session = null

    runHookTest(
        (_, s, _, _) => sessionPassed = s
    )

    assert(sessionPassed === session)
  }

  test("when config.requestHooks is set and recordResponse is called then response is passed to hook") {
    var responsePassed: Response = null

    runHookTest(
      (_, _, r, _) => responsePassed = r
    )

    assert(responsePassed === response)
  }

  test("when config.requestHooks is set and recordResponse is called then telemetry is passed to hook") {
    var telemetryPassed: RequestTelemetry = null

    runHookTest(
      (_, _, _, r) => telemetryPassed = r
    )

    assert(telemetryPassed !== null)
  }

  test("when recordResponse called then response is returned") {
    assert(
      recorder.recordResponse(session, response) === response
    )
  }

  test("when flushAppInsightRequests is called then telemetry client is called") {
    recorder.flushAppInsightRequests()

    verify(telemetryClient, times(1)).flush()
  }

  private def buildRecorder(recorderConfigToUse: RecorderConfig = recorderConfig,
                            telemetryClientToUse: TelemetryClient = telemetryClient): Unit = {
    recorder = new AppInsightsResponseRecorder()

    recorder.config = recorderConfigToUse
    recorder.telemetryClientFactory = c => {
      telemetryConfigUsed = c

      telemetryClientToUse
    }
  }

  private def runRecordTest(fieldExtractor: RequestTelemetry => Any,
                            expectedValue: Any,
                            timesCalled: VerificationMode = times(1)): Unit = {
    recorder.recordResponse(session, response)

    val requestCaptor = ArgCaptor[RequestTelemetry]

    verify(telemetryClient, timesCalled).trackRequest(requestCaptor.capture)

    val requestTelemetryFieldValue = fieldExtractor.apply(requestCaptor.value)

    assert(requestTelemetryFieldValue === expectedValue)
  }

  def runHookTest(hook: (TelemetryClient, Session, Response, RequestTelemetry) => Unit): Unit = {
    recorderConfig = RecorderConfig(
      testInstrumentationKey,
      requestHooks = Seq(hook)
    )

    buildRecorder()

    recorder.recordResponse(session, response)
  }
}
