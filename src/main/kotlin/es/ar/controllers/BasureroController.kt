package es.ar.controllers

import es.ar.mappers.ContenedoresMapper
import es.ar.mappers.ContenedoresMapper.contenedoresToContenedoresDTO
import es.ar.mappers.ContenedoresMapper.dtoToContenedores
import es.ar.mappers.ContenedoresMapper.mapToContenedor
import es.ar.mappers.ResiduosMapper
import es.ar.mappers.ResiduosMapper.dtoToResiduos
import es.ar.mappers.ResiduosMapper.mapToResiduo
import es.ar.mappers.ResiduosMapper.residuosToResiduosDTO
import es.ar.models.Contenedores
import es.ar.models.Residuos
import es.ar.utils.esCSVContenedores
import es.ar.utils.esCSVResiduos
import es.ar.utils.validarDirectorio
import es.ar.utils.validarExtension
import jetbrains.datalore.base.values.Color
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.html
import org.jetbrains.letsPlot.Stat.identity
import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomBar
import org.jetbrains.letsPlot.geom.geomPoint
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.letsPlot
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.exists

/**
 * Basurero Controller
 * @author Alejandro Lopez Abad y Ruben Garcia-Redondo Marin
 *
 *
 */
class BasureroController {


    /**
     * Función que parsea los archivos csv en csv, json y xml
     *
     * @param pathOrigen Lugar de los ficheros a parsear
     * @param pathFinal Lugar donde se parsearan los ficheros
*/
    fun programaParser(pathOrigen: String, pathFinal: String) {
        if ( esCSVContenedores(pathOrigen) && esCSVResiduos(pathOrigen)) {
            val listaResiduos = ResiduosMapper.csvReaderToResiduo( pathOrigen + File.separator + "modelo_residuos_2021.csv" )
            val listaResiduosDTO = listaResiduos.map { it.residuosToResiduosDTO() }
            ResiduosMapper.residuoToCSV(pathFinal, listaResiduosDTO)
            ResiduosMapper.residuoToJson(pathFinal, listaResiduosDTO)
            ResiduosMapper.residuoToXML(pathFinal, listaResiduosDTO)
            val listaContenedores =
                ContenedoresMapper.csvReaderToContenedores(pathOrigen + File.separator + "contenedores_varios.csv")
            val listaContenedoresDTO = listaContenedores.map { it.contenedoresToContenedoresDTO() }
            ContenedoresMapper.contenedorToCSV(pathFinal, listaContenedoresDTO)
            ContenedoresMapper.contenedorToJson(pathFinal, listaContenedoresDTO)
            ContenedoresMapper.contenedorToXML(pathFinal, listaContenedoresDTO)
        } else {
            throw Exception("Archivo no válido")
        }
    }


    /**
     *  Metodo que se encarga de ejecutar la primera parte de la segunda consulta del ejercicio que se trata de RESUMEN
     * en la cual tenemos que coger la informacion de los contenedores y de la recodiga, independientemente de la extensión que tenga
     * y la procesamos generando.
     * Este metodo hara todas las consultas que necesitamos para mas tarde generar las graficas y el resumen.html
     *
     * @param pathOrigen Es el directorio origen
     * @param pathFinal Es el directorio donde se guarda los datos conseguidos con el metodo
     */
    fun programaResumen(pathOrigen: String, pathFinal: String) {
        val pathResiduos =  pathOrigen + File.separator + "modelo_residuos_2021.csv"
        val pathContenedores =  pathOrigen + File.separator + "contenedores_varios.csv"
        if(validarExtension(pathResiduos) && validarExtension(pathContenedores)){
            val tiempoInicial = System.currentTimeMillis()
            val listaResiduos = parserFicherosResiduos(pathOrigen)
            val listaContenedores = parserFicherosContenedores(pathOrigen)
            // Número de contenedores de cada tipo que hay en cada distrito.
            numContenedoresByTipoByDistrito(listaContenedores)
            // Media de contenedores de cada tipo que hay en cada distrito.
            mediaContenedoresByTipoByDistrito(listaContenedores)
            //Media de toneladas anuales de recogidas por cada tipo de basura agrupadas por distrito.
            mediaToneladasByTipoByDistrito(listaResiduos)
            //Gráfico de media de toneladas mensuales de recogida de basura por distrito.
            graficoMediaToneladasDistrito(listaResiduos, pathFinal)
            //Máximo, mínimo, media y desviación de toneladas anuales de recogidas por cada tipo de basura agrupadas por distrito.
            estadisticasByTipoByDistrito(listaResiduos)
            //Suma de to do lo recogido en un año por distrito.
            sumaByDistrito(listaResiduos)
            //Por cada distrito obtener para cada tipo de residuo la cantidad recogida.
            cantidadRecogidaByTipoByDistrito(listaResiduos)
            //Gráfico con el total de contenedores por distrito.
            graficoTotalContenedoresDistrito(listaContenedores, pathFinal)
            resumenTemplate(listaContenedores, listaResiduos, pathFinal)
            val tiempoFinal = System.currentTimeMillis()
            println("El tiempo de ejecución es de: ${(tiempoFinal - tiempoInicial)} milisegundos ")
        }else{
            println("La extensión no es válida")
        }

    }

