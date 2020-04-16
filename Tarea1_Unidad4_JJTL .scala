// Databricks notebook source
//Objetivo: realizar análisis de flujo de clicks con datos recolectados de Wikipedia (Clickstream Analysis).
//Data: https://old.datahub.io/dataset/wikipedia-clickstream/resource/be85cc68-d1e6-4134-804a-fd36b94dbb82
//Requerimientos: Scala
//Este notebook está basado de: WikipediaClickStream desarrollado por Raazesh Sainudiin y Sivanand Sivaram.

//Convertir a parquet
val clickstream = sqlContext
    .read
    .format("csv")
    .options(Map("header" -> "true", "delimiter" -> "\t", "mode" -> "PERMISSIVE", "inferSchema" -> "true"))
    .load("dbfs:///databricks-datasets/wikipedia-datasets/data-001/clickstream/raw-uncompressed")


// COMMAND ----------

// Mostramos los primeros cinco registros
clickstream.show(5)

// COMMAND ----------

// Convertimos el DataFrame a un formato más eficiente para acelerar nuestro análisis
clickstream
  .write
  .mode(SaveMode.Overwrite)
  .parquet("/datasets/wiki-clickstream") // los warnings son inofensivos

// COMMAND ----------

//Paso 1: Carga de datos
val data = sc.textFile("//datasets/wiki-clickstream/part-00007-tid-7899525909358547766-ddd74921-2515-40b7-ab52-b1c576d254fd-1089-1-c000.snappy.parquet") //creamos un RDD


// COMMAND ----------

//Cargamos los datos a la variable clickstream

val clickstream = sqlContext.read.parquet("/datasets/wiki-clickstream/part-00007-tid-7899525909358547766-ddd74921-2515-40b7-ab52-b1c576d254fd-1089-1-c000.snappy.parquet")


// COMMAND ----------

//Visualizamos el esquema de los datos 'clickstream'
clickstream.printSchema

// COMMAND ----------

//Visualizamos algunos registros del dataframe
display(clickstream)

// COMMAND ----------

clickstream.show(5)//Otra forma de visualizar los registros del dataframe


// COMMAND ----------

//Vamos a visualizar cuántos registros existen en el dataframe
clickstream.count()


// COMMAND ----------

// Recordemos que:
//Definición de cada columna:
//prev_id: el ID de la página previa / origen del usuario
//curr_id: el ID de la página actual del usuario
//n: el número de pares de ocurrencias entre prev_id y curr_id
//prev_title: el resultado de mapear la URL de origen
//curr_title: el título del artículo que solicitó el cliente
//type: link, redlink y other.

// COMMAND ----------

// Esta consulta solo me sirve para ver qué consultas puedo hacer
//¿Cuáles fueron los principales 20 sitios de dónde se iniciaron las consultas a Wikipedia?
display(clickstream
      .select(clickstream("prev_title"), clickstream("n"))
      .groupBy("prev_title").sum()
      .orderBy($"sum(n)".desc)
      .limit(50))

// COMMAND ----------

// Esta consulta solo me sirve para ver qué consultas puedo hacer
//¿Cuáles fueron las 50 páginas más solicitadas?
display(clickstream
      .select(clickstream("curr_title"), clickstream("n"))
      .groupBy("curr_title").sum()
      .orderBy($"sum(n)".desc)
      .limit(100))

// COMMAND ----------

//creamos una tabla temporal
clickstream.createOrReplaceTempView("clicks_table")

// COMMAND ----------

