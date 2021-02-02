package com.kn.diagrams.clustering.algorithm;


import com.intellij.openapi.progress.ProgressManager;
import com.kn.diagrams.clustering.model.GAGraph;
import com.kn.diagrams.clustering.model.GANode;
import org.tinylog.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class GAConnectedComponents {

    private final int lvlOfConnectivity;
    private GAGraph graph;
    private List<String> nodesToInspect;

    public GAConnectedComponents(GAGraph graph, int lvlOfConnectivity) {
        this.graph = graph;
        this.lvlOfConnectivity = lvlOfConnectivity;
        this.nodesToInspect = graph.getNodes().stream().filter(n -> !n.isMdm()).map(GANode::getId).collect(Collectors.toList());
    }

    public List<List<GANode>> runAlgorithm() {
        Logger.debug("started connected components algorithm");
        Logger.debug("level of connectivity: {}", lvlOfConnectivity);
        List<List<GANode>> res = new ArrayList<>();
        while (nodesToInspect.size() > 0) {
            Logger.debug("{} nodes left to inspect", nodesToInspect.size());
            List<GANode> component = new LinkedList<>();
            //choose random node
            GANode node = graph.getNodeById(nodesToInspect.get(0));
            if (addNodeToComponent(node, component)) {

                //int numberOfOutgoingEdges;
                Map<GANode, Integer> counterMap;
                do {

                    counterMap = getCounterMap(component);
                    Map.Entry<GANode, Integer> entry = counterMap.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
                    if(entry != null){
                        GANode adjacentNode = entry.getKey();
                        addNodeToComponent(adjacentNode, component);
                        //numberOfOutgoingEdges = getNumberOfAdjacent(component);
                    }
                } while (lvlOfConnectivity <= counterMap.size() && counterMap.size() > 0);

                counterMap.keySet()
                        .stream().filter(n ->
                        graph.getAdjacentNodes(n).stream().filter(n1 -> !component.contains(n1)).count()
                                < graph.getAdjacentNodes(n).stream().filter(component::contains).count()
                ).forEach(n -> addNodeToComponent(n, component));

                Logger.debug("component found: {}", component.toString());
                res.add(component);
            }
        }
        return res;
    }

    private boolean addNodeToComponent(GANode node, List<GANode> component) {
        if (node != null && nodesToInspect.contains(node.getId())) {
            component.add(node);
            nodesToInspect.remove(node.getId());
            if(ProgressManager.getGlobalProgressIndicator() != null){
                ProgressManager.getGlobalProgressIndicator().setText("Diagram is generated ("+nodesToInspect.size()+")");
            }
            return true;
        }
        return false;
    }

    private int getNumberOfAdjacent(List<GANode> component) {
        return component.stream().mapToInt(node ->
                (int) graph.getAdjacentNodes(node)
                        .stream().filter(n -> !component.contains(n)).count()).sum();
    }

    private GANode getNodeWithHighestCounter(List<GANode> component) {
        Map<GANode, Integer> counterMap = getCounterMap(component);
        Map.Entry<GANode, Integer> res = counterMap.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
        if (res == null)
            return null;
        return res.getKey();
    }

    private Map<GANode, Integer> getCounterMap(List<GANode> component) {
        Map<GANode, Integer> counterMap = new HashMap<>();
        component.forEach(node -> {
            List<GANode> adjacentNodes = graph.getAdjacentNodes(node);
            adjacentNodes.forEach(aNode -> {
                if (!component.contains(aNode) && nodesToInspect.contains(aNode.getId())) {
                    if (counterMap.containsKey(aNode)) {
                        counterMap.put(aNode, counterMap.get(aNode) + 1);
                    } else {
                        counterMap.put(aNode, 1);
                    }
                }
            });
        });
        return counterMap;
    }

}
