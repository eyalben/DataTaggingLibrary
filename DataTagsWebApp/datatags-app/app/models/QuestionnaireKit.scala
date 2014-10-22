package models

import play.api._
import java.nio.file._
import edu.harvard.iq.datatags.runtime._
import edu.harvard.iq.datatags.model.charts._
import edu.harvard.iq.datatags.parser.definitions.DataDefinitionParser
import edu.harvard.iq.datatags.parser.exceptions.DataTagsParseException
import edu.harvard.iq.datatags.parser.flowcharts._
import edu.harvard.iq.datatags.model.charts._
import edu.harvard.iq.datatags.model.values._
import edu.harvard.iq.datatags.model.types._
import views._

case class QuestionnaireKit( val id:String,
                             val title: String,
                             val tags: CompoundType,
                             val questionnaire: FlowChartSet,
                             val serializer: Serialization )

object QuestionnaireKits {
  val allKits = loadQuestionnaires()
  
  /** This will go away once we have multi questionnaire support */
  val kit = allKits.toArray.apply(0)._2

  private def loadQuestionnaires() = {
    Logger.info("DataTags application started")
    Play.current.configuration.getString("datatags.folder") match {
    case Some(str) => {
          val p = Paths.get(str)
          Logger.info( "Loading questionnaire data from " + p.toAbsolutePath.toString )

          val dp = new DataDefinitionParser()
          val dataTags = dp.parseTagDefinitions( readAll(p.resolve("definitions.tags")), "definitions").asInstanceOf[CompoundType]
          val fcsParser = new FlowChartSetComplier( dataTags )

          val source = readAll( p.resolve("questionnaire.flow") )

          val interview = fcsParser.parse(source, "Data Deposit Screening" )
          Logger.info("Default chart id: %s".format(interview.getDefaultChartId) )

          // var for answers and their frequencies
          var answerFrequencies: scala.collection.mutable.Map[Answer, Integer] = scala.collection.mutable.Map()
          var answerMap: Map[Answer, String] = Map()
          
          // find all answers and their frequencies
          val interviewItr = interview.charts.iterator
          while (interviewItr.hasNext) { // while there are charts remaining
            val chartItr = interviewItr.next.nodes.iterator
            while(chartItr.hasNext) { // while there are nodes in the chart remaining
              answerFrequencies = matchNode(chartItr.next, answerFrequencies)
            }
          }

          // create map from Answers to short string
          var serializedAns = 0 // serialized value of each ans
          for (ans <- answerFrequencies.keys) {
              if (!answerMap.contains(ans)) {
                  answerMap = answerMap.updated(ans, serializedAns.toString)
                  serializedAns = serializedAns + 1
              }
          }

          // serialization object
          val serializer = Serialization.create( answerMap )

          Map( "dds-c1" -> QuestionnaireKit("dds-c1", "Data Deposit Screening", dataTags, interview, serializer) )
        }

        case None => {
          Logger.error("Bad configuration: Can't find \"datatags.folder\"")
          Map[String, QuestionnaireKit]()
        }
    }
  }

  private def readAll( p:Path ) : String = scala.io.Source.fromFile( p.toFile ).getLines().mkString("\n")


  private def matchNode(aNode: nodes.Node, answerFrequencies: scala.collection.mutable.Map[Answer, Integer]): scala.collection.mutable.Map[Answer, Integer] = aNode match {

    case n:nodes.AskNode => { // if node is AskNode, update frequency of answer
      val answerItr = n.getAnswers.iterator
      while (answerItr.hasNext) { // while answers remain in the node
        val nextAns= answerItr.next
        if (answerFrequencies.contains(nextAns)) { // if answer is already listed, update number
          val frequency = answerFrequencies(nextAns)
          answerFrequencies.update(nextAns, frequency+1)
        } else { // if not, insert answer
          answerFrequencies.put(nextAns,1)
        }
      }
      answerFrequencies
     }

    case _ => { // if node is any other node, simply return the current answer frequency map
      answerFrequencies
    }
  }

}

