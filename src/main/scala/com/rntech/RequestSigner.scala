package com.rntech

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.amazonaws.auth.AWSCredentials
import org.apache.commons.codec.binary.Hex
import org.apache.commons.lang3.StringUtils

case class Header(key: String, values: List[String])

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
      s"${header.key.toLowerCase}:${StringUtils.normalizeSpace(header.values.mkString(","))}"

    def buildCanonicalRequest(request: Request): String = {

      val canonicalQueryParams = request.queryParameters.sortBy(_.name).map {
        case QueryParam(name, value) => s"${specialUrlEncode(name)}=${specialUrlEncode(value)}"
      }.mkString("&")

      val sortedHeaders = request.headers.sortBy(_.key)
      val canonicalHeaders = sortedHeaders.map(headerToCanonicalString).mkString("\n")
      val canonicalSignedHeaders = sortedHeaders.map(_.key.toLowerCase).mkString(";")

      val bodyBytes = request.body.map(_.getBytes).getOrElse(emptyBody)
      val hexEncodedPayloadHash = Hex.encodeHexString(sha256Hash(bodyBytes))

      val normalisedPath = new URI(request.uriPath).normalize().getPath

      val canonicalRequest = Seq(request.method, s"/${specialUrlEncode(normalisedPath.stripPrefix("/"))}", canonicalQueryParams, canonicalHeaders, "",
        canonicalSignedHeaders, hexEncodedPayloadHash)

      canonicalRequest.mkString("\n")
    }

    private def specialUrlEncode(str: String) = {
      urlEncode(str)
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")
    }
  }

  object StringToSignBuilder {
    val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    def buildStringToSign(region: String, service: String, canonicalRequest: String, requestDate: ZonedDateTime) = {

      val credentialsScope = s"${requestDate.format(DateTimeFormatter.BASIC_ISO_DATE)}/$region/$service/aws4_request"

      val hexEncodedCanonicalRequestHash = Hex.encodeHexString(sha256Hash(canonicalRequest.getBytes))
      val stringToSign = Seq(
        "AWS4-HMAC-SHA256",
        requestDate.format(DATE_FORMATTER),
        credentialsScope,
        hexEncodedCanonicalRequestHash)
        .mkString("\n")

      stringToSign
    }
  }

  object StringSigner {
    def signStringWithS4(stringToSign: String,
                         requestDate: ZonedDateTime,
                         credentials: AWSCredentials,
                         service: String,
                         region: String,
                         headers: Seq[Header]) = {
      val credentialsScope = s"${requestDate.format(DateTimeFormatter.BASIC_ISO_DATE)}/$region/$service/aws4_request"
      val canonicalSignedHeaders = headers.sortBy(_.key).map(e => e.key.toLowerCase).mkString(";")
      val signature = encryptWithHmac256(stringToSign, requestDate, credentials, region, service)
      s"AWS4-HMAC-SHA256 Credential=${credentials.getAWSAccessKeyId}/$credentialsScope, SignedHeaders=$canonicalSignedHeaders, Signature=$signature"
    }

    private def encryptWithHmac256(stringToSign: String,
                                   requestDate: ZonedDateTime,
                                   credentials: AWSCredentials,
                                   region: String,
                                   service: String): String = {

      def encrypt(data: String, key: Array[Byte]): Array[Byte] = {
        val hmacSha256 = "HmacSHA256"
        val mac = Mac.getInstance(hmacSha256)
        mac.init(new SecretKeySpec(key, hmacSha256))
        mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
      }

      def getSignatureKey(now: ZonedDateTime, credentials: AWSCredentials): Array[Byte] = {
        val kSecret = s"AWS4${credentials.getAWSSecretKey}".getBytes(StandardCharsets.UTF_8)

        Seq(now.format(DateTimeFormatter.BASIC_ISO_DATE), region, service, "aws4_request").foldLeft(kSecret) {
          (acc, value) => encrypt(value, acc)
        }
      }

      Hex.encodeHexString(encrypt(stringToSign, getSignatureKey(requestDate, credentials)))
    }
  }


  private def urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")

  private def sha256Hash(payload: Array[Byte]): Array[Byte] = {
    val md: MessageDigest = MessageDigest.getInstance("SHA-256")
    md.update(payload)
    md.digest
  }
}
