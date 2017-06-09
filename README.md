# aws4-request-signer

[![Build Status](https://travis-ci.org/rama-nallamilli/aws4-request-signer.svg?branch=master)](https://travis-ci.org/rama-nallamilli/aws4-request-signer)

Scala library that handles authentication of AWS requests using the S4 Signing Process.

http://docs.aws.amazon.com/general/latest/gr/signature-version-4.html

## Usage

WIP - Adding to bintray

```scala
import com.rntech.RequestSigner

val signature = RequestSigner.sign(
    uriPath = "/status",
    method = "GET",
    body = Some("""{ "hello": "foo" }"""),
    headers = Seq(("Host", List("example.com"))),
    queryParameters = Seq.empty,
    credentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey),
    region = "eu-west-1",
    service = "execute-api")
```

Depending on your provider you can then set the appropriate headers using the returned Signature.
For example when using [Play WS](https://www.playframework.com/documentation/2.5.x/ScalaWS).

```scala
import java.net.URLDecoder

import com.amazonaws.auth.BasicAWSCredentials
import com.rntech.RequestSigner
import org.asynchttpclient.{Request, RequestBuilderBase, SignatureCalculator}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSResponse, WSSignatureCalculator}

import scala.concurrent.Future

object AwsSignatureCalculator extends WSSignatureCalculator with SignatureCalculator {

  import scala.collection.JavaConversions._

  private val awsAccessKey = sys.env.getOrElse("AWS_ACCESS_KEY_ID", ???)
  private val awsSecretKey = sys.env.getOrElse("AWS_SECRET_ACCESS_KEY", ???)

  override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {

    val requestHeadersToSign = request.getHeaders
    requestHeadersToSign.add("Host", request.getUri.getHost)

    val signature = RequestSigner.sign(
      uriPath = request.getUri.getPath,
      method = request.getMethod,
      body = Option(request.getByteData).map(data => new String(data)),
      headers = requestHeadersToSign.entries().map { h => h.getKey -> List(h.getValue) },
      queryParameters = request.getQueryParams.map { p => (p.getName, URLDecoder.decode(p.getValue, "UTF-8")) },
      credentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey),
      region = "eu-west-1",
      service = "execute-api")

    requestBuilder.setHeader("Authorization", signature.authorisationSignature)
    requestBuilder.setHeader("X-Amz-Date", signature.xAmzDate)
    requestBuilder.setHeader("Host", request.getUri.getHost)
    signature.xAmzSecurityToken.foreach(token => requestBuilder.setHeader("X-Amz-Security-Token", token))
  }
}

class MyExample(client: AhcWSClient) {

  def get(url: String): Future[WSResponse] =
    client
      .url(url)
      .sign(AwsSignatureCalculator)
      .get()
}
```

## Contributors

* [@rama-nallamilli](https://github.com/rama-nallamilli)
* [@xnejp03](https://github.com/xnejp03)
