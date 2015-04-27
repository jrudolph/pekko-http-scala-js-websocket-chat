package example.akkawschat

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{ Message, TextMessage }

import scala.concurrent.duration._

import akka.http.scaladsl.server.Directives
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source, Flow }

class Webservice(implicit fm: FlowMaterializer, system: ActorSystem) extends Directives {
  val theChat = Chat.create(system)
  import system.dispatcher
  system.scheduler.schedule(15.second, 15.second) {
    theChat.injectMessage(ChatMessage(sender = "clock", s"Bling! The time is ${new Date().toString}."))
  }

  def route =
    get {
      pathSingleSlash {
        getFromResource("web/index.html")
      } ~
        // Scala-JS puts them in the route per default, so that's where we pick them up
        path("frontend-launcher.js")(getFromResource("frontend-launcher.js")) ~
        path("frontend-fastopt.js")(getFromResource("frontend-fastopt.js")) ~
        path("clock")(handleWebsocketMessages(clockFlow)) ~
        path("chat") {
          parameter('name) { name ⇒
            handleWebsocketMessages(websocketChatFlow(sender = name))
          }
        }
    } ~
      getFromResourceDirectory("web")

  def clockFlow: Flow[Message, Message, Unit] =
    Flow.wrap(
      Sink.ignore,
      clockTickSource.map(TextMessage.Strict))((_, _) ⇒ ())

  def clockTickSource: Source[String, Any] =
    Source(1.second, 1.second, "tick").map(_ ⇒ s"Bling! The time is ${new Date().toString}.")

  def websocketChatFlow(sender: String): Flow[Message, Message, Unit] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg
      }
      .via(theChat.chatFlow(sender))
      .map {
        case ChatMessage(sender, message) ⇒ TextMessage.Strict(s"$sender: $message")
      }
}