    /**
     * Metodo que se encarga de crear una Grafica basada en la consulta que le pasamos
     * que es eL Total de contenedores por distrito
     *
     * @param listaContenedores es un dataframe de contenedores
     * @param pathFinal donde se guarda la imagen
     */
    private fun graficoTotalContenedoresDistrito(listaContenedores: DataFrame<Contenedores>, pathFinal: String) {
        val consulta = listaContenedores
            .groupBy("distrito", "type_Contenedor")
            .aggregate { sum("cantidad") into "TotalContenedores" }.toMap()

        val fig: Plot = letsPlot(data = consulta) + geomBar(
            stat = identity,
            alpha = 0.6,
            fill = Color.BLUE,
            color = Color.RED
        ) {
            x = "distrito"
            y = "TotalContenedores"
        } + labs(
            x = "Nombre del distrito",
            y = "Número total de contenedores",
            title = "Total de contenedores por distrito"
        )

        val path = pathFinal + File.separator + "images"
        if (!Paths.get(path).exists()) {
            Files.createDirectory(Paths.get(pathFinal + File.separator + "images" + File.separator))
        }

        ggsave(fig, path = path + File.separator, filename = "total_contenedores_distrito.png")
    }

    /**
     * Metodo que se encarga de crear la grafica  de media de toneladas mensuales de recogida de basura por distrito
     *
     * @param listaResiduos es un data frame donde se encuentra la lista con todos los datos
     * @param pathDestino donde se guarda la imagen
     */
    private fun graficoMediaToneladasDistrito(listaResiduos: DataFrame<Residuos>, pathDestino: String) {
        val consulta = listaResiduos
            .groupBy("nombre_distrito", "month")
            .aggregate { mean("toneladas") into "media" }.toMap()

        val fig: Plot = letsPlot(data = consulta) + geomBar(
            stat = identity,
            alpha = 0.6,
            fill = Color.ORANGE,
            color = Color.BLACK
        ) {
            x = "nombre_distrito"
            y = "media"
        } + labs(
            x = "Nombre distrito",
            y = "Media de basura recogida",
            title = "Media de toneladas mensuales de basura por distrito."
        )
        val path = pathDestino + File.separator + "images"
        if (!Paths.get(path).exists()) {
            Files.createDirectory(Paths.get(pathDestino + File.separator + "images" + File.separator))
        }
        ggsave(fig, path = path + File.separator, filename = "media_toneladas_distrito.png")
    }

    /**
     *Metodo que resuelve la consulta Cantidad recogida por tipo y por distrito
     *
     * @param listaResiduos es un data frame donde se encuentra la lista con todos los datos
     * @return  devuelve el string filtrado y adapatado a html
     */
    private fun cantidadRecogidaByTipoByDistrito(listaResiduos: DataFrame<Residuos>): String {
        return listaResiduos
                .groupBy("nombre_distrito", "residuos")
                .aggregate {
                    sum("toneladas") into "Total_Toneladas"
                }.sortBy("nombre_distrito").html()
    }

