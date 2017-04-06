package com.rntech

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._

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
      "post-x-www-form-urlencoded-parameters")

  forAll(scenerios) { (scenarioName: String) =>

    it should s"build a valid canonical request for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)

      withFileContents(fileName = s"$filePath.req") { requestStr =>
        withFileContents(fileName = s"$filePath.creq") { expectedCanonical =>
          val request = parseRequest(requestStr)
          val canonicalRequest = RequestSigner.CanonicalRequestBuilder.buildCanonicalRequest(request)

          canonicalRequest shouldBe expectedCanonical
        }
      }

    }


    it should s"build the string to sign for $scenarioName" in {
      val filePath = resolveFilePathForScenario(scenarioName)

      withFileContents(fileName = s"$filePath.creq") { canonicalRequest =>
        withFileContents(fileName = s"$filePath.sts") { expectedStringToSign =>
          val stringToSign = RequestSigner.StringToSignBuilder.buildStringToSign(
            region = "eu-west-1",
            service = "execute-api",
            canonicalRequest = canonicalRequest.mkString("\n"))

          stringToSign shouldBe expectedStringToSign
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
    val firstLine = requestStr.head.split(" ")
    val (method, uri) = (firstLine(0), firstLine(1))

    val headers = requestStr.tail.map { header =>
      header.split(":") match {
        case Array(key, value) => (key, value)
      }
    }.foldLeft(Map.empty[String, List[String]]) { (acc, header) =>
      val (headerKey, headerValue) = header
      val currentValues = acc.getOrElse(headerKey, default = List.empty[String])
      acc + (headerKey -> (headerValue :: currentValues)) //groups duplicate keys
    } map {
      case (key, values) => Header(key, values)
    } toSeq

    Request(headers, None, method, uri)
  }


  def using[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A =
    try {
      f(closeable)
    } finally {
      closeable.close()
    }

  def withFileContents[T](fileName: String)(body: List[String] => T) = {
    using(Source.fromURL(getClass.getResource(s"/aws-sig-v4-test-suite/$fileName"))) { stream =>
      stream.getLines().toList
    }
  }
}


