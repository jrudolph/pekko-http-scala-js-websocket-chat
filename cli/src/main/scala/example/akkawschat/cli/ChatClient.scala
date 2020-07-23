package example.akkawschat.cli

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import shared.Protocol
import upickle.default._

import scala.concurrent.Future

object ChatClient {
  def connect[T](endpoint: Uri, handler: Flow[Protocol.Message, String, T])(implicit system: ActorSystem): Future[T] = {
    val wsFlow: Flow[Message, Message, T] =
      Flow[Message]
        .collect {
          case TextMessage.Strict(msg) ⇒ read[Protocol.Message](msg)
        }
        .viaMat(handler)(Keep.right)
        .map(TextMessage(_))

    val (fut, t) = Http().singleWebSocketRequest(WebSocketRequest(endpoint), wsFlow)
    fut.map {
      case v: ValidUpgrade                         ⇒ t
      case InvalidUpgradeResponse(response, cause) ⇒ throw new RuntimeException(s"Connection to chat at $endpoint failed with $cause")
    }(system.dispatcher)
  }

  def connect[T](endpoint: Uri, in: Sink[Protocol.Message, Any], out: Source[String, Any])(implicit system: ActorSystem): Future[Unit] =
    connect(endpoint, Flow.fromSinkAndSource(in, out)).map(_ ⇒ ())(system.dispatcher)

  def connect[T](endpoint: Uri, onMessage: Protocol.Message ⇒ Unit, out: Source[String, Any])(implicit system: ActorSystem): Future[Unit] =
    connect(endpoint, Sink.foreach(onMessage), out)
}