package example.akkawschat.cli

import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.tailrec
import scala.collection.immutable

import akka.stream._
import akka.stream.scaladsl.{ Flow, Source, FlowGraph }
import akka.stream.stage.{ OutHandler, InHandler, GraphStageLogic, GraphStage }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NoStackTrace

object Prompt {
  import sys.process._

  /**
   * A flow that prompts for lines from the tty and allows to output lines at the same time, without
   * disrupting user input
   */
  def prompt(implicit ec: ExecutionContext): Flow[String, String, Unit] = {
    val characters = Source.fromGraph(new ConsoleInput)

    val graph =
      FlowGraph.create() { implicit b ⇒
        import FlowGraph.Implicits._

        val prompt = b.add(PromptFlow)
        characters ~> prompt.characterInput

        FlowShape(prompt.outputLines, prompt.readLines)
      }

    Flow.fromGraph(graph)
  }

  def noEchoStty() = "stty -F /dev/tty -echo -icanon min 1 -icrnl -inlcr".!!
  def saneStty() = "stty -F /dev/tty sane".!!
}

case class PromptFlowShape(characterInput: Inlet[Char], outputLines: Inlet[String], readLines: Outlet[String]) extends Shape {
  def inlets = Vector(characterInput, outputLines)
  def outlets = Vector(readLines)
  def deepCopy(): Shape = PromptFlowShape(characterInput.carbonCopy(), outputLines.carbonCopy(), readLines.carbonCopy())
  def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape =
    PromptFlowShape(inlets(0).asInstanceOf[Inlet[Char]], inlets(1).asInstanceOf[Inlet[String]], outlets(0).asInstanceOf[Outlet[String]])
}

object PromptFlow extends GraphStage[PromptFlowShape] {
  val characterInput = Inlet[Char]("characterInput")
  val outputLinesIn = Inlet[String]("outputLinesIn")
  val readLinesOut = Outlet[String]("readLinesOut")

  val shape = PromptFlowShape(characterInput, outputLinesIn, readLinesOut)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var collectedString: String = ""

      setHandler(characterInput, new InHandler {
        def onPush(): Unit = {
          val res = grab(characterInput)
          res match {
            case 4 /* Ctrl-D */ ⇒
              println()
              complete(readLinesOut)
              completeStage()
            case '\r' ⇒
              push(readLinesOut, collectedString)
              collectedString = ""
              prompt()
            case 127 /* backspace */ ⇒
              collectedString = collectedString.dropRight(1)
              prompt()
            case x ⇒
              //println(s"Got ${x.toInt}")
              collectedString += x
              print(x)
              pull(characterInput)
          }
        }
      })
      setHandler(outputLinesIn, new InHandler {
        def onPush(): Unit = {
          print(s"$RESTORE$ERASE_LINE${grab(outputLinesIn)}\n$SAVE$promptLine")
          pull(outputLinesIn)
        }
      })
      setHandler(readLinesOut, eagerTerminateOutput)

      override def preStart(): Unit = {
        pull(outputLinesIn)
        print(SAVE) // to make sure we don't jump back to former SAVE position in the terminal
        prompt()
      }

      def promptLine = s"$RESTORE$ERASE_LINE$SAVE> $collectedString"
      def prompt(): Unit = {
        print(promptLine)
        pull(characterInput)
      }
    }

  val ANSI_ESCAPE = "\u001b["
  val SAVE = ANSI_ESCAPE + "s"
  val RESTORE = ANSI_ESCAPE + "u"
  val ERASE_LINE = ANSI_ESCAPE + "K"
}

class ConsoleInput(implicit ec: ExecutionContext) extends GraphStage[SourceShape[Char]] {
  val out = Outlet[Char]("consoleOut")
  val shape: SourceShape[Char] = SourceShape(out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      Prompt.noEchoStty()

      @volatile var cancelled = false
      val outstanding = new AtomicInteger(0)
      def getOne(): Unit = {
        val callback = getAsyncCallback[Char](push(out, _))

        Future {
          @tailrec def read(): Unit =
            if (cancelled) throw new Exception with NoStackTrace
            else if (System.in.available() > 0)
              callback.invoke(System.in.read().toChar)
            else {
              Thread.sleep(0)
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
        Prompt.saneStty()
    }
}