    /**
     * Metodo que resuelve la consulta de suma por distrito
     *
     * @param listaResiduos es un data frame donde se encuentra la lista con todos los datos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun sumaByDistrito(listaResiduos: DataFrame<Residuos>): String {
        return listaResiduos
                .groupBy("nombre_distrito")
                .aggregate { sum("toneladas") into "Toneladas_Totales" }
                .sortBy("nombre_distrito").html()
    }

    /**
     * Metodo que resuelve la consulta de estadisticas por tipo y Distrito
     *
     * @param listaResiduos es un data frame donde se encuentra la lista con todos los datos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun estadisticasByTipoByDistrito(listaResiduos: DataFrame<Residuos>): String {
        return listaResiduos
                .groupBy("nombre_distrito", "residuos", "year")
                .aggregate {
                    min("toneladas") into "Mínimo_Toneladas"
                    max("toneladas") into "Máximo_Toneladas"
                    mean("toneladas") into "Media_Toneladas"
                    std("toneladas") into "Desviación_Toneladas"
                }.sortBy("nombre_distrito").html()
    }

    /**
     * Metodo que resuelve la consulta de media de toneladas por tipo y distrito
     *
     * @param listaResiduos es un data frame donde se encuentra la lista con todos los datos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun mediaToneladasByTipoByDistrito(listaResiduos: DataFrame<Residuos>): String {
        return listaResiduos
                .groupBy("nombre_distrito", "residuos", "year")
                .aggregate { mean("toneladas") into "Media_Toneladas" }
                .sortBy("nombre_distrito").html()
    }

    /**
     * Metodo que resuelve la consulta de media de contenedores filtrado por tipo y distrito
     *
     * @param listaContenedores es un data frame donde se encuentra la lista con todos los datos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun mediaContenedoresByTipoByDistrito(listaContenedores: DataFrame<Contenedores>): String {
        return listaContenedores
                .groupBy("distrito", "type_Contenedor")
                .mean().sortBy("distrito").html()
    }

    /**
     * Metodo que resuelve la consulta de numero de contenedores
     *
     * @param listaContenedores  es un data frame donde se encuentra la lista con todos los datos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun numContenedoresByTipoByDistrito(listaContenedores: DataFrame<Contenedores>): String {
        return listaContenedores
                .groupBy("distrito", "type_Contenedor")
                .count().sortBy("distrito").html()
    }

    /**
     * Programa resumen distrito
     *
     * @param pathOrigen path de donde se sacarán los archivos
     * @param pathFinal path donde se generará el informe
     * @param distrito nombre del distrito a realizar las consultas
     */
    fun programaResumenDistrito(pathOrigen: String, pathFinal: String, distrito: String) {
        val tiempoInicial = System.currentTimeMillis()
        val listaResiduos = parserFicherosResiduos(pathOrigen)
        val listaContenedores = parserFicherosContenedores(pathOrigen)
        val distrito2 = distrito[0].uppercaseChar() + distrito.slice(1 until distrito.length).lowercase(Locale.getDefault());
        if(listaResiduos["nombre_distrito"].toList().toString().contains(distrito2)){
            // Número de contenedores de cada tipo que hay en el distrito dado
            numContenedoresByTipoDistrito(listaContenedores, distrito2)
            //Total de toneladas recogidas en ese distrito por residuo.
            totalToneladasByResiduoDistrito(listaResiduos, distrito2)
            // Gráfico con el total de toneladas por residuo en ese distrito.
            graficoTotalToneladasResiduoDistrito(listaResiduos, distrito2, pathFinal)
            //Máximo, mínimo, media y desviación por mes por residuo en dicho distrito.
            estadisticaByMesByResiduo(listaResiduos, distrito2)
            // Gráfica del máximo, mínimo y media por meses en dicho distrito.
            graficoMaxMinMediaPorMeses(listaResiduos, distrito2, pathFinal)
            distritoResumentemplate(listaContenedores, listaResiduos, pathFinal, distrito2)
            val tiempoFinal = System.currentTimeMillis()
            println("El tiempo de ejecución es de: ${(tiempoFinal - tiempoInicial)} milisegundos ")
        }else{
            println("El distrito no existe")
        }
    }

