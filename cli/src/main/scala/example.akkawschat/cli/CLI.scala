package example.akkawschat.cli

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import akka.http.scaladsl.model.Uri
import akka.stream.stage.{ TerminationDirective, SyncDirective, Context, PushStage }

import shared.Protocol.ChatMessage

import scala.util.control.NonFatal

object CLI extends App {
  val name = "Jane Doe"
  val endpointBase = "ws://localhost:8080/chat"

  val endpoint = Uri(endpointBase).withQuery(Uri.Query("name" -> name))

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  import Console._
  val appFlow =
    Flow[ChatMessage]
      .map(msg ⇒ s"$YELLOW${msg.sender}$RESET: ${msg.message}")
      .via(Prompt.prompt)
      .filterNot(_.trim.isEmpty)
      .transform(() ⇒ new PushStage[String, String] {
        def onPush(elem: String, ctx: Context[String]): SyncDirective = ctx.push(elem)

        override def onUpstreamFinish(ctx: Context[String]): TerminationDirective = {
          println("\nFinishing...")
          system.shutdown()
          Prompt.saneStty()
          super.onUpstreamFinish(ctx)
        }
      })

  ChatClient.connect(endpoint, appFlow)
    .onFailure {
      case NonFatal(e) ⇒
        println(s"Connection to $endpoint failed because of '${e.getMessage}'")
        system.shutdown()
    }
}
