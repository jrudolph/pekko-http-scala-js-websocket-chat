package example.akkawschat.cli

import akka.stream.stage.{ OutHandler, GraphStageLogic, GraphStage }
import akka.stream._

import scala.annotation.tailrec
import scala.concurrent.{ Future, ExecutionContext }
import scala.util.control.NoStackTrace

class ConsoleInput(implicit ec: ExecutionContext) extends GraphStage[SourceShape[Char]] {
  val out = Outlet[Char]("consoleOut")
  val shape: SourceShape[Char] = SourceShape(out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      TTY.noEchoStty()

      @volatile var cancelled = false
      def getOne(): Unit = {
        val callback = getAsyncCallback[Char](push(out, _))

        Future {
          @tailrec def read(): Unit =
            if (cancelled) throw new Exception with NoStackTrace
            else if (System.in.available() > 0)
              callback.invoke(System.in.read().toChar)
            else {
              Thread.sleep(10)
              read()
            }

          read()
        }
      }

      setHandler(out, new OutHandler {
        def onPull(): Unit = getOne()

        override def onDownstreamFinish(): Unit = {
          cancelled = true
          super.onDownstreamFinish()
        }
      })

      override def postStop(): Unit =
        TTY.saneStty()
    }
}