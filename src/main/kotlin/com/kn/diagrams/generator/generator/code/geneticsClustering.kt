package com.kn.diagrams.generator.generator.code


//import com.kn.diagrams.generator.config.GeneticsParametersVariation
import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.generator.containingClass
import java.io.File
import java.util.stream.IntStream
import kotlin.math.pow
import kotlin.math.roundToInt


 class GeneticsClustering() {

    fun dependencyMatrix(nodes:List<String>,edges: List<Pair<String, String>>): Array<IntArray>{
/*
        var nodesNb = nodes.size

        var dependencyMatrix = Array(nodesNb) { IntArray(nodesNb) }

        for (i in 0 until  nodesNb) {

            for (j in 0 until nodesNb) {

                for (k in edges) {

                    if (nodes[j] == k.second &&
                        nodes[i] == k.first
                        ||
                        nodes[i] == k.second &&
                        nodes[j] == k.first

                    )
                        dependencyMatrix[i][j] = 1
                    else

                        dependencyMatrix[i][j] = 0
                }
            }
        }

        return dependencyMatrix


 */

        var nodesNb = nodes.size


        var dependencyMatrix = Array(nodesNb) { IntArray(nodesNb) {0} }



         var e = edges.groupBy ({ it.first},{it.second})

        e.forEach { (edge, list) -> list.forEach { nachbar->

            if(nodes.indexOf(edge)!=nodes.indexOf(nachbar)) {
                dependencyMatrix[nodes.indexOf(edge)][nodes.indexOf(nachbar)] = 1
                dependencyMatrix[nodes.indexOf(nachbar)] [nodes.indexOf(edge)] = 1
            }

        } }

       /* edges.forEach { edge ->
           // if(nodes.indexOf(edge.first)>0 && nodes.indexOf(edge.second)>0)
            if(nodes.indexOf(edge.first)!=nodes.indexOf(edge.second))
            dependencyMatrix[nodes.indexOf(edge.first)] [nodes.indexOf(edge.second)] = 1
        }

        */
        return dependencyMatrix

    }



    fun degree(nodeIndex: Int, dependencyMatrix: Array<IntArray>): Int {

        return dependencyMatrix[nodeIndex].sum()

    }

   /* fun nodesNeighbors(dependencyMatrix: Array<IntArray>, nodesNb: Int): HashMap<Int, MutableList<Int>> {
        val nodeNeighbors = HashMap<Int, MutableList<Int>>()

        IntStream.range(0, nodesNb).parallel()
            .forEach { i -> var neighbors = mutableListOf<Int>()
                IntStream.range(0, nodesNb)
                    .forEach { j ->
                if (i != j) {
                    if (dependencyMatrix[i][j] == 1) {
                        neighbors.add(j)
                        nodeNeighbors[i] = neighbors
                    }
                }
            }
        }

        return nodeNeighbors


    }

    */

    fun structuralSimilarityMatrix(dependencyMatrix: Array<IntArray>, nodesNb: Int,nodeNeighbors : HashMap<Int, MutableList<Int>>): Array<DoubleArray> {

        var structsim = Array(nodesNb) { DoubleArray(nodesNb) {0.0} }

        dependencyMatrix.forEachIndexed { row, ints ->
            ints.forEachIndexed { col, i ->
                if (i == 1) {
                    var commonNeighbors = mutableListOf<Int>()

                    var c =
                        nodeNeighbors[row]?.let { nodeNeighbors[col]?.intersect(it.toSet()) }

                    if (c != null) {
                        if (c.isNotEmpty()) {
                            commonNeighbors = c.toMutableList()
                        } else {
                            commonNeighbors.add(row)
                            commonNeighbors.add(col)

                        }
                    }

                    var numerator = sumCommonNeighborsDegree(dependencyMatrix, commonNeighbors)

                    var denominator = 0.0

                    if (nodeNeighbors[row] != null && nodeNeighbors[col] != null) {
                        denominator =
                            (sumCommonNeighborsDegree(
                                dependencyMatrix, nodeNeighbors[row]!!.toMutableList()
                            ).pow(0.5)) * (sumCommonNeighborsDegree(
                                dependencyMatrix,
                                nodeNeighbors[col]!!.toMutableList()
                            ).pow(
                                0.5
                            ))
                    }

                    if (denominator != 0.0)
                        structsim[row][col] = numerator / denominator


                }
            }
        }
                return structsim
            }

    fun sumCommonNeighborsDegree(dependencyMatrix: Array<IntArray>,commonNeighbors: MutableList<Int>): Double {

        var reciprocal = DoubleArray(commonNeighbors!!.size)

        commonNeighbors.forEachIndexed {neighbor,Int ->
            reciprocal[neighbor]= degree(neighbor.toString().toInt(),dependencyMatrix).toDouble().pow(-1)
        }
        return reciprocal.sum()

    }

    /*fun sumCommonNeighborsDegree(dependencyMatrix: Array<IntArray>, commonNeighbors: MutableList<Any>): Double {

        var reciprocal = DoubleArray(commonNeighbors!!.size)
        IntStream.range(0, commonNeighbors.size!!).parallel()
            .forEach { i ->
            reciprocal[i] = degree(commonNeighbors[i].toString().toInt(), dependencyMatrix).toDouble().pow(-1)
        }
        return reciprocal.sum()

    }

     */

  /*  fun sumReciprocalNeighborDegree(dependencyMatrix: Array<IntArray>, index: Int,nodeNeighbors : HashMap<Int, MutableList<Int>>): Double {



        var neighbors= mutableListOf<Int>()

        if(nodeNeighbors[index]!=null)
             neighbors= nodeNeighbors[index]!!
        else
            return 0.0

            var reciprocal = DoubleArray(neighbors!!.size)!!

        IntStream.range(0, neighbors!!.size).parallel()
            .forEach { i ->

                reciprocal[i] = degree(neighbors[i], dependencyMatrix).toDouble().pow(-1)

            }


        return reciprocal.sum()
    }

   */

    fun sumNeighborsSimilarity(index: Int, dependencyMatrix: Array<IntArray>, nodesNb: Int,nodeNeighbors : HashMap<Int, MutableList<Int>>,structuralSimilarity:Array<DoubleArray>): Double {


        var neighbors = nodeNeighbors[index]

        var d=0.0
        if(neighbors!=null)
        neighbors.forEach {  value ->
            d+= structuralSimilarity[index][value]}


        return d
    }


    fun rouletteWheelSelection(dependencyMatrix: Array<IntArray>, nodesNb: Int,nodeNeighbors : HashMap<Int, MutableList<Int>>,structuralSimilarity:Array<DoubleArray>): Array<DoubleArray> {

        val roulette = Array(nodesNb) { DoubleArray(nodesNb) {0.0} }

        structuralSimilarity.forEachIndexed { row, ints -> ints.forEachIndexed { col, i ->

            if(i!=0.0)
            roulette[row][col]=i/sumNeighborsSimilarity(col, dependencyMatrix, nodesNb,nodeNeighbors,structuralSimilarity)

        }}

        return roulette
    }


    fun initialPopulation(parentSize: Int, dependencyMatrix: Array<IntArray>, nodesNb: Int,nodeNeighbors : HashMap<Int, MutableList<Int>>,structuralSimilarity: Array<DoubleArray>): HashSet<IntArray> {

        val population = HashSet<IntArray>()

        val roulette = rouletteWheelSelection(dependencyMatrix,nodesNb,nodeNeighbors,structuralSimilarity)

        for (k in 0 until parentSize) {
            var individual = IntArray(nodesNb)
            roulette.forEachIndexed { indexI, rows ->
                var nachbar = mutableListOf<Int>()
                rows.forEachIndexed { indexJ, value ->
                    if(value!=0.0){
                        nachbar.add(indexJ)

                    }
                    if(nachbar.size>0) {

                        individual[indexI] = nachbar.random()
                    }
                }

            }

            population.add(individual)
        }

        return population

    }

    fun modularity(individual: IntArray, dependencyMatrix: Array<IntArray>): Double {

        var m =0.0
        dependencyMatrix.forEach {  ints ->
            m+= ints.sum().toDouble()
        }
      /*  var s=0.0
        individual.forEachIndexed { index, i ->
            s+= 1-((degree(index, dependencyMatrix)*degree(i, dependencyMatrix)).div(m))

        }
       */
        var labels= encoding(individual)
        var s=0.0
        dependencyMatrix.forEachIndexed { i, ints -> ints.forEachIndexed { j, k ->

            if(labels[i]==labels[j] && i!=j) {
                s+=k-((degree(i, dependencyMatrix) * degree(j, dependencyMatrix)).div(m))
            }


        }  }
        return ((1.0.div(m))*s)
    }

    fun crossover(individual1: IntArray, individual2: IntArray): IntArray {

        var randomArray = IntArray(individual1.size)
        var individual3 = IntArray(individual1.size)

        IntStream.range(0, individual1.size).parallel()
            .forEach { i ->

            randomArray[i] = Math.random().roundToInt()

            if (randomArray[i] == 0) {
                individual3[i] = individual1[i]

            } else {
                individual3[i] = individual2[i]

            }
        }


        return individual3

    }


    fun encoding(individual: IntArray):IntArray{
       /* var labels =IntArray(individual.size)

        var communities= mutableListOf<MutableList<Int>>()
        var com1 = mutableListOf<Int>()
        com1.add(0)
        com1.add(individual[0])
        communities.add(com1)
        var com = mutableListOf<Int>()

        IntStream.range(0, communities.size).parallel()
            .forEach { k ->

                IntStream.range(1, individual.size)
                    .forEach { i ->
                if(communities.elementAt(k).contains(i) || communities.elementAt(k).contains(individual[i]))
                {
                    communities.elementAt(k).add(i)
                    communities.elementAt(k).add(individual[i])

                }
                else{

                    com = mutableListOf()
                    com.add(i)
                    com.add(individual[i])
                }

            }
            if(com.size!=0) {
                communities.add(com)


            }


        }


        IntStream.range(0, individual.size).parallel()
            .forEach { i ->
                IntStream.range(0, communities.size)
                    .forEach { j ->
                if(communities.elementAt(j).contains(individual[i]))

                    labels[i]=j+1


            }
        }


        return labels

        */
      /*  var labels =IntArray(individual.size)

        var communities= mutableListOf<MutableList<Int>>()

        individual.forEachIndexed { index, i ->
            var c = mutableListOf<Int>()
            c.add(index)
            c.add(individual[index])
            communities.add(c)
        }

        outer@while(true) {
            for (i in 0 until communities.size) {
                if (i + 1 < communities.size) {
                    var inter = communities.elementAt(i).intersect(communities.elementAt(i + 1).toSet())
                    if (inter.isNotEmpty()) {
                        var union = communities.elementAt(i).union(communities.elementAt(i + 1)).distinct()
                        communities.remove(communities.elementAt(i))
                        communities.add(i, union.toMutableList())
                        communities.remove(communities.elementAt(i + 1))

                        continue@outer

                    }

                }

            }
            break@outer
        }

        individual.forEachIndexed{indexI,  i -> communities.forEachIndexed { indexJ,com ->
            if(com.contains(i)) {
                labels[indexI] = indexJ
            }
        } }
        return labels

       */
    /*    var labels =IntArray(individual.size)

        var communities= mutableListOf<MutableList<Int>>()

        individual.forEachIndexed { index, i ->
            var c = mutableListOf<Int>()
            c.add(index)
            c.add(individual[index])
            communities.add(c)
        }

        var inter :Set<Int>
        var union :MutableList<Int>
        IntStream.range(0, communities.size)
            .forEach { i ->
                IntStream.range(0, communities.size)
                    .forEach { j ->
                if (j<communities.size && i<communities.size && communities.elementAt(i)!=(communities.elementAt(j)) ) {

                    inter = communities.elementAt(i).intersect(communities.elementAt(j))

                    if (inter.isNotEmpty()) {
                        union =
                            communities.elementAt(i).union(communities.elementAt(j)).distinct().toMutableList()

                        communities.remove(communities.elementAt(i))
                        communities.add(i,union)
                        communities.remove(communities.elementAt(j))
                        if(j-1>0 )
                            communities.add(j,communities.elementAt(j-1))



                    }}
            }
        }

        individual.forEachIndexed{indexI,  i -> communities.forEachIndexed { indexJ,com ->
            if(com.contains(i)) {
                labels[indexI] = indexJ
            }
        } }


        return labels

     */

        var labels =IntArray(individual.size)


        individual.forEachIndexed() { i,c->
            individual.forEachIndexed { j,m->
                if(c==m)
                {
                    labels[i]=individual.size-i
                    labels[j]=individual.size-i
                    labels[c]=individual.size-i
                    labels[m]=individual.size-i
                }
            }
        }

        return labels
    }
    fun LPLSS(individual: IntArray, dependencyMatrix: Array<IntArray>,nodeNeighbors : HashMap<Int, MutableList<Int>>,structSim: Array<DoubleArray>): IntArray {

        var mutatedC = individual
       // var structSim = structuralSimilarityMatrix(dependencyMatrix, individual.size,nodeNeighbors)
        var marginalGenes = mutableListOf<Int>()
        /*IntStream.range(0, individual.size).parallel()
            .forEach { i ->
            if (!individual.contains(i)) {
                marginalGenes.add(i)
            }

        }

         */
        individual.forEachIndexed { index, i ->  if(! individual.contains(index))
            marginalGenes.add(i)}

        if (marginalGenes.size != 0) {
            var neighbors = nodeNeighbors
            var labels = encoding(individual)
            var neighLabels = mutableListOf<Int>()

            IntStream.range(0, marginalGenes.size)
                .forEach { i ->
                var n = neighbors[marginalGenes[i]]
                if (n != null) {

                    if (n.size == 1)
                        mutatedC = individual


                    if (n.size == 2) {
                        if (labels[n.first()] == labels[n.last()]) {
                            mutatedC[marginalGenes[i]] = n.random()
                        } else {
                            if (structSim[marginalGenes[i]][n.first()] > structSim[marginalGenes[i]][n.last()])

                                mutatedC[marginalGenes[i]] = n.first()
                            else
                                mutatedC[marginalGenes[i]] = n.last()
                        }

                    }
                    if (n.size > 2) {
                        var neighLabels = IntArray(n.size)
                        IntStream.range(0, n.size)
                            .forEach { j ->
                            neighLabels[j] = labels[n[j]]
                        }

                        //wenn .toList klappt nicht-> toList().toTypedArray()
                        var mostLabel = neighLabels.toList()
                            .groupingBy { it }
                            .eachCount()
                            .toList()
                            .sortedByDescending { it.second }
                            .take(1)
                            .map { it.first }[0]

                        var neigMostLabel = mutableListOf<Int>()
                        IntStream.range(0, n.size)
                            .forEach { j ->
                            if (labels[n[j]] == mostLabel)

                                neigMostLabel.add(n[j])

                        }

                        if (neigMostLabel.size == 1)
                            mutatedC[marginalGenes[i]] = neigMostLabel[0]
                        else {
                            var mostStruct = 0
                            IntStream.range(0, neigMostLabel!!.size-1)
                                .forEach { j ->
                                if (structSim[marginalGenes[i]][neigMostLabel[j]]
                                    > structSim[marginalGenes[i]][neigMostLabel[j + 1]]
                                )

                                    mostStruct = neigMostLabel[j]
                            }

                            mutatedC[marginalGenes[i]] = mostStruct
                        }
                    }


                } else
                    mutatedC = individual

            }
        }

        return mutatedC
    }

data class Result(val nodeMLabel:Map<String,String>, val modularity:Double)
    fun LSSGA(
        nodes: List<String>,
        edges: List<Pair<String, String>>,

        iterations: Int,
        parentSize: Int,
        childSize: Int,
        crossoverRate: Double,
        mutationRate: Double,

        ):Result {


        var dependencyMatrix = dependencyMatrix(nodes, edges)

        val nodeNeighbors = HashMap<Int, MutableList<Int>>()
        dependencyMatrix.forEachIndexed { row, ints ->
            var n = mutableListOf<Int>()
            ints.forEachIndexed { col, i ->
                if (i == 1) {
                    n.add(col)
                    nodeNeighbors[row] = n
                }

            }
        }
        var structuralSimilarity = structuralSimilarityMatrix(dependencyMatrix, nodes.size, nodeNeighbors)
        var initialPopulation =
            initialPopulation(parentSize, dependencyMatrix, nodes.size, nodeNeighbors, structuralSimilarity)



        IntStream.range(0, iterations)
            .forEach { g ->
            var childPopulation = HashSet<IntArray>()

            IntStream.range(0, childSize)
                .forEach { i ->
                    var chromo = IntArray(nodes.size)


                    var individual1 = initialPopulation.random()
                    var individual2 = initialPopulation.random()

                    if (modularity(individual1, dependencyMatrix) != 0.0 &&
                        modularity(individual2, dependencyMatrix) != 0.0
                    ) {
                        chromo = crossover(individual1, individual2)

                    }

                    var C = LPLSS(chromo, dependencyMatrix, nodeNeighbors, structuralSimilarity)
                    childPopulation.add(C)

                }
            initialPopulation.addAll(childPopulation)

            var rankedPopulation = initialPopulation.toList()
                .map { modularity(it, dependencyMatrix) to it }
                .sortedByDescending { it.first }
                .take(parentSize)
                .map { it.second }
                .toHashSet()

            initialPopulation = rankedPopulation


        }


        var bestIndividuals = initialPopulation.toList()
            .map { modularity(it, dependencyMatrix) to it }
            .sortedByDescending { it.first }
            .take(1)
            .map { it.second }[0]


        var algoModularity= modularity(bestIndividuals,dependencyMatrix)

        var labels = encoding(bestIndividuals)

        var nodeMLabel = mutableMapOf<String, String>()

        labels.forEachIndexed { index, i -> nodeMLabel[nodes[index]] = "Cluster_$i" }

        /*  IntStream.range(0, nodes.size).parallel()
            .forEach { i ->
            nodeMLabel[nodes[i]] = "Cluster_${labels[i]}"

        }

       */
/* Cluster Anzahl
var x= nodeMLabel.toList()
        var y= nodeMLabel.values.toList()
  var c = y.toList().groupBy { it }.mapValues { it.value.size }.toList().sortedByDescending { (_, value) -> value }
      .toMap()


 */
        return Result(nodeMLabel.toMap(),algoModularity)


    }


}
/*data class GeneticsConfigVariation(var iteration: List<Int>, var parentSize: List<Int>, var childSize: List<Int>, var crossoverRate: List<Double>, var mutationRate: List<Double>){
    constructor() : this(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}
fun variateGenetics(variation: GeneticsParametersVariation): List<GeneticConfig> {
    val configs = mutableListOf<GeneticConfig>()

    for (iterations in variation.iterations){
        for (parentSize in variation.parentsize){
            for (childSize in variation.childSize){
                for (crossoverRate in variation.crossoverRate){
                    for(mutationRate in variation.mutationRate) {
                        configs += GeneticConfig(
                            iterations,
                            parentSize,
                            childSize,
                            crossoverRate,
                            mutationRate
                        )
                    }
                }
            }
        }
    }

    return configs.toList()
}



 */

    data class GeneticConfig(val iterations:Int,
                             val parentSize:Int, val childSize:Int,
                             val crossoverRate:Double, val mutationRate:Double)

   fun clusterByGenetic(
        config: GeneticConfig,
        nodes: List<String>,
        edges: List<Pair<String, String>>,
        genetic:GeneticsClustering
    ): GeneticsClusterDefinition {

       return GeneticsClusterDefinition(
           genetic.LSSGA(
               nodes, edges,
               config.iterations, config.parentSize, config.childSize,
               config.crossoverRate, config.mutationRate
           ).nodeMLabel
       )

    }


