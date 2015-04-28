package example.akkawschat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorFlowMaterializer

object Boot extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  val service = new Webservice

  val binding = Http().bindAndHandle(service.route, "0.0.0.0", 8080)
  binding.onFailure {
    case e ⇒
      println(s"Binding failed with ${e.getMessage}")
      system.shutdown()
  }
}