// MAGIC %scala
// MAGIC package d3
// MAGIC  // We use a package object so that we can define top level classes like Edge that need to be used in other cells
// MAGIC //Este código fue desarrollado por "Michael Armbrust at Spark Summit East February 2016"
// MAGIC  
// MAGIC  import org.apache.spark.sql._
// MAGIC  import com.databricks.backend.daemon.driver.EnhancedRDDFunctions.displayHTML
// MAGIC  
// MAGIC  case class Edge(src: String, dest: String, count: Long)
// MAGIC  
// MAGIC  case class Node(name: String)
// MAGIC  case class Link(source: Int, target: Int, value: Long)
// MAGIC  case class Graph(nodes: Seq[Node], links: Seq[Link])
// MAGIC  
// MAGIC  object graphs {
// MAGIC  val sqlContext = SQLContext.getOrCreate(org.apache.spark.SparkContext.getOrCreate())  
// MAGIC  import sqlContext.implicits._
// MAGIC    
// MAGIC  def force(clicks: Dataset[Edge], height: Int = 100, width: Int = 960): Unit = {
// MAGIC    val data = clicks.collect()
// MAGIC    val nodes = (data.map(_.src) ++ data.map(_.dest)).map(_.replaceAll("_", " ")).toSet.toSeq.map(Node)
// MAGIC    val links = data.map { t =>
// MAGIC      Link(nodes.indexWhere(_.name == t.src.replaceAll("_", " ")), nodes.indexWhere(_.name == t.dest.replaceAll("_", " ")), t.count / 20 + 1)
// MAGIC    }
// MAGIC    showGraph(height, width, Seq(Graph(nodes, links)).toDF().toJSON.first())
// MAGIC  }
// MAGIC  
// MAGIC  /**
// MAGIC   * Displays a force directed graph using d3
// MAGIC   * input: {"nodes": [{"name": "..."}], "links": [{"source": 1, "target": 2, "value": 0}]}
// MAGIC   */
// MAGIC  def showGraph(height: Int, width: Int, graph: String): Unit = {
// MAGIC  
// MAGIC  displayHTML(s"""
// MAGIC  <!DOCTYPE html>
// MAGIC  <html>
// MAGIC  <head>
// MAGIC    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
// MAGIC    <title>Polish Books Themes - an Interactive Map</title>
// MAGIC    <meta charset="utf-8">
// MAGIC  <style>
// MAGIC  
// MAGIC  .node_circle {
// MAGIC    stroke: #777;
// MAGIC    stroke-width: 1.3px;
// MAGIC  }
// MAGIC  
// MAGIC  .node_label {
// MAGIC    pointer-events: none;
// MAGIC  }
// MAGIC  
// MAGIC  .link {
// MAGIC    stroke: #777;
// MAGIC    stroke-opacity: .2;
// MAGIC  }
// MAGIC  
// MAGIC  .node_count {
// MAGIC    stroke: #777;
// MAGIC    stroke-width: 1.0px;
// MAGIC    fill: #999;
// MAGIC  }
// MAGIC  
// MAGIC  text.legend {
// MAGIC    font-family: Verdana;
// MAGIC    font-size: 13px;
// MAGIC    fill: #000;
// MAGIC  }
// MAGIC  
// MAGIC  .node text {
// MAGIC    font-family: "Helvetica Neue","Helvetica","Arial",sans-serif;
// MAGIC    font-size: 17px;
// MAGIC    font-weight: 200;
// MAGIC  }
// MAGIC  
// MAGIC  </style>
// MAGIC  </head>
// MAGIC  
// MAGIC  <body>
// MAGIC  <script src="//d3js.org/d3.v3.min.js"></script>
// MAGIC  <script>
// MAGIC  
// MAGIC  var graph = $graph;
// MAGIC  
// MAGIC  var width = $width,
// MAGIC      height = $height;
// MAGIC  
// MAGIC  var color = d3.scale.category20();
// MAGIC  
// MAGIC  var force = d3.layout.force()
// MAGIC      .charge(-700)
// MAGIC      .linkDistance(180)
// MAGIC      .size([width, height]);
// MAGIC  
// MAGIC  var svg = d3.select("body").append("svg")
// MAGIC      .attr("width", width)
// MAGIC      .attr("height", height);
// MAGIC      
// MAGIC  force
// MAGIC      .nodes(graph.nodes)
// MAGIC      .links(graph.links)
// MAGIC      .start();
// MAGIC  
// MAGIC  var link = svg.selectAll(".link")
// MAGIC      .data(graph.links)
// MAGIC      .enter().append("line")
// MAGIC      .attr("class", "link")
// MAGIC      .style("stroke-width", function(d) { return Math.sqrt(d.value); });
// MAGIC  
// MAGIC  var node = svg.selectAll(".node")
// MAGIC      .data(graph.nodes)
// MAGIC      .enter().append("g")
// MAGIC      .attr("class", "node")
// MAGIC      .call(force.drag);
// MAGIC  
// MAGIC  node.append("circle")
// MAGIC      .attr("r", 10)
// MAGIC      .style("fill", function (d) {
// MAGIC      if (d.name.startsWith("other")) { return color(1); } else { return color(2); };
// MAGIC  })
// MAGIC  
// MAGIC  node.append("text")
// MAGIC        .attr("dx", 10)
// MAGIC        .attr("dy", ".35em")
// MAGIC        .text(function(d) { return d.name });
// MAGIC        
// MAGIC  //Now we are giving the SVGs co-ordinates - the force layout is generating the co-ordinates which this code is using to update the attributes of the SVG elements
// MAGIC  force.on("tick", function () {
// MAGIC      link.attr("x1", function (d) {
// MAGIC          return d.source.x;
// MAGIC      })
// MAGIC          .attr("y1", function (d) {
// MAGIC          return d.source.y;
// MAGIC      })
// MAGIC          .attr("x2", function (d) {
// MAGIC          return d.target.x;
// MAGIC      })
// MAGIC          .attr("y2", function (d) {
// MAGIC          return d.target.y;
// MAGIC      });
// MAGIC      d3.selectAll("circle").attr("cx", function (d) {
// MAGIC          return d.x;
// MAGIC      })
// MAGIC          .attr("cy", function (d) {
// MAGIC          return d.y;
// MAGIC      });
// MAGIC      d3.selectAll("text").attr("x", function (d) {
// MAGIC          return d.x;
// MAGIC      })
// MAGIC          .attr("y", function (d) {
// MAGIC          return d.y;
// MAGIC      });
// MAGIC  });
// MAGIC  </script>
// MAGIC  </html>
// MAGIC  """)
// MAGIC  }
// MAGIC    
// MAGIC    def help() = {
// MAGIC  displayHTML("""
// MAGIC  <p>
// MAGIC  Produces a force-directed graph given a collection of edges of the following form:</br>
// MAGIC  <tt><font color="#a71d5d">case class</font> <font color="#795da3">Edge</font>(<font color="#ed6a43">src</font>: <font color="#a71d5d">String</font>, <font color="#ed6a43">dest</font>: <font color="#a71d5d">String</font>, <font color="#ed6a43">count</font>: <font color="#a71d5d">Long</font>)</tt>
// MAGIC  </p>
// MAGIC  <p>Usage:<br/>
// MAGIC  <tt>%scala</tt></br>
// MAGIC  <tt><font color="#a71d5d">import</font> <font color="#ed6a43">d3._</font></tt><br/>
// MAGIC  <tt><font color="#795da3">graphs.force</font>(</br>
// MAGIC  &nbsp;&nbsp;<font color="#ed6a43">height</font> = <font color="#795da3">500</font>,<br/>
// MAGIC  &nbsp;&nbsp;<font color="#ed6a43">width</font> = <font color="#795da3">500</font>,<br/>
// MAGIC  &nbsp;&nbsp;<font color="#ed6a43">clicks</font>: <font color="#795da3">Dataset</font>[<font color="#795da3">Edge</font>])</tt>
// MAGIC  </p>""")
// MAGIC    }
// MAGIC }