fun GeneticsClusterDiagramContext.loadGeneticsClusters(): GeneticsClusterDefinition {
    val nodes = baseEdgesGenetics.flatMap { it.nodes() }.map { it.nameInClusterGenetics() }.distinct()
    val edges =
        baseEdgesGenetics.flatMap { listOf((it.from()?.nameInClusterGenetics()) to (it.to()?.nameInClusterGenetics())) }
                as List<Pair<String, String>>

    File(".\\genetics_graph_${nodes.hashCode()}.txt")
        .createIfNotExists()
        .writeText(createGeneticsEdges(nodes))

    /* if (configGenetics.details.LSSGA.geneticsOptimizeClusterDistribution) {
        val bestClusters = variateGenetics(configGenetics.details.LSSGA.geneticsOptimization).parallelStream()
            .map {
                throwExceptionIfCanceled()
                it to edgesToGeneticsClusters()
            }.toList()
            .sortedBy { scoreGenetics(it.second) }
            .take(20)

        bestClusters.forEachIndexed { i, clusters ->
            File("result_winner_Genetics_$i").let {
                if (!it.exists()) it.createNewFile()
                it
            }.writeText(
                serializer.toJson(clusters.first) + "\n\n" + clusters
                    .second.sortedByDescending { it.dependenciesToOtherClustersCount }.joinToString("\n")
            )
        }

        val bestConfig = bestClusters.first().first
        with(configGenetics.details.LSSGA) {
            iterations = bestConfig.iterations
            parentSize = bestConfig.parentSize
            childSize = bestConfig.childSize
            crossoverRate = bestConfig.crossoverRate
            mutationRate = bestConfig.mutationRate
        }

        return clusterByGenetic(
            GeneticConfig(
                bestConfig.iterations,
                bestConfig.parentSize,
                bestConfig.childSize,
                bestConfig.crossoverRate,
                bestConfig.mutationRate
            ), nodes,edges, GeneticsClustering(nodes,edges,bestConfig.iterations,
                bestConfig.parentSize,
                bestConfig.childSize,
                bestConfig.crossoverRate,
                bestConfig.mutationRate )

        )
    } else {
        with(configGenetics.details.LSSGA) {
            return clusterByGenetic(
                GeneticConfig(
                    iterations, parentSize, childSize, crossoverRate, mutationRate)
                , nodes,edges,
                GeneticsClustering(nodes,edges,iterations, parentSize, childSize, crossoverRate, mutationRate)
            )
        }
    }
}

    */

    /*val c = configGenetics.details.LSSGA
    val gc = GeneticConfig(c.iterations,c.parentSize,c.childSize,c.crossoverRate,c.mutationRate)
    val g = GeneticsClustering(nodes, edges, c.iterations, c.parentSize, c.childSize, c.crossoverRate, c.mutationRate)
    val cg = clusterByGenetic(gc,nodes,edges,g)

    return cg

     */
    with(configGenetics.details.LSSGA) {
        configGenetics.details.Modularity = GeneticsClustering().LSSGA(nodes, edges, iterations, parentSize, childSize, crossoverRate, mutationRate).modularity
    }
    with(configGenetics.details.LSSGA) {
        return clusterByGenetic(
            GeneticConfig(
                iterations,
                parentSize,
                childSize,
                crossoverRate,
                mutationRate
            ), nodes,
            edges, GeneticsClustering()
        )
    }

}

