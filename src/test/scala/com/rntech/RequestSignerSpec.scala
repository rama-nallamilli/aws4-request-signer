package com.rntech


import java.time.ZonedDateTime

import com.amazonaws.auth.BasicAWSCredentials
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.io.Source

class RequestSignerSpec extends FlatSpec with Matchers {

  //TODO THE TESTS ARE NOT RUNNING, WTF
  val scenerios =
    Table(
      "scenario",
      "get-header-key-duplicate",
      "get-header-value-multiline",
      "get-header-value-order",
      "get-header-value-trim",
      "get-unreserved",
      "get-utf8",
      "get-vanilla",
      "get-vanilla-empty-query-key",
      "get-vanilla-query",
      "get-vanilla-query-order-key-case",
      "get-vanilla-query-unreserved",
      "get-vanilla-utf8-query",
      "normalize-path/get-relative",
      "normalize-path/get-relative-relative",
      "normalize-path/get-slash",
      "normalize-path/get-slash-dot-slash",
      "normalize-path/get-slash-pointless-dot",
      "normalize-path/get-slashes",
      "normalize-path/get-space",
      "post-header-key-case",
      "post-header-key-sort",
      "post-header-value-case",
      "post-sts-token/post-sts-header-after",
      "post-sts-token/post-sts-header-before",
      "post-vanilla",
      "post-vanilla-empty-query-value",
      "post-vanilla-query",
      "post-vanilla-query-nonunreserved",
      "post-vanilla-query-space",
      "post-x-www-form-urlencoded",
      "post-x-www-form-urlencoded-parameters"
    )
  forAll(scenerios) { (scenarioName: String) =>

        it should s"build a valid canonical request for $scenarioName" in {
          val filePath = resolveFilePathForScenario(scenarioName)

          withFileContents(fileName = s"$filePath.req") { requestStr =>
            withFileContents(fileName = s"$filePath.creq") { expectedCanonical =>
              val request = parseRequest(requestStr)
              val canonicalRequest = RequestSigner.CanonicalRequestBuilder.buildCanonicalRequest(request)

              println("-----------------------------")
              println("-----------------------------")
              println(canonicalRequest)
              println("-----------------------------")
              println("-----------------------------")
              println(expectedCanonical.mkString("\n"))
              println("-----------------------------")
              println("-----------------------------")
              canonicalRequest shouldBe expectedCanonical.mkString("\n")
            }
          }

        }

    it should s"build the string to sign for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)

      withFileContents(fileName = s"$filePath.creq") { canonicalRequest =>
        withFileContents(fileName = s"$filePath.sts") { expectedStringToSign =>

          val stringToSign = RequestSigner.StringToSignBuilder.buildStringToSign(
            region = "us-east-1",
            service = "service",
            canonicalRequest = canonicalRequest.mkString("\n"),
            requestDate = ZonedDateTime.parse("2015-08-30T12:36:00Z[UTC]"))

          stringToSign shouldBe expectedStringToSign.mkString("\n")
        }
      }
    }


    it should s"calculate the signature for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)
      withFileContents(fileName = s"$filePath.req") { requestStr =>
        withFileContents(fileName = s"$filePath.sts") { stringToSign =>
          withFileContents(fileName = s"$filePath.authz") { expectedSignature =>

            val credentials = new BasicAWSCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
            val requestHeaders = parseRequest(requestStr).headers

            val signature = RequestSigner.StringSigner.signStringWithS4(
              stringToSign = stringToSign.mkString("\n"),
              requestDate = ZonedDateTime.parse("2015-08-30T12:36:00Z[UTC]"),
              credentials = credentials,
              service = "service",
              region = "us-east-1",
              headers = requestHeaders)

            signature shouldBe expectedSignature.mkString("\n")
          }
        }
      }
    }
  }

  def resolveFilePathForScenario(scenario: String) = {
    scenario.split("/") match {
      case Array(path, fileName) => s"$path/$fileName/$fileName"
      case Array(fileName) => s"$fileName/$fileName"
      case _ => scenario
    }
  }

  def parseRequest(requestStr: List[String]) = {

    val method = requestStr.head.split(" ")(0)
    val uri = requestStr.head
      .stripPrefix("GET ")
      .stripPrefix("POST ")
      .stripSuffix(" HTTP/1.1")

    val headers = parseAndGroupHeaders(requestStr.tail)

    import com.netaporter.uri.Uri.parseQuery
    val queryParams = uri.split("\\?") match {
      case Array(_, query) => parseQuery(query).params.map {
        case (k, v) => QueryParam(k, v.getOrElse(""))
      }
      case _ => Seq.empty[QueryParam]
    }

    val uriWithoutQueryParams = uri.split("\\?") match {
      case Array(path, query) => path
      case Array(path) => path
    }

    val request = Request(headers, None, method, uriWithoutQueryParams, queryParams)
    println(request)
    request
  }

  def parseAndGroupHeaders(headerLines: List[String]): Seq[Header] = {

    def addHeaderValue(parseHeaders: ParseHeaders, key: String, value: String): ParseHeaders = {
      if(parseHeaders.headers.contains(key)) {
        val updatedHeaderValue = (key -> (parseHeaders.headers(key) :+ value))
        parseHeaders.copy(headers = parseHeaders.headers + updatedHeaderValue, Some(key))
      } else {
        parseHeaders.copy(headers = parseHeaders.headers + (key -> Seq(value)), Some(key))
      }
    }

    case class ParseHeaders(headers: Map[String, Seq[String]], lastKey: Option[String] = None)
    headerLines.foldLeft(ParseHeaders(headers = Map.empty[String, Seq[String]], lastKey = None)) { (acc, line) =>
      line.split(":") match {
        case Array(key, value) => addHeaderValue(acc, key, value)
        case Array(value) if acc.lastKey.isDefined =>
          val body = acc.headers(acc.lastKey.get).head + s"\n$value"
          acc.copy(headers = acc.headers + (acc.lastKey.get -> Seq(body)))
      }
    }.headers
      } map {
      case (key, values) => Header(key, values.toList)
    } toSeq



  def using[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A =
    try {
      f(closeable)
    } finally {
      closeable.close()
    }

  def withFileContents[T](fileName: String)(body: List[String] => T) = {
    using(Source.fromURL(getClass.getResource(s"/aws-sig-v4-test-suite/$fileName"))) { stream =>
      body(stream.getLines().toList)
    }
  }
}