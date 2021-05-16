package io.github.djfdyuruiry.gatling.azure

import java.util

import org.junit.runner.RunWith
import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

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

import com.microsoft.applicationinsights.telemetry.RequestTelemetry
import com.microsoft.applicationinsights.{TelemetryClient, TelemetryConfiguration}

@RunWith(classOf[JUnitRunner])
class AppInsightsResponseRecorderSuite extends AnyFunSuite with BeforeAndAfter with MockitoSugar {
  private var session: Session = _
  private var request: Request = _
  private var response: Response = _
  private var telemetryClient: TelemetryClient = _

  private var recorder: AppInsightsResponseRecorder = _

  private var telemetryConfigUsed: TelemetryConfiguration = _

  before {
    session = new Session(
      "scenario",
      12345,
      Map[String, Any](),
      OK,
      List[Block](),
      _ => {
        // on exit
      },
      mock[EventLoop]
    )

    request = new Request(
      HttpMethod.GET,
      Uri.create("https://duckduckgo.com"),
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

    telemetryClient = mock[TelemetryClient]

    recorder = new AppInsightsResponseRecorder()

    recorder.telemetryClientFactory = c => {
      telemetryConfigUsed = c

      telemetryClient
    }
  }

  test("when recordResponse called then telemetry client is called") {
    recorder.recordResponse(session, response)

    verify(telemetryClient, times(1)).trackRequest(any[RequestTelemetry])
  }

  test("when recordResponse called then response is returned") {
    assert(
      recorder.recordResponse(session, response) === response
    )
  }
}