/*fun scoreGenetics(clusters: List<GeneticsCluster>): Double{
    val openClusters = clusters.filterNot { it.encapsulated() }
    val averageDependencies = openClusters.map { it.dependenciesToOtherClustersCount }.average()
    val averageDeviation = openClusters.map { it.dependenciesToOtherClustersCount }.standardDeviationGenetics()

    return averageDependencies * averageDeviation
}
fun List<Int>.standardDeviationGenetics(): Double {
    var sum = 0.0
    var standardDeviation = 0.0

    for (num in this) {
        sum += num
    }

    val mean = sum / size

    for (num in this) {
        standardDeviation += (num - mean).pow(2.0)
    }

    return sqrt(standardDeviation / size)
}

 */

fun GeneticsClusterDiagramContext.createGeneticsEdges(nodes: List<String>) = baseEdgesGenetics.asSequence()
        .flatMap {
            sequenceOf(
                nodes.indexOf(it.from()!!.nameInClusterGenetics()) to nodes.indexOf(it.to()!!.nameInClusterGenetics()),
                nodes.indexOf(it.to()!!.nameInClusterGenetics()) to nodes.indexOf(it.from()!!.nameInClusterGenetics())
            )
        }
        .groupBy { it }
        .entries.map { (key, values) -> Triple(key.first, key.second, values.size) }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { "" + it.first + "\t" + it.second + "\t" + it.third }
        .joinToString("\n")