// COMMAND ----------

//Consultamos los datos
//¿Cuáles fueron las principales tendencias en la teoría del big bang que arrojaron consultas en Wikipedia?
display(clickstream
        .select(clickstream("curr_title"), clickstream("prev_title"), clickstream("n"))
        .filter("prev_title = 'The_Big_Bang_Theory'")
        .groupBy("curr_title").sum()
        .orderBy($"sum(n)".desc)
        .limit(9))


// COMMAND ----------

// MAGIC %sql
// MAGIC --- Hacemos la misma consulta anterior en SQL
// MAGIC SELECT 
// MAGIC       curr_title,
// MAGIC       sum(n) AS count
// MAGIC     FROM clicks_table
// MAGIC     WHERE 
// MAGIC       prev_title = 'The_Big_Bang_Theory'
// MAGIC     GROUP BY curr_title
// MAGIC     ORDER BY count DESC
// MAGIC     LIMIT 9

// COMMAND ----------

// MAGIC %scala
// MAGIC import d3._
// MAGIC  
// MAGIC  graphs.force(height = 800,width = 1000,
// MAGIC               clicks = sql("""SELECT 
// MAGIC                                     prev_title AS src,
// MAGIC                                     curr_title AS dest,
// MAGIC                                     sum(n) AS count
// MAGIC                                   FROM clicks_table
// MAGIC                                   WHERE 
// MAGIC                                     prev_title = 'The_Big_Bang_Theory'
// MAGIC                                   GROUP BY prev_title, curr_title
// MAGIC                                   ORDER BY count DESC
// MAGIC                                   LIMIT 9""").as[Edge])

// COMMAND ----------

// MAGIC %sql
// MAGIC --- Páginas de cantantes
// MAGIC SELECT 
// MAGIC       prev_title,
// MAGIC       curr_title,
// MAGIC       n
// MAGIC     FROM clicks_table
// MAGIC     WHERE 
// MAGIC       curr_title IN ('Madonna_(entertainer)','Lady_Gaga') AND
// MAGIC       prev_id IS NOT NULL AND prev_title != 'Main_Page'
// MAGIC     ORDER BY n DESC
// MAGIC     LIMIT 20

