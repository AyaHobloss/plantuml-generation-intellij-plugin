package com.kn.diagrams.generator.generator.code


import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.generator.containingClass
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt


data class GeneticsClustering(val nodes:List<String>, val edges:List<Pair<String, String>>,val iterations:Int,
                              val parentSize:Int, val childSize:Int,
                              val crossoverRate:Double, val mutationRate:Double) {

    fun GeneticsClusterDiagramContext.dependencyMatrix(): Array<IntArray> {

        val nodes = baseEdges.flatMap { it.nodes() }.map { it.nameInCluster() }.distinct()
       val edgesBesser = baseEdges.flatMap { listOf(it.from()?.cluster()!! to it.cluster(), it.to()!!.cluster()!! to it.cluster()) }


        val edges = baseEdges.flatMap { listOf(it.from()!! to it, it.to()!! to it) }

        var nodesNb = nodes.size

        var dependencyMatrix = arrayOf<IntArray>()
        for (i in 0 until nodesNb) {

            for (j in 0 until nodesNb) {

                if (nodes[j].cluster() == edges[i].second.nodes().cluster() &&
                    nodes[i].cluster() == edges[i].first.cluster()
                    ||
                    nodes[i].cluster() == edges[i].second.nodes().cluster() &&
                    nodes[j].cluster() == edges[i].first.cluster()

                )

                    dependencyMatrix[i][j] = 1
                else

                    dependencyMatrix[i][j] = 0
            }
        }

        return dependencyMatrix


    }
    fun getDependencyMatrix(c:GeneticsClusterDiagramContext): Array<IntArray>{

        return c.dependencyMatrix()
    }

    fun dependencyMatrix(nodes:List<String>,edges: List<Pair<String, String>>): Array<IntArray>{

        var nodesNb = nodes.size

        var dependencyMatrix = Array(nodesNb) { IntArray(nodesNb) }


        if(nodesNb==0 || nodesNb<0)
        {
            throw NullPointerException("$nodesNb")
        }

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

        for (i in 0 until parentSize) {

            var individual = IntArray(nodesNb)


            for (i in 0 until nodesNb) {

                //alle nachbarn von node i in einem array um ein random nachbar auszuwählen
                var nachbar = mutableListOf<Int>()

                for (j in 0 until nodesNb) {

                    if (roulette[i][j] != 0.0) {

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


    fun encoding(individual: IntArray): IntArray {
        var labels = IntArray(individual.size)

        var communities = HashSet<MutableList<Int>>()
        var com1 = mutableListOf(0, individual[0])

        communities.add(com1)




        for (k in 0 until communities.size) {
            var com = mutableListOf<Int>()
            for (i in 1 until individual.size) {
                if (communities.elementAt(k).contains(i) || communities.elementAt(k).contains(individual[i])) {
                    communities.elementAt(k).add(i)
                    communities.elementAt(k).add(individual[i])

                } else {

                    com.add(i)
                    com.add(individual[i])
                }

            }
            if (com.size != 0)
                communities.add(com)


        }


        for (i in 0 until individual.size) {
            for (j in 0 until communities.size) {
                if (communities.elementAt(j).contains(individual[i]))

                    labels[i] = j + 1


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

    ): MutableMap<String, String> {

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


        return nodeMLabel


    }


}


    data class GeneticConfig(val iterations:Int,
                             val parentSize:Int, val childSize:Int,
                             val crossoverRate:Double, val mutationRate:Double)

   fun clusterByGenetic(
        config: GeneticConfig,
        nodes: List<String>,
        edges: List<Pair<String, String>>,
        genetic:GeneticsClustering
    ): ClusterDefinition {

       return ClusterDefinition(genetic.LSSGA(nodes,edges,
           config.iterations,config.parentSize,config.childSize,
           config.crossoverRate,config.mutationRate))

    }

fun GeneticsClusterDiagramContext.loadGeneticsClusters(): ClusterDefinition {
    val nodes = baseEdges.flatMap { it.nodes() }.map { it.nameInCluster() }.distinct()
    val edges = baseEdges.flatMap { listOf((it.from()?.nameInCluster()) to (it.to()?.nameInCluster()) )}
            as List<Pair<String, String>>


    File(".\\genetics_graph_${nodes.hashCode()}.txt")
        .createIfNotExists()
        .writeText(createGeneticsEdges(nodes))

    //edgesToGeneticsClusters()

        with(configGenetics.details.LSSGA){
            return clusterByGenetic(GeneticConfig(
                iterations,
                parentSize,
                childSize,
                crossoverRate,
                mutationRate
            ), nodes,
                edges , GeneticsClustering(nodes,edges,iterations,parentSize,childSize,crossoverRate,mutationRate)
            )
        }
    }



fun GeneticsClusterDiagramContext.createGeneticsEdges(nodes: List<String>) = baseEdges.asSequence()
    .flatMap { sequenceOf(nodes.indexOf(it.from()!!.nameInCluster()) to nodes.indexOf(it.to()!!.nameInCluster()),
        nodes.indexOf(it.to()!!.nameInCluster()) to nodes.indexOf(it.from()!!.nameInCluster())) }
    .groupBy { it }
    .entries.map { (key, values) -> Triple(key.first, key.second, values.size) }
    .sortedWith(compareBy({ it.first }, { it.second }))
    .map { ""+it.first + "\t" + it.second + "\t" + it.third }
    .joinToString("\n")

// TODO unify with datastructure ClusterNode; add comment in config that services/dataclasses get balanced - configure the weight?

fun GeneticsClusterDiagramContext.edgesToGeneticsClusters(): List<GeneticsCluster> {

    with(geneticsVisualConfig.projectClassification){
        return baseEdges.flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregate() to edge }
            .groupBy { (node, _) -> node.cluster() }
            .map { (cluster, links) ->
                val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                val ingoing = distinctUsages
                    .filter { (from, to) -> from.cluster() != cluster && to.cluster() == cluster }
                    .map { (from, _) -> from.cluster() }
                    .distinct()
                val outgoing = distinctUsages
                    .filter { (from, to) -> from.cluster() == cluster && to.cluster() != cluster }
                    .map { (_, to) -> to.cluster() }
                    .distinct()
                val dataClasses = links.map { it.first }.filter { it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure() }.distinct()
                val serviceClasses = links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                GeneticsCluster(cluster, ingoing + outgoing, dataClasses, serviceClasses)
            }
    }
}
data class GeneticsCluster(val name: String, val dependenciesToOtherClusters: List<String>, val dataClasses: List<Any>, val serviceClasses: List<Any>){

    val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size

    fun encapsulated() = dependenciesToOtherClustersCount == 0

}









