package example.akkawschat.cli

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }

import akka.http.scaladsl.model.Uri

import shared.Protocol.ChatMessage

object CLI extends App {
  val name = "Jane Doe"
  val endpointBase = "ws://localhost:8080/chat"

  val endpoint = Uri(endpointBase).withQuery(Uri.Query("name" -> name))

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val messageIn: ChatMessage ⇒ Unit = { msg ⇒
    import Console._

    Console.println(s"[$YELLOW${msg.sender}$RESET]: ${msg.message}")
    Console.out.flush()
  }

  val inputLinesIterator: Iterator[String] =
    Iterator.continually {
      Console.print("> ")
      val res = Console.in.readLine()
      if (res == null) system.shutdown()
      res
    }.takeWhile(_ != null).filterNot(_.trim.isEmpty)

  val messageOut: Source[String, Unit] = Source(() ⇒ inputLinesIterator)

  ChatClient.connect(endpoint, messageIn, messageOut)
}