// COMMAND ----------

// MAGIC %scala
// MAGIC import d3._
// MAGIC  
// MAGIC  graphs.force(height = 800,width = 1000,
// MAGIC               clicks = sql("""SELECT 
// MAGIC                                    prev_title AS src,
// MAGIC                                    curr_title AS dest,
// MAGIC                                    n AS count FROM clicks_table
// MAGIC                              WHERE 
// MAGIC                                    prev_title IN ('Madonna_(entertainer)','Lady_Gaga') AND
// MAGIC                                    curr_title IS NOT NULL AND NOT (curr_title = 'Main_Page' OR prev_title = 'Main_Page')
// MAGIC                              ORDER BY n DESC
// MAGIC                              LIMIT 20""").as[Edge])

// COMMAND ----------

// MAGIC %scala
// MAGIC // Página de Lionel Messi, Luis Suárez y los jugadores más caros del mundo
// MAGIC import d3._
// MAGIC  
// MAGIC  graphs.force(height = 800,width = 1000,
// MAGIC               clicks = sql("""SELECT 
// MAGIC                                    prev_title AS src,
// MAGIC                                    curr_title AS dest,
// MAGIC                                    n AS count FROM clicks_table
// MAGIC                              WHERE 
// MAGIC                                    curr_title IN ('List_of_most_expensive_association_football_transfers', 'Lionel_Messi', 'Luis_Suárez') AND
// MAGIC                                    prev_id IS NOT NULL AND NOT (curr_title = 'Main_Page' OR prev_title = 'Main_Page')
// MAGIC                              ORDER BY n DESC
// MAGIC                              LIMIT 30""").as[Edge])

// COMMAND ----------

// Ya que hice tres consultas visualizando grafos, a pesar de que una la hice de las tres formas (esa la considero dentro de las que se pedían como grafo), haré tres consultas visualizando la tabla, estas tres consultas las hago de las dos formas:

// COMMAND ----------

// MAGIC %sql
// MAGIC --- ¿Cuántas páginas de wikipedia hacen referencia a la página de Mexico?
// MAGIC SELECT *
// MAGIC   FROM clicks_table
// MAGIC   WHERE 
// MAGIC     curr_title = 'Mexico' AND
// MAGIC     prev_id IS NOT NULL AND prev_title != 'Main_Page'
// MAGIC   ORDER BY n DESC
// MAGIC   LIMIT 20

// COMMAND ----------

//Consultamos los datos
//¿Cuántas páginas de wikipedia hacen referencia a la página de México?
display(clickstream
        .select(clickstream("prev_id"),clickstream("curr_id"),clickstream("n"),clickstream("prev_title"),clickstream("curr_title"),clickstream("type"))
        .filter("curr_title = 'Mexico'").filter("prev_title != 'Main_Page'").filter("prev_id > 0")
        .orderBy($"n".desc)
        .limit(20))

// COMMAND ----------

// MAGIC %sql
// MAGIC --- ¿Cuáles fueron los principales 15 tendencias en Cristiano Ronaldo que arrojaron consultas en Wikipedia?
// MAGIC SELECT curr_title, sum(n)
// MAGIC   FROM clicks_table
// MAGIC   WHERE 
// MAGIC     prev_title = 'Cristiano_Ronaldo'
// MAGIC   GROUP BY curr_title
// MAGIC   ORDER BY sum(n) DESC
// MAGIC   LIMIT 15

// COMMAND ----------

display(clickstream
        .select(clickstream("curr_title"), clickstream("prev_title"), clickstream("n"))
        .filter("prev_title = 'Cristiano_Ronaldo'")
        .groupBy("curr_title").sum()
        .orderBy($"sum(n)".desc)
        .limit(15))

// COMMAND ----------

// MAGIC %sql
// MAGIC --- ¿Cuáles fueron los dos 'types' más solicitados?
// MAGIC SELECT type, sum(n)
// MAGIC   FROM clicks_table
// MAGIC   GROUP BY type
// MAGIC   ORDER BY sum(n) DESC
// MAGIC   LIMIT 2

// COMMAND ----------

display(clickstream
        .select(clickstream("type"), clickstream("n"))
        .groupBy("type").sum()
        .orderBy($"sum(n)".desc)
        .limit(2))
