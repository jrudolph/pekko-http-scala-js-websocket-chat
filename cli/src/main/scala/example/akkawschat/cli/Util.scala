package example.akkawschat.cli

import akka.stream.FlowShape
import akka.stream.scaladsl.{ Concat, Flow, GraphDSL, Source }

object Util {
  def inject[U](source: Source[U, Any]): Flow[U, U, Any] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val concat = b.add(new Concat[U](2))

      source ~> concat.in(0)

      FlowShape(concat.in(1), concat.out)
    })
}
