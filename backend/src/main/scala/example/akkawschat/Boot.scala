package example.akkawschat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorFlowMaterializer

object Boot extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val service = new Webservice

  val binding = Http().bindAndHandle(service.route, interface, port)
  binding.onFailure {
    case e ⇒
      println(s"Binding failed with ${e.getMessage}")
      system.shutdown()
  }
}
