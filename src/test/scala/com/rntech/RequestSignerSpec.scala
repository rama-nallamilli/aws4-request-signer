package com.rntech


import java.net.{URI, URL}
import java.nio.file.Paths

import org.scalatest.FlatSpec
import org.scalatest.Matchers
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

    //    it should s"build the string to sign for $scenarioName" in {
    //      val filePath = resolveFilePathForScenario(scenarioName)
    //
    //      withFileContents(fileName = s"$filePath.creq") { canonicalRequest =>
    //        withFileContents(fileName = s"$filePath.sts") { expectedStringToSign =>
    //          val stringToSign = RequestSigner.StringToSignBuilder.buildStringToSign(
    //            region = "us-east-1",
    //            service = "execute-api",
    //            canonicalRequest = canonicalRequest.mkString("\n"),
    //            requestDate = ZonedDateTime.now())
    //
    //          stringToSign shouldBe expectedStringToSign
    //        }
    //      }
    //
    //    }
    //
    //
    //    it should s"calculate the signature for $scenarioName" in {
    //      val filePath = resolveFilePathForScenario(scenarioName)
    //      withFileContents(fileName = s"$filePath.req") { requestStr =>
    //        withFileContents(fileName = s"$filePath.sts") { stringToSign =>
    //          withFileContents(fileName = s"$filePath.authz") { expectedSignature =>
    //            val now = ZonedDateTime.now()
    //            val credentials = new BasicAWSCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
    //
    //            val requestHeaders = parseRequest(requestStr).headers
    //            //todo
    //            //AKIDEXAMPLE/20150830/us-east-1/service/aws4_request
    //
    //            val signature = RequestSigner.StringSigner.signStringWithS4(
    //              stringToSign = stringToSign.mkString,
    //              requestDate = now,
    //              credentials = credentials,
    //              service = "service",
    //              region = "us-east-1",
    //              headers = requestHeaders)
    //
    //
    //            signature shouldBe expectedSignature
    //          }
    //        }
    //      }
    //    }
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

  def parseAndGroupHeaders(headerLines: List[String]) = {
    headerLines.map { header =>
      header.split(":") match {
        case Array(key, value) => (key, value)
      }
    }.foldLeft(Map.empty[String, List[String]]) { (acc, header) =>
      val (headerKey, headerValue) = header
      val currentValues = acc.getOrElse(headerKey, default = List.empty[String])
      acc + (headerKey -> (currentValues :+ headerValue)) //groups multiple headers (of same type) into single entry
    } map {
      case (key, values) => Header(key, values)
    } toSeq
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


object mytest extends App {
  println("wtf")
//  Path("lol")
  val baseUri = new URI("//").normalize()
  println(baseUri.getPath)


}