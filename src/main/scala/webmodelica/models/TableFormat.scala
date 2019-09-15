package webmodelica.models

import mope.responses.SimulationResult
import config.MaxSimulationData
import io.circe.generic.JsonCodec

@JsonCodec
case class TableFormat(
  modelName: String,
  data: Seq[Seq[Double]],
  header: Seq[String],
  dataManipulated: Option[String]
)

object TableFormat {
  def apply(maxSimulationData:MaxSimulationData, result:SimulationResult): TableFormat = {
    val originalVariablesSize = result.variables.values.head.size
    val variables = result.trimmedVariables(maxSimulationData.value)
    val headers = variables.keys.filterNot(k => k=="time").toList
    val tableData = (variables("time")+:headers.map(k => variables(k)).toSeq).transpose
    TableFormat(
      result.modelName,
      tableData,
      "time"::headers,
      if(originalVariablesSize>maxSimulationData.value)
        Some(s"The plot doesn't contain all variables ($originalVariablesSize). Variables stripped to ${variables.values.head.size}. ")
      else
        None
    )
  }
}
