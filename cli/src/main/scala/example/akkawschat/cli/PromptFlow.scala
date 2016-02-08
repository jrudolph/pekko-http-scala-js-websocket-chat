package example.akkawschat.cli

import scala.collection.immutable

import akka.stream._
import akka.stream.scaladsl.{ Flow, Source, GraphDSL }
import akka.stream.stage.{ InHandler, GraphStageLogic, GraphStage }

import scala.concurrent.ExecutionContext

object Prompt {
  /**
   * A flow that prompts for lines from the tty and allows to output lines at the same time, without
   * disrupting user input
   */
  def prompt(implicit ec: ExecutionContext): Flow[String, String, Any] = {
    val characters = Source.fromGraph(new ConsoleInput)

    val graph =
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val prompt = b.add(PromptFlow)
        characters ~> prompt.characterInput

        FlowShape(prompt.outputLines, prompt.readLines)
      }

    Flow.fromGraph(graph)
  }
}

case class PromptFlowShape(characterInput: Inlet[Char], outputLines: Inlet[String], readLines: Outlet[String]) extends Shape {
  def inlets = Vector(characterInput, outputLines)
  def outlets = Vector(readLines)
  def deepCopy(): Shape = PromptFlowShape(characterInput.carbonCopy(), outputLines.carbonCopy(), readLines.carbonCopy())
  def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape =
    PromptFlowShape(inlets(0).asInstanceOf[Inlet[Char]], inlets(1).asInstanceOf[Inlet[String]], outlets(0).asInstanceOf[Outlet[String]])
}

object PromptFlow extends GraphStage[PromptFlowShape] {
  import TTY._

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
}

