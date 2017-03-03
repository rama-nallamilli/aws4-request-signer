package com.rntech

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.apache.commons.codec.binary.Hex
import org.apache.commons.lang3.StringUtils

case class Header(key: String, value: List[String])

case class QueryParam(name: String, value: String)

case class Request(headers: Seq[Header],
                   body: Option[String],
                   method: String,
                   uriPath: String,
                   queryParameters: Seq[QueryParam] = Seq.empty[QueryParam])

object RequestSigner {

  object CanonicalRequestBuilder {
    private val emptyBody = "".getBytes(StandardCharsets.UTF_8)

    private val headerToCanonicalString = (header: Header) =>
      s"${header.key.toLowerCase}:${StringUtils.normalizeSpace(header.value.mkString(","))}"

    def buildCanonicalRequest(request: Request): String = {

      val canonicalQueryParams = request.queryParameters.sortBy(_.name).map {
        case QueryParam(name, value) => s"${urlEncode(name)}=${urlEncode(value)}"
      }.mkString("&")

      val sortedHeaders = request.headers.sortBy(_.key)
      val canonicalHeaders = sortedHeaders.map(headerToCanonicalString).mkString("\n")
      val canonicalSignedHeaders = sortedHeaders.map(_.key.toLowerCase).mkString(";")

      val bodyBytes = request.body.map(_.getBytes).getOrElse(emptyBody)
      val hexEncodedPayloadHash = Hex.encodeHexString(sha256Hash(bodyBytes))

      val canonicalRequest = Seq(request.method, request.uriPath, canonicalQueryParams, canonicalHeaders, "",
        canonicalSignedHeaders, hexEncodedPayloadHash)

      canonicalRequest.mkString("\n")
    }
  }


  private def urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")

  private def sha256Hash(payload: Array[Byte]): Array[Byte] = {
    val md: MessageDigest = MessageDigest.getInstance("SHA-256")
    md.update(payload)
    md.digest
  }
}