    /**
     * Metodo que se encarga de filtrar que existan los ficheros  para su procesamiento
     *
     * @param pathOrigen el directorio origen
     * @return  un dataframe de contenedores
     */
    private fun parserFicherosContenedores(pathOrigen: String): DataFrame<Contenedores> {
         if(File(pathOrigen + File.separator + "contenedor.csv").exists()){
             return ContenedoresMapper.csvReaderToContenedores(pathOrigen + File.separator + "contenedores_varios.csv").toDataFrame()
        }else if(File(pathOrigen + File.separator + "contenedor.json").exists()){
             return  ContenedoresMapper.jsonToContenedor(pathOrigen + File.separator + "contenedor.json").map{it.dtoToContenedores()}.toDataFrame()
        }else if(File(pathOrigen + File.separator + "contenedor.xml").exists()){
            return ContenedoresMapper.xmlToContenedorDTO(pathOrigen + File.separator + "contenedor.xml").map{it.dtoToContenedores()}.toDataFrame()
        }else{
            throw Exception("Archivo no válido")
        }
    }

    /**
     * Metodo que se encarga de filtrar que existan los ficheros  para su procesamiento
     *
     * @param pathOrigen el directorio origen
     * @return un dataframe de contenedores
     */

    private fun parserFicherosResiduos(pathOrigen: String): DataFrame<Residuos> {
         if(File(pathOrigen + File.separator + "residuos.csv").exists()){
            return ResiduosMapper.csvReaderToResiduo(pathOrigen + File.separator + "modelo_residuos_2021.csv").toDataFrame()
        }else if(File(pathOrigen + File.separator + "residuos.json").exists()){
            return  ResiduosMapper.jsonToResiduoDTO(pathOrigen + File.separator + "residuos.json").map{it.dtoToResiduos()}.toDataFrame()
        }else if(File(pathOrigen + File.separator + "residuos.xml").exists()){
           return ResiduosMapper.xmlToResiduoDTO(pathOrigen + File.separator + "residuos.xml").map{it.dtoToResiduos()}.toDataFrame()
        }else{
            throw Exception("Archivo no válido")
        }
    }

    /**
     * Metodo que se encarga de generar el grafico
     *
     * @param listaResiduos es un dataframe de residuos
     * @param distrito2 el distrito por el que filtraremos
     * @param pathFinal donde se guardara la grafica
     */

    private fun graficoMaxMinMediaPorMeses(listaResiduos: DataFrame<Residuos>, distrito2: String, pathFinal: String) {
        val res = listaResiduos.filter { it["nombre_distrito"] == distrito2 }
            .groupBy("nombre_distrito", "month")
            .aggregate {
                max("toneladas") into "Máximo"
                min("toneladas") into "Mínimo"
                mean("toneladas") into "Media"
            }.toMap()

        val fig: Plot = letsPlot(data = res)  + geomPoint(
            stat = identity,
            alpha = 0.6,
            fill = Color.DARK_BLUE,
            color = Color.DARK_GREEN
        ) {
            x = "month"
            y = "Mínimo"
        } + geomPoint(
            stat = identity,
            alpha = 0.6,
            fill = Color.YELLOW,
            color = Color.DARK_GREEN
        ) {
            x = "month"
            y = "Media"
        } + geomPoint(
            stat = identity,
            alpha = 0.6,
            fill = Color.RED,
            color = Color.DARK_GREEN
        ) {
            x = "month"
            y = "Máximo"
        } + labs(
            x = "Mes",
            y = "Total",
            title = "Máximo, mínimo y media por meses."
        )
        val path = pathFinal + File.separator + "images"
        if (!Paths.get(path).exists()) {
            Files.createDirectory(Paths.get(pathFinal + File.separator + "images" + File.separator))
        }
        ggsave(fig, path = path + File.separator, filename = "estadisticas_mensual_$distrito2.png")
    }

    /**
     * Metodo que se encarga de crear el grafico de Total de Toneldas de Residuo por Distrito
     *
     * @param listaResiduos es un dataframe de residuos
     * @param distrito2 el distrito por el que filtraremos
     * @param pathFinal donde se guardara la imagen
     */
    private fun graficoTotalToneladasResiduoDistrito(listaResiduos: DataFrame<Residuos>, distrito2: String, pathFinal: String) {
        val res = listaResiduos
            .filter { it["nombre_distrito"] == distrito2 }
            .groupBy("residuos", "toneladas")
            .aggregate {
                count() into "totalToneladas"
            }
            .toMap()

        val fig: Plot = letsPlot(data = res) + geomBar(
            stat = identity,
            alpha = 0.8,
            fill = Color.RED,
            color = Color.BLACK,
        ) {
            x = "residuos"
            y = "totalToneladas"
        } + labs(
            x = "Tipo de residuo",
            y = "Toneladas",
            title = "Total de toneladas por residuo en $distrito2"
        )

        val path = pathFinal + File.separator + "images"
        if (!Paths.get(path).exists()) {
            Files.createDirectory(Paths.get(pathFinal + File.separator + "images" + File.separator))
        }
        ggsave(fig, path = path + File.separator, filename = "toneladas_tipo_$distrito2.png")
    }

