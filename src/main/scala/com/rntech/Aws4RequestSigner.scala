//package com.rntech
//
//import java.net.URLEncoder
//import java.nio.charset.StandardCharsets
//import java.security.MessageDigest
//import java.time.format.DateTimeFormatter
//import java.time.{LocalDateTime, ZoneId}
//import javax.crypto.Mac
//import javax.crypto.spec.SecretKeySpec
//
//import com.amazonaws.auth.{AWSCredentials, AWSSessionCredentials, DefaultAWSCredentialsProviderChain}
//import org.apache.commons.codec.binary.Hex
//import org.apache.commons.lang3.StringUtils
//import org.apache.http.HttpHeaders
//import org.asynchttpclient.{Request, RequestBuilderBase, SignatureCalculator}
//
//import scala.collection.JavaConverters._
//
//object RamaAwsSignatureCalculator extends SignatureCalculator {
//  val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
//
//  val credentials = new DefaultAWSCredentialsProviderChain().getCredentials
//  val region = "eu-west-1"
//  val service = "execute-api"
//
//  //TODO abstract away from play
//  override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
//
//    val requestDate = LocalDateTime.now(ZoneId.of("UTC"))
//
//    if (!request.getHeaders.contains("DATE"))
//      request.getHeaders.add("X-Amz-Date", requestDate.format(DATE_FORMATTER))
//    request.getHeaders.add("Host", request.getUri.getHost)
//
//    credentials match {
//      case creds: AWSSessionCredentials => request.getHeaders.add("X-Amz-Security-Token", creds.getSessionToken)
//      case _ =>
//    }
//
//    val sortedHeadersByKey = request.getHeaders.entries().asScala.sortBy(_.getKey)
//    val canonicalHeaders = sortedHeadersByKey.map(e => s"${e.getKey.toLowerCase}:${StringUtils.normalizeSpace(e.getValue)}").mkString("\n")
//    val canonicalSignedHeaders = sortedHeadersByKey.map(e => e.getKey.toLowerCase).mkString(";")
//
//    val queryParams = request.getQueryParams.asScala.sortBy(_.getName).map { param =>
//      s"${urlEncode(param.getName)}=${urlEncode(param.getValue)}"
//    }.mkString("&")
//
//    val bodyBytes = Option(request.getByteData).getOrElse("".getBytes(StandardCharsets.UTF_8))
//    val hexEncodedPayloadHash = Hex.encodeHexString(sha256Hash(bodyBytes))
//
//    val canonicalRequest = Seq(request.getMethod, request.getUri.getPath, queryParams, canonicalHeaders, "",
//      canonicalSignedHeaders, hexEncodedPayloadHash).mkString("\n")
//
//    val hexEncodedCanonicalRequestHash = Hex.encodeHexString(sha256Hash(canonicalRequest.getBytes))
//    val credentialsScope = s"${requestDate.format(DateTimeFormatter.BASIC_ISO_DATE)}/$region/$service/aws4_request"
//
//    val stringToSign = Seq(
//      "AWS4-HMAC-SHA256",
//      requestDate.format(DATE_FORMATTER),
//      credentialsScope,
//      hexEncodedCanonicalRequestHash)
//      .mkString("\n")
//
//    val signature = encryptWithHmac256(stringToSign, requestDate, credentials)
//    val authorizationHeader = s"AWS4-HMAC-SHA256 Credential=${credentials.getAWSAccessKeyId}/$credentialsScope, " +
//      s"SignedHeaders=$canonicalSignedHeaders, Signature=$signature"
//
//    request.getHeaders.add(HttpHeaders.AUTHORIZATION, authorizationHeader)
//    requestBuilder.setHeaders(request.getHeaders)
//  }
//
//  private def urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")
//
//  private def sha256Hash(payload: Array[Byte]): Array[Byte] = {
//    val md: MessageDigest = MessageDigest.getInstance("SHA-256")
//    md.update(payload)
//    md.digest
//  }
//
//  private def encryptWithHmac256(stringToSign: String, now: LocalDateTime, credentials: AWSCredentials): String = {
//    val hmacSha256 = "HmacSHA256"
//
//    def encrypt(data: String, key: Array[Byte]): Array[Byte] = {
//      val mac = Mac.getInstance(hmacSha256)
//      mac.init(new SecretKeySpec(key, hmacSha256))
//      mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
//    }
//
//    def getSignatureKey(now: LocalDateTime, credentials: AWSCredentials): Array[Byte] = {
//      val kSecret = s"AWS4${credentials.getAWSSecretKey}".getBytes(StandardCharsets.UTF_8)
//
//      Seq(now.format(DateTimeFormatter.BASIC_ISO_DATE), region, service, "aws4_request").foldLeft(kSecret) {
//        (acc, value) => encrypt(value, acc)
//      }
//    }
//
//    Hex.encodeHexString(encrypt(stringToSign, getSignatureKey(now, credentials)))
//  }
//}