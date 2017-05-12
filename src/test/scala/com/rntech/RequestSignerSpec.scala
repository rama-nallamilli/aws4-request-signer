package com.rntech


import java.time.ZonedDateTime

import com.amazonaws.auth.BasicAWSCredentials
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

class RequestSignerSpec extends FlatSpec with Matchers {

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

  val credentials = new BasicAWSCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")

  forAll(scenerios) { (scenarioName: String) =>

    it should s"build a valid canonical request for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)

      withFileContents(fileName = s"$filePath.req") { requestStr =>
        withFileContents(fileName = s"$filePath.creq") { expectedCanonical =>
          val request = parseRequest(requestStr)
          val canonicalRequest = RequestSigner.CanonicalRequestBuilder.buildCanonicalRequest(request)

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

    it should s"create the signed request for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)
      withFileContents(fileName = s"$filePath.req") { requestStr =>
        withFileContents(fileName = s"$filePath.sreq") { expectedSignedRequest =>

          val requestDate = "2015-08-30T12:36:00Z[UTC]"
          val request = parseRequest(requestStr)

          val canonicalRequest = RequestSigner.CanonicalRequestBuilder.buildCanonicalRequest(request)

          val stringToSign = RequestSigner.StringToSignBuilder.buildStringToSign(
            region = "us-east-1",
            service = "service",
            canonicalRequest = canonicalRequest,
            requestDate = ZonedDateTime.parse(requestDate))

          val authHeader = RequestSigner.StringSigner.signStringWithS4(
            stringToSign = stringToSign,
            requestDate = ZonedDateTime.parse(requestDate),
            credentials = credentials,
            service = "service",
            region = "us-east-1",
            headers = request.headers)

          val signedRequest = requestStr.mkString("\n") + s"\nAuthorization: $authHeader"
          signedRequest shouldBe expectedSignedRequest.mkString("\n")
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

    val statusLineRegex = """(GET|POST|PUT|DELETE)\s(.*?)\sHTTP.*""".r
    statusLineRegex.findFirstMatchIn(requestStr.head).map { m => (m.group(1), m.group(2)) }
      .map { case (method, uri) =>
        val (headers, body) = parseAndGroupHeadersAndBody(requestStr.tail)
        val (uriWithoutQueryParams, queryString) = uri.split("\\?", 2).toList match {
          case path :: Nil => (path, "")
          case path :: qs => (path, qs.head)
          case Nil => throw new Exception("Invalid request status line")
        }
        val queryParams = findQueryParams(queryString.split("\\s", 2).head, Seq.empty)
        val request = Request(headers, body, method, uriWithoutQueryParams, queryParams)
        println(request)
        request
      }.getOrElse(throw new Exception(s"Cannot parse request: ${requestStr.head}"))

  }

  def findQueryParams(queryString: String, params: Seq[QueryParam]): Seq[QueryParam] = {
    queryString.split("=", 2) match {
      case Array(k, rest) => {
        rest.split("&", 2) match {
          case Array(v, next) => {
            findQueryParams(next, params :+ QueryParam(k, v))
          }
          case _ => params :+ QueryParam(k, rest)
        }
      }
      case Array(k) if !k.isEmpty => params :+ QueryParam(k, "")
      case _ => params
    }
  }


  def parseAndGroupHeadersAndBody(payload: List[String]): (Seq[Header], Option[String]) = {

    def addHeaderValue(parseHeaders: ParseHeaders, key: String, value: String): ParseHeaders = {
      if (parseHeaders.headers.contains(key)) {
        val updatedHeaderValue = key -> (parseHeaders.headers(key) :+ value)
        parseHeaders.copy(headers = parseHeaders.headers + updatedHeaderValue, Some(key))
      } else {
        parseHeaders.copy(headers = parseHeaders.headers + (key -> Seq(value)), Some(key))
      }
    }

    case class ParseHeaders(headers: Map[String, Seq[String]], lastKey: Option[String] = None)

    val (headerLines, bodyLines) = payload.span { line => line != "" }
    val headers = headerLines.foldLeft(ParseHeaders(headers = Map.empty[String, Seq[String]], lastKey = None)) { (acc, line) =>
      line.split(":") match {
        case Array(key, value) => addHeaderValue(acc, key, value)
        case Array(value) if acc.lastKey.isDefined =>
          val body = acc.headers(acc.lastKey.get).head + s"\n$value"
          acc.copy(headers = acc.headers + (acc.lastKey.get -> Seq(body)))
      }
    }.headers
      .map {
        case (key, values) => Header(key, values.toList)
      }.toSeq

    val body = bodyLines.filter(_ != "") match {
      case Nil => None
      case lines => Some(lines.mkString("\n"))
    }
    (headers, body)
  }


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