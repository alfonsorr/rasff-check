// Scala 3.1
import $ivy.`com.lihaoyi::requests:0.7.0`
import $ivy.`com.lihaoyi::ujson:1.5.0`

import scala.jdk.CollectionConverters._
import ujson._
import java.time.{Instant, LocalDate}
import java.time.format.DateTimeFormatter

val lastMonday =
  LocalDate.now().minusDays(LocalDate.now().getDayOfWeek.getValue - 1)
val previousMonday = lastMonday.minusDays(7)
val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
val formatterWithTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")

val rasff =
  "https://webgate.ec.europa.eu/rasff-window/backend/public/notification/search/consolidated/"

val rasffNotification =
  "https://webgate.ec.europa.eu/rasff-window/screen/notification/"

val jsonPayload = s"""{
    "parameters": {
        "pageNumber": 1,
        "itemsPerPage": 100
    },
    "ecValidDateFrom": "${formatter.format(previousMonday)} 00:00:00",
    "ecValidDateTo": "${formatter.format(lastMonday)} 00:00:00",
    "productCategory": [
        239,
        240,
        241,
        243,
        246,
        247,
        195,
        193
    ]
}"""

val session = requests.Session(
  headers = Map(
    // "Accept:" -> "application/json, text/plain, */*",
    "Content-Type" -> "application/json"
  ),
  cookieValues = Map("cookie" -> "vanilla")
)
val response = session.post(rasff, data = jsonPayload)
val list: List[Value] = ujson.read(response.text)("notifications").arr.toList

val productUrl =
  "https://webgate.ec.europa.eu/rasff-window/backend/public/notification/view/id/"

def formatElement(element: Value): String = {
  val product =
    requests.get(productUrl + element("notifId").num.toInt + "/").text
  val description = ujson.read(product)("product")("description").str.trim()
  s"""### ${element("ecValidationDate").str} [${element(
    "subject"
  ).str}](${rasffNotification}${element("notifId").num.toInt})
    |${description}""".stripMargin
}

def changeFormatDate(date: String): String =
  LocalDate
    .parse(date, formatterWithTime)
    .format(DateTimeFormatter.ISO_LOCAL_DATE)

val category: (String, List[Value]) => String = (category, elements) =>
  s"## ${category}\n" + elements
    .sortBy(x => changeFormatDate(x("ecValidationDate").str))
    .map(formatElement)
    .mkString("\n")

val grouped = list
  .groupBy(_("productCategory")("description").str)
  .toList
  .map(category.tupled)

println(
  "# RASFF notifications from " + formatter.format(previousMonday) + " to " + formatter.format(lastMonday
    .minusDays(1)) + "\n" + grouped.mkString("\n")
)
