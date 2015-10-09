package com.collective.utils

import java.util.concurrent.Executors
import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.client.pipelining._
import com.pragmasoft.reactive.throttling.http.client.HttpClientThrottling._
import com.pragmasoft.reactive.throttling.threshold._
import spray.http._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
 * Created by anand on 12/11/14.
 */
object HttpUtils extends Logging {

  val appnexusHost = ServicesConfig.appnexusConfig("appnexus.host")

  implicit val system = ActorSystem("HttpClient")
  system.dispatcher

  implicit val timeout: Timeout = Timeout(1200.second)
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))

  val pipeline: Future[SendReceive] = {
      log.info("appnexushost = " + appnexusHost)
      for {
        Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup(appnexusHost)
      } yield sendReceive(throttleFrequencyWithTransport(55 perMinute, connector))
  }

  def post(requestRelativeUri: String, jsonPayLoad: String) : Future[HttpResponse] = {

    val uri: Uri = Uri(requestRelativeUri)
    val request: HttpRequest = Post(uri, jsonPayLoad)
    //Await.result(pipeline.flatMap { client => client.apply(request)}, 800 seconds)
    pipeline.flatMap { client => client.apply(request)}
  }

  def put(requestRelativeUri: String, queryParams: Map[String, String] = Map(), httpHeaders: List[HttpHeader] = Nil, jsonPayLoad: String) : Future[HttpResponse] = {

    val uri: Uri = Uri(requestRelativeUri).withQuery(queryParams)
    val request: HttpRequest = Put(uri, jsonPayLoad).withHeaders(httpHeaders)
    pipeline.flatMap { client => client.apply(request)}
  }

  def get(requestRelativeUri: String, queryParams: Map[String, String] = Map(), httpHeaders: List[HttpHeader] = Nil) : Future[HttpResponse] = {

    val uri: Uri = Uri(requestRelativeUri).withQuery(queryParams)
    val request: HttpRequest = Get(uri).withHeaders(httpHeaders)
    //Await.result(pipeline.flatMap { client => client.apply(request)}, 800 seconds)
    pipeline.flatMap { client => client.apply(request)}
  }

}
