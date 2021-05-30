# Gatling App Insights Extension

This library provides extensions for the Gatling testing framework for recording requests telemetry to Azure App Insights.

- Record Gatling scenario/user metadata
- Send headers, cookies, query parameters and more
- Configuration for custom request properties
- Hooks to customize request telemetry

## Quickstart

- Go to the [latest release](https://github.com/djfdyuruiry/gatling-app-insights-extension/releases/latest) on GitHub and download the `jar` file

- Add the `jar` file to your project directory

- Reference the library in your Maven/Gradle project

- Add the following import to your Gatling Simulation class:

  ```scala
  import io.github.djfdyuruiry.gatling.azure.AppInsights.useAppInsightsConfig
  import io.github.djfdyuruiry.gatling.azure.AppInsights.RequestBuilderExtensions
  import io.github.djfdyuruiry.gatling.azure.RecorderConfig
  ```

- Use the `recordToAppInsights` extension method to record a Gatling HTTP request to App Insights

  ```scala
  val scn = scenario("Scenario Name")
    .exec(
      http("request_1")
        .get("/")
        .recordToAppInsights
    )
    .pause(7)
    .exec(
      http("request_2")
        .get("/computers?f=macbook")
        .recordToAppInsights
    )
    .pause(2)
    .exec(
      http("request_3")
        .post("/computers")
        .formParam("name", "Beautiful Computer")
        .formParam("introduced", "2012-05-30")
        .formParam("discontinued", "")
        .formParam("company", "37")
        .recordToAppInsights
    )
  ``` 

- Update your `setUp` to configure recording

  ```
  setUp({
    useAppInsightsConfig(
      RecorderConfig("<YOUR APP INSIGHTS INSTRUMENTATION KEY HERE>")
    )

    // your scneario injection code
  })
  ```

## Configuration

The `RecorderConfig` class provides the following options:

- `defaultValue: String`
  - Description: Value to use when a property can't be found in the current Gatling session/request
  - Default: `unknown`

- `requestNameProvider: (Session, Response) => String`
  - Description: Lambda to generate the App Insights request name from a Gatling session and response
  - Default: Returns `<REQUEST METHOD> <REQUEST URL>`, e.g. `GET https://somewhere.org/resource`

- `sessionFieldMappings: Map[String, String]`
  - Description: Add fields from the Gatling session as request metadata, key is the session field name and value is the desired metadata field name
  - Default: None

- `customMappings: Map[String, (Session, Response) => String]`
  - Description: Extract some data from the current Gatling session/request and store it in a request metadata field. Key is the desired metadata field name and key is a lambda which takes the current session/request and returns a string value
  - Default: None

- `requestHooks: Seq[(TelemetryClient, Session, Response, RequestTelemetry) => Unit]`
  - Description: Callback lambdas which will be run before telemetry is sent to App Insights, this allows advanced manipulation of the telemetry and/or interaction with the client
  - Default: None

- `exceptionHandler: (Throwable) => Unit`
  - Description: Lambda to handle any exceptions thrown when recording to App Insights
  - Default: Simply calls the `printStackTrace` method of the exception and ignores the error

Azure App Insights Java SDK v2 configuration can be tweaked as well, see: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-2x-get-started?tabs=maven#add-an-applicationinsightsxml-file

## Limitations

- Requests that fail to be sent because Gatling can't build the request body or URL (missing session field etc.) will not be recorded
- Timeouts or other network level request failures (DNS failure etc.) will not be recorded
