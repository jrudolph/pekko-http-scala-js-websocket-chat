package example.akkawschat

import akka.actor.ActorSystem

import akka.http.scaladsl.server.Directives
import akka.stream.FlowMaterializer

class Webservice(implicit fm: FlowMaterializer, system: ActorSystem) extends Directives {
  import system.dispatcher

  def route =
    get {
      pathSingleSlash {
        getFromResource("web/index.html")
      } ~
        // Scala-JS puts them in the root of the resource directory per default,
        // so that's where we pick them up
        path("frontend-launcher.js")(getFromResource("frontend-launcher.js")) ~
        path("frontend-fastopt.js")(getFromResource("frontend-fastopt.js"))
    } ~ getFromResourceDirectory("web")
}
