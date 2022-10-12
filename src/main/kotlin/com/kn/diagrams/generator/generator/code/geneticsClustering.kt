package com.kn.diagrams.generator.generator.code


import com.kn.diagrams.generator.config.GeneticsParametersVariation
import com.kn.diagrams.generator.config.serializer
import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.generator.containingClass
import com.kn.diagrams.generator.throwExceptionIfCanceled
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.streams.toList


data class GeneticsClustering(val nodes:List<String>, val edges:List<Pair<String, String>>,val iterations:Int,
                              val parentSize:Int, val childSize:Int,
                              val crossoverRate:Double, val mutationRate:Double) {


    fun dependencyMatrix(nodes:List<String>,edges: List<Pair<String, String>>): Array<IntArray>{

        var nodesNb = nodes.size

        if(nodes.size==0)
        {
            throw NullPointerException("kein nodes")
        }
        if(edges.size==0)
        {
            throw NullPointerException("kein edges")
        }
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

    }


    fun degree(nodeIndex: Int, dependencyMatrix: Array<IntArray>): Int {

        return dependencyMatrix[nodeIndex].sum()

    }

    fun nodesNeighbors(dependencyMatrix: Array<IntArray>, nodesNb: Int): LinkedHashMap<Int, MutableList<Int>> {
        val nodeNeighbors = LinkedHashMap<Int, MutableList<Int>>()

        for (i in 0 until nodesNb) {
            var neighbors = mutableListOf<Int>()

            for (j in 0 until nodesNb) {
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

    fun structuralSimilarityMatrix(dependencyMatrix: Array<IntArray>, nodesNb: Int): Array<DoubleArray> {


        val structuralSimilarity = Array(nodesNb) { DoubleArray(nodesNb) }

        val nodeNeighbors = nodesNeighbors(dependencyMatrix, nodesNb)
        var commonNeighbors: MutableList<Any>


        for (i in 0 until nodesNb) {
            for (j in 0 until nodesNb) {
                if (dependencyMatrix[i][j] == 1) {

                    commonNeighbors =
                        nodeNeighbors[j]?.let { nodeNeighbors[i]!!.intersect(it.toSet()) }!!.toMutableList()

                    //  println("common$commonNeighbors")
                    if (commonNeighbors.size == 0) {
                        /*structuralSimilarity[i][j] = 0.0
                    println("i$i  j$j")
                    println(structuralSimilarity[i][j])
                    */
                        commonNeighbors = mutableListOf(i, j)
                    }


                    var numerator = sumCommonNeighborsDegree(dependencyMatrix, commonNeighbors)


                    var denominator =
                        (sumReciprocalNeighborDegree(
                            dependencyMatrix,
                            i, nodesNb
                        ).pow(0.5)) * (sumReciprocalNeighborDegree(dependencyMatrix, j, nodesNb).pow(0.5))



                    structuralSimilarity[i][j] = numerator / denominator
                    /*println("i$i  j$j")
                println(structuralSimilarity[i][j])
               */

                }


                if (dependencyMatrix[i][j] == 0) {
                    structuralSimilarity[i][j] = 0.0
                    /* println("i$i  j$j")
                 println(structuralSimilarity[i][j])
                */
                }



                if (i == j) {
                    structuralSimilarity[i][j] = 0.0
                    /*  println("i$i  j$j")
                  println(structuralSimilarity[i][j])
               */
                }


            }

        }


        return structuralSimilarity
    }

    fun sumCommonNeighborsDegree(dependencyMatrix: Array<IntArray>, commonNeighbors: MutableList<Any>): Double {

        var reciprocal = DoubleArray(commonNeighbors!!.size)
        for (i in 0 until commonNeighbors!!.size) {
            reciprocal[i] = degree(commonNeighbors[i].toString().toInt(), dependencyMatrix).toDouble().pow(-1)
        }
        return reciprocal.sum()

    }

    fun sumReciprocalNeighborDegree(dependencyMatrix: Array<IntArray>, index: Int, nodesNb: Int): Double {

        var nodeNeighbors = nodesNeighbors(dependencyMatrix, nodesNb)
        var neighbors = nodeNeighbors[index]

        var reciprocal = DoubleArray(neighbors!!.size)
        for (i in 0 until neighbors!!.size) {

            reciprocal[i] = degree(neighbors[i], dependencyMatrix).toDouble().pow(-1)

        }

        return reciprocal.sum()
    }

    fun sumNeighborsSimilarity(index: Int, dependencyMatrix: Array<IntArray>, nodesNb: Int): Double {

        val nodeNeighbors = nodesNeighbors(dependencyMatrix, nodesNb)
        val structuralSimilarity = structuralSimilarityMatrix(dependencyMatrix, nodesNb)

        var neighbors = nodeNeighbors[index]
        var similarity = DoubleArray(neighbors!!.size)

        for (i in 0 until neighbors!!.size) {
            similarity[i] = structuralSimilarity[index][neighbors[i]]
        }

        return similarity.sum()

    }


    fun rouletteWheelSelection(dependencyMatrix: Array<IntArray>, nodesNb: Int): Array<DoubleArray> {

        val roulette = Array(nodesNb) { DoubleArray(nodesNb) }
        val structuralSimilarity = structuralSimilarityMatrix(dependencyMatrix, nodesNb)

        for (i in 0 until nodesNb) {
            for (j in 0 until nodesNb) {

                if (structuralSimilarity[i][j] == 0.0)
                    roulette[i][j] = 0.0
                else
                    roulette[i][j] = structuralSimilarity[i][j] / sumNeighborsSimilarity(i, dependencyMatrix, nodesNb)

            }

        }

        return roulette
    }


    fun initialPopulation(parentSize: Int, dependencyMatrix: Array<IntArray>, nodesNb: Int): HashSet<IntArray> {

        val population = HashSet<IntArray>()

        val roulette = rouletteWheelSelection(dependencyMatrix, nodesNb)

        for (k in 0 until parentSize) {

            var individual = IntArray(nodesNb)


            for (i in 0 until nodesNb) {

                //alle nachbarn von node i in einem array um ein random nachbar auszuwählen
                var nachbar = mutableListOf<Int>()

                for (j in 0 until nodesNb) {

                    if (roulette[i][j] > 0.0) {

                        nachbar.add(j)


                    }

                }
                if(nachbar.size>0)
                individual[i] = nachbar.random()


            }


            population.add(individual)
            // println(population.elementAt(i).toMutableList())
        }


        return population

    }

    fun modularity(individual: IntArray, dependencyMatrix: Array<IntArray>, nodesNb: Int): Double {


        var sumOfRows = IntArray(nodesNb)

        for (i in 0 until nodesNb) {
            sumOfRows[i] = dependencyMatrix[i].sum()
        }
        // hier meinen 2*m = sum of elements of the matrix
        val m = sumOfRows.sum()

        var modularity = 0.0

        var md = mutableListOf<Double>()

        for (i in 0 until nodesNb) {
            for (j in 0 until nodesNb) {

                if (individual[i] == j) {

                    md.add(
                        (dependencyMatrix[i][j] -
                                ((degree(i, dependencyMatrix) * degree(j, dependencyMatrix)).toDouble().div(m)))
                    )


                } else
                    md.add(0.0)


            }
        }
        modularity = md.sum()


        return ((1.0.div(m)) * modularity)
    }

    fun crossover(individual1: IntArray, individual2: IntArray): IntArray {

        var randomArray = IntArray(individual1.size)
        var individual3 = IntArray(individual1.size)

        for (i in 0 until individual1.size) {
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
        var labels =IntArray(individual.size)

        var communities= mutableListOf<MutableList<Int>>()
        var com1 = mutableListOf<Int>()
        com1.add(0)
        com1.add(individual[0])
        communities.add(com1)
        var com = mutableListOf<Int>()

        for(k in 0 until communities.size)
        {

            for (i in 1 until individual.size)
            {
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


        for(i in 0 until individual.size)
        {
            for(j in 0 until communities.size)
            {
                if(communities.elementAt(j).contains(individual[i]))

                    labels[i]=j+1


            }
        }


        return labels
    }
    fun LPLSS(individual: IntArray, dependencyMatrix: Array<IntArray>): IntArray {

        var mutatedC = individual
        var structSim = structuralSimilarityMatrix(dependencyMatrix, individual.size)
        var marginalGenes = mutableListOf<Int>()
        for (i in individual.indices) {
            if (!individual.contains(i)) {
                marginalGenes.add(i)
            }

        }
        if (marginalGenes.size != 0) {
            var neighbors = nodesNeighbors(dependencyMatrix, individual.size)
            var labels = encoding(individual)
            var neighLabels = mutableListOf<Int>()

            for (i in 0 until marginalGenes.size) {
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
                        for (j in 0 until n.size) {
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
                        for (j in 0 until n.size) {
                            if (labels[n[j]] == mostLabel)

                                neigMostLabel.add(n[j])

                        }

                        if (neigMostLabel.size == 1)
                            mutatedC[marginalGenes[i]] = neigMostLabel[0]
                        else {
                            var mostStruct = 0
                            for (j in 0 until neigMostLabel.size - 1) {
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


    fun LSSGA(
        nodes: List<String>,
        edges: List<Pair<String, String>>,

        iterations: Int,
        parentSize: Int,
        childSize: Int,
        crossoverRate: Double,
        mutationRate: Double,

    ): Map<String, String> {

        var dependencyMatrix = dependencyMatrix(nodes, edges)
        var structuralSimilarityMatrix = structuralSimilarityMatrix(dependencyMatrix, nodes.size)

        var initialPopulation = initialPopulation(parentSize, dependencyMatrix, nodes.size)



        for (g in 1..iterations) {
            var childPopulation = HashSet<IntArray>()

            for (i in 1..childSize) {
                var chromo = IntArray(nodes.size)


                var individual1 = initialPopulation.random()
                var individual2 = initialPopulation.random()

                if (modularity(individual1, dependencyMatrix, nodes.size) != 0.0 &&
                    modularity(individual2, dependencyMatrix, nodes.size) != 0.0
                ) {
                    chromo = crossover(individual1, individual2)

                }

                var C = LPLSS(chromo, dependencyMatrix)
                childPopulation.add(C)

            }
            initialPopulation.addAll(childPopulation)

            var rankedPopulation = initialPopulation.toList()
                .map { modularity(it, dependencyMatrix, nodes.size) to it }
                .sortedByDescending { it.first }
                .take(parentSize)
                .map { it.second }
                .toHashSet()

            initialPopulation = rankedPopulation


        }


        var bestIndividuals = initialPopulation.toList()
            .map { modularity(it, dependencyMatrix, nodes.size) to it }
            .sortedByDescending { it.first }
            .take(1)
            .map { it.second }[0]


        var labels = encoding(bestIndividuals)

        var nodeMLabel = mutableMapOf<String, String>()

        for (i in 0 until nodes.size) {
            nodeMLabel[nodes[i]] = "Cluster_${labels[i]}"

        }


        return nodeMLabel.toMap()


    }


}
data class GeneticsConfigVariation(var iteration: List<Int>, var parentSize: List<Int>, var childSize: List<Int>, var crossoverRate: List<Double>, var mutationRate: List<Double>){
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

    data class GeneticConfig(val iterations:Int,
                             val parentSize:Int, val childSize:Int,
                             val crossoverRate:Double, val mutationRate:Double)

   fun clusterByGenetic(
        config: GeneticConfig,
        nodes: List<String>,
        edges: List<Pair<String, String>>,
        genetic:GeneticsClustering
    ): GeneticsClusterDefinition {

       return GeneticsClusterDefinition(genetic.LSSGA(nodes,edges,
           config.iterations,config.parentSize,config.childSize,
           config.crossoverRate,config.mutationRate))

    }


fun GeneticsClusterDiagramContext.loadGeneticsClusters(): GeneticsClusterDefinition {
    val nodes = baseEdgesGenetics.flatMap { it.nodes() }.map { it.nameInClusterGenetics() }.distinct()
    val edges =
        baseEdgesGenetics.flatMap { listOf((it.from()?.nameInClusterGenetics()) to (it.to()?.nameInClusterGenetics())) }
                as List<Pair<String, String>>

    File(".\\genetics_graph_${nodes.hashCode()}.txt")
        .createIfNotExists()
        .writeText(createGeneticsEdges(nodes))

    if (configGenetics.details.LSSGA.geneticsOptimizeClusterDistribution) {
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

    /*val c = configGenetics.details.LSSGA
    val gc = GeneticConfig(c.iterations,c.parentSize,c.childSize,c.crossoverRate,c.mutationRate)
    val g = GeneticsClustering(nodes, edges, c.iterations, c.parentSize, c.childSize, c.crossoverRate, c.mutationRate)
    val cg = clusterByGenetic(gc,nodes,edges,g)

    return cg

     */
       /* with(configGenetics.details.LSSGA) {
            return clusterByGenetic(
                GeneticConfig(
                    iterations,
                    parentSize,
                    childSize,
                    crossoverRate,
                    mutationRate
                ), nodes,
                edges, GeneticsClustering(nodes, edges, iterations, parentSize, childSize, crossoverRate, mutationRate)
            )
        }

        */

fun scoreGenetics(clusters: List<GeneticsCluster>): Double{
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

        val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size

        fun encapsulated() = dependenciesToOtherClustersCount == 0

    }