    /**
     * Metodo que resuelve la consulta de Estadisticas en un mes por residuo
     *
     * @param listaResiduos data frame de residuos
     * @param distrito2 distrito por el que filtraremos para sacar los resultados
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun estadisticaByMesByResiduo(listaResiduos: DataFrame<Residuos>, distrito2: String): String {
        return listaResiduos
                .filter { it["nombre_distrito"] == distrito2 }
                .groupBy("month", "nombre_distrito", "residuos")
                .aggregate {
                    max("toneladas") into "Máximo_Toneladas"
                    min("toneladas") into "Mínimo_Toneladas"
                    mean("toneladas") into "Media_Toneladas"
                    std("toneladas").toString().replace("NaN", "0") into "Desviación_Toneladas"
                }.html()
    }

    /**
     * Metodo que resuelve la consulta de Total de Tonelada por Distrito
     *
     * @param listaResiduos es un dataframe de residuos
     * @param distrito2 distrito por el que filtraremos
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun totalToneladasByResiduoDistrito(listaResiduos: DataFrame<Residuos>, distrito2: String): String {
        return listaResiduos
            .filter { it["nombre_distrito"] == distrito2 }
            .groupBy("nombre_distrito", "residuos")
            .aggregate { sum("toneladas") into "Toneladas_Totales" }
            .sortBy("nombre_distrito").html()
    }

    /**
     * Metodo que resuelve la consulta Numero de Contenedores por Distrito
     *
     * @param listaContenedores es un dataframe de contenedores
     * @param distrito distrito por el que filtraremos la consulta
     * @return devuelve el string filtrado y adapatado a html
     */
    private fun numContenedoresByTipoDistrito(listaContenedores: DataFrame<Contenedores>, distrito: String): String {
        var cambioDistrito = distrito.replace("í", "i").uppercase(Locale.getDefault())
        return listaContenedores
            .filter { it["distrito"] == cambioDistrito }
            .groupBy("distrito", "type_Contenedor")
            .count().sortBy("distrito").html()
    }

    /**
     * Comprueba segun los argumentos de la consola dados
     *
     * @param args argumentos de la consola
     * @return la opcion elegida
     */
    fun comprobarPrograma(args: Array<String>): String {
        if (args.size < 2 || args.size > 5) {
            throw Exception("Argumentos no válidos")
        }
        if (args[0] == "parser") {
            val pathOrigen = args[1]
            val pathFinal = args[2]
            if ( validarDirectorio(pathOrigen, pathFinal)) { //&&
                return "Parsear"
            }
        }
        else if (args[0].lowercase(Locale.getDefault()) == "resumen" && args.size == 3) {
            val pathOrigen = args[1]
            val pathFinal = args[2]
            if (validarDirectorio(pathOrigen, pathFinal)) {
                return "Resumen"
            } else {
                throw Exception("Extensión no válida")
            }
        } else if (args[0].lowercase(Locale.getDefault()) == "resumen" && args.size == 4) {
            val pathOrigen = args[2]
            val pathFinal = args[3]
            if (validarDirectorio(pathOrigen, pathFinal)) {
                return "ResumenDistrito"
            } else {
                throw Exception("Extensión no válida")
            }
        }
        throw Exception("Argumentos no válidos")
    }




