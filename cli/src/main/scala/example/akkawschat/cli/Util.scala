package example.akkawschat.cli

import akka.stream.FlowShape
import akka.stream.scaladsl.{ Concat, FlowGraph, Flow, Source }

object Util {
  def inject[U](source: Source[U, Unit]): Flow[U, U, Unit] =
    Flow.fromGraph(FlowGraph.create() { implicit b ⇒
      import FlowGraph.Implicits._

      val concat = b.add(new Concat[U](2))

      source ~> concat.in(0)

      FlowShape(concat.in(1), concat.out)
    })
}