// TODO unify with datastructure ClusterNode; add comment in config that services/dataclasses get balanced - configure the weight?

    fun GeneticsClusterDiagramContext.edgesToGeneticsClusters(): List<GeneticsCluster> {

        with(geneticsVisualConfig.projectClassification) {
            return baseEdgesGenetics.flatMap { listOf(it.from()!! to it, it.to()!! to it) }
                .map { (node, edge) -> node.aggregateGenetics() to edge }
                .groupBy { (node, _) -> node.geneticsCluster() }
                .map { (cluster, links) ->
                    val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                    val ingoing = distinctUsages
                        .filter { (from, to) -> from.geneticsCluster() != cluster && to.geneticsCluster() == cluster }
                        .map { (from, _) -> from.geneticsCluster() }
                        .distinct()
                    val outgoing = distinctUsages
                        .filter { (from, to) -> from.geneticsCluster() == cluster && to.geneticsCluster() != cluster }
                        .map { (_, to) -> to.geneticsCluster() }
                        .distinct()
                    val dataClasses = links.map { it.first }.filter {
                        it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure()
                    }.distinct()
                    val serviceClasses =
                        links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                    GeneticsCluster(cluster, ingoing + outgoing, dataClasses, serviceClasses)
                }
        }
    }

    data class GeneticsCluster(
        val name: String,
        val dependenciesToOtherClusters: List<String>,
        val dataClasses: List<Any>,
        val serviceClasses: List<Any>
    ) {

       /* val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size

        fun encapsulated() = dependenciesToOtherClustersCount == 0


        */
    }