    /**
     * Metodo que se encarga de hacer el template html de Resumen
     *
     * @return un html con el resumen
     */
    private fun resumenTemplate(listaContenedores: DataFrame<Contenedores>, listaResiduos: DataFrame<Residuos>, pathFinal: String ) {
        val html = """ <!doctype html>
        <html lang="en">
        <head>
             <meta charset="utf-8">
             <title>Resumen de recogidas de basura y reciclaje de Madrid</title>
             <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" type="text/css" href="./css/main.css" media="screen" />
        </head>

    <body>
        <h1>Resumen de recogidas de basura y reciclaje de Madrid </h1></n>
        <hr/><hr/>
        <p>Este resumen se ha generado a las ${LocalDateTime.now()}<br/>
           Autores:Alejandro López Abad y Rubén García-Redondo Marín </p>
        <h3>Este es el resumen de Contenedores y Residuos</h3>  <!--hay que hacer contenedores-->

        <h4>Consultas</h4>
        <p>Se van a resolver las siguientes consultas: </p>
           <ol>
             <li>Número de contenedores de cada tipo que hay en cada distrito</li>
          
             <li> Media de contenedores de cada tipo que hay en cada distrito</li>
             <li> Media de toneladas anuales de recogidas por cada tipo de basura agrupadas por
                 distrito.</li>
             <li> Máximo, mínimo , media y desviación de toneladas anuales de recogidas por cada tipo
                 de basura agrupadas por distrito.
             </li>
             <li>Suma de todo lo recogido</li>
             <li> Por cada distrito obtener para cada tipo de residuo la cantidad recogida.</li>
           </ol>

            <hr/>
            <hr/>

        <table style="text-align: center;width: 100%;" border="1" cellpadding="2" cellspacing="2">

            <tr>
                <th>Consulta</th> <!--1-->
                <th>Resultado</th>
                <th>Grafica</th>
            </tr>

            <tr>
               <td><h5>1</h5></td><!--1-->
               <td>${numContenedoresByTipoByDistrito(listaContenedores)}</td><!--2-->
               <td>Sin gráfica</td><!--3-->
            </tr>

            <tr>
               <td><h5>2</h5></td><!--2-->
               <td>${mediaContenedoresByTipoByDistrito(listaContenedores)}</td><!--2-->
               <td>Sin gráfica</td><!--3-->
            </tr>
            <tr>
               <td><h5>3</h5></td><!--3-->
               <td>${mediaToneladasByTipoByDistrito(listaResiduos)}</td><!--2-->
               <td><img src="./images/media_toneladas_distrito.png"></td><!--3-->
            </tr>
             <tr>
                <td><h5>4</h5></td><!--4-->
                <td>${estadisticasByTipoByDistrito(listaResiduos)}</td><!--2-->
                <td>Sin gráfica</td><!--3-->
             </tr>
             <tr>
                <td><h5>5</h5></td><!--3-->
                <td>${sumaByDistrito(listaResiduos)} </td><!--2-->
                <td>Sin gráfica</td><!--3-->
            </tr>
            <tr>
               <td><h5>6</h5></td><!--3-->
               <td>${cantidadRecogidaByTipoByDistrito(listaResiduos)}</td><!--2-->
               <td><img src="./images/total_contenedores_distrito.png"></td><!--3-->
            </tr>
        </table>

    </body>

</html>"""

        val css = """html {
  color: #222;
  font-size: 1em;
  line-height: 1.4;
}

hr {
  display: block;
  height: 1px;
  border: 0;
  border-top: 1px solid #ccc;
  margin: 1em 0;
  padding: 0;
}
textarea {
  resize: vertical;
}
 p,
  h2,
  h3 {
    orphans: 3;
    widows: 3;
  }

  h2,
  h3 {
    text-align: center;
    page-break-after: avoid;
  }
  * {
    line-height: 1.2;
    margin: 0;
  }

  html {
    color: #888;
    display: table;
    font-family: sans-serif;
    height: 100%;
    padding-left: 10px;
    width: 100%;
  }

  h1 {
    color: #555;
    font-size: 2em;
    font-weight: 400;
    text-align: center;
    position: center;
  }

  h3 {
    text-align: center;
  }
  table{

    align-content: center;
    align-items: center;
    align-self: center;


  }
 body{
    background: #66f7ff;

  }
  img{
     
      width: 720px;
      height: 480px;
      
  }
  td{
      align-content: center;
  }

  h5{
    text-align: center
  }
  tr{
    padding-right: 100px;
  }"""

        File(pathFinal + File.separator + "resumen.html").writeText(html)
        if(!(Files.exists(Paths.get(pathFinal + File.separator + "css")))){
            Files.createDirectory(Paths.get(pathFinal + File.separator + "css"))
        }
        File(pathFinal + File.separator + "css" + File.separator + "main.css").writeText(css)
    }



