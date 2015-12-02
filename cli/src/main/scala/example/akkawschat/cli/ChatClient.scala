package example.akkawschat.cli

import akka.stream.Materializer

import scala.concurrent.Future

import akka.actor.ActorSystem

import akka.stream.scaladsl.{ Keep, Source, Sink, Flow }

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws._

import upickle._

import shared.Protocol.ChatMessage

object ChatClient {
  def connect[T](endpoint: Uri, handler: Flow[ChatMessage, String, T])(implicit system: ActorSystem, materializer: Materializer): Future[T] = {
    val wsFlow: Flow[Message, Message, T] =
      Flow[Message]
        .collect {
          case TextMessage.Strict(msg) ⇒ read[ChatMessage](msg)
        }
        .viaMat(handler)(Keep.right)
        .map(TextMessage(_))

    val (fut, t) = Http().singleWebsocketRequest(WebsocketRequest(endpoint), wsFlow)
    fut.map {
      case v: ValidUpgrade                         ⇒ t
      case InvalidUpgradeResponse(response, cause) ⇒ throw new RuntimeException(s"Connection to chat at $endpoint failed with $cause")
    }(system.dispatcher)
  }

  def connect[T](endpoint: Uri, in: Sink[ChatMessage, Any], out: Source[String, Any])(implicit system: ActorSystem, materializer: Materializer): Future[Unit] =
    connect(endpoint, Flow.fromSinkAndSource(in, out)).map(_ ⇒ ())(system.dispatcher)

  def connect[T](endpoint: Uri, onMessage: ChatMessage ⇒ Unit, out: Source[String, Any])(implicit system: ActorSystem, materializer: Materializer): Future[Unit] =
    connect(endpoint, Sink.foreach(onMessage), out)
}