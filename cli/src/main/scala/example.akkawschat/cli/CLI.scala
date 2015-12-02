package example.akkawschat.cli

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import akka.http.scaladsl.model.Uri
import akka.stream.stage.{ TerminationDirective, SyncDirective, Context, PushStage }

import shared.Protocol.ChatMessage

import scala.util.control.NonFatal

object CLI extends App {
  def promptForName(): String = {
    Console.out.print("What's your name? ")
    Console.out.flush()
    Console.in.readLine()
  }

  val endpointBase = "ws://localhost:8080/chat"
  val name = promptForName()

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
          super.onUpstreamFinish(ctx)
        }
      })

  println("Connecting... (Use Ctrl-D to exit.)")
  ChatClient.connect(endpoint, appFlow)
    .onFailure {
      case NonFatal(e) ⇒
        println(s"Connection to $endpoint failed because of '${e.getMessage}'")
        system.shutdown()
    }
}