    /**
     *
     * Metodo que se encarga de hacer el template html de un resumen por distrito
     *
     * @return un html con el resumen por distrito
     */
    private fun distritoResumentemplate(listaContenedores: DataFrame<Contenedores>, listaResiduos: DataFrame<Residuos>, pathFinal: String , distrito2: String){
        val html =  """  <!doctype html>
        <html lang="en">
        <head>
             <meta charset="utf-8">
             <title>Resumen de recogidas de basura y reciclaje de Madrid</title>
             <meta name="viewport" content="width=device-width, initial-scale=1">
             <link rel="stylesheet" type="text/css" href="./css/main.css" media="screen" />
        </head>

    <body>
        <h1>Resumen de recogidas de basura y reciclaje de Madrid </h1></n>
        <hr/><hr/>
        <p>Este resumen se ha generado a las ${LocalDateTime.now()}<br/>
           Autores:Alejandro López Abad y Rubén García-Redondo Marín </p>
        <h3>Este es el resumen de $distrito2</h3>

        <h4>Consultas</h4>
        <p>Se van a resolver las siguientes consultas: </p>
           <ol>
             <li>Media de contenedores de cada tipo que hay en cada distrito</li>
             <li> Total de toneladas recogidas en ese distrito por residuo</li>
             <li> Máximo, mínimo , media y desviación por mes por residuo </li>
           </ol>

            <hr/>
            <hr/>

        <table style="text-align: center;width: 100%;" border="1" cellpadding="2" cellspacing="2">

            <tr>
              <th>Consulta</th> 
              <th>Resultado</th>
              <th>Grafica</th>
            </tr>

            <tr>
                <td><h5>1</h5></td>
                <td>>${numContenedoresByTipoDistrito(listaContenedores, distrito2.replace("í", "i").uppercase(Locale.getDefault())
        )}</td>
                <td>Sin gráfica</td>
            </tr>
            <tr>
                <td><h5>2</h5></td><!--2-->
                <td>${totalToneladasByResiduoDistrito(listaResiduos, distrito2)}</td><!--2-->
                <td><img src="./images/toneladas_tipo_$distrito2.png"></td><!--3-->
            </tr>
            <tr>
                <td><h5>3</h5></td><!--3-->
                <td> 
                   ${estadisticaByMesByResiduo(listaResiduos, distrito2)}
                </td>     
                <td><img src="./images/estadisticas_mensual_$distrito2.png"></td><!--3-->
            </tr>
          
           
       </table>

    </body>

</html>"""

        val css = """html {
  color: #000000;
  font-size: 1em;
  line-height: 1.4;
}

hr {
  display: block;
  height: 1px;
  border: 0;
  border-top: 1px solid #ccc;
  margin: 1em 0;
  padding: 0;
}
textarea {
  resize: vertical;
}
 p,
  h2,
  h3 {
    orphans: 3;
    widows: 3;
  }

  h2,
  h3 {
    text-align: center;
    page-break-after: avoid;
  }
  * {
    line-height: 1.2;
    margin: 0;
  }

  html {
    color: #888;
    display: table;
    font-family: sans-serif;
    height: 100%;
    padding-left: 10px;
    width: 100%;
  }

  h1 {
    color: #555;
    font-size: 2em;
    font-weight: 400;
    text-align: center;
    position: center;
  }

  h3 {
    text-align: center;
  }
  table{

    align-content: center;
    align-items: center;
    align-self: center;


  }
 body{
    background: #66f7ff;

  }
  img{
     
      width: 720px;
      height: 480px;
      
  }
  td{
      align-content: center;
  }

  h5{
    text-align: center
  }
  tr{
    padding-right: 100px;
  }"""

        File(pathFinal + File.separator + "resumen_$distrito2.html").writeText(html)
        if(!(Files.exists(Paths.get(pathFinal + File.separator + "css")))){
            Files.createDirectory(Paths.get(pathFinal + File.separator + "css"))
        }
        File(pathFinal + File.separator + "css" + File.separator + "main.css").writeText(css)
    }


}