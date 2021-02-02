package com.kn.diagrams.clustering.model;

import java.util.ArrayList;
import java.util.List;

public class GAGraph {

    private List<GANode> nodes;
    private List<GAEdge> edges;

    public GAGraph(List<GANode> nodes, List<GAEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public GANode getNodeById(String id) {
        return nodes.stream().filter(node -> node.getId().equals(id)).findFirst().orElse(null);
    }

    public GANode getNodeByIndex(int index) {
        return nodes.get(index);
    }

    public List<GANode> getNodes() {
        return nodes;
    }

    public boolean hasEdgeBetween(GANode fromNode, GANode toNode) {
        return edges.stream().anyMatch(e -> e.getFromNode().equals(fromNode) && e.getToNode().equals(toNode));
    }

    public List<GANode> getAdjacentNodes(GANode node) {
        List<GANode> res = new ArrayList<>();
        edges.stream().filter(edge -> !edge.getType().equals(GAEdgeType.DIRECT_ENTITY)).forEach(edge -> {
            if (edge.getFromNode().getId().equals(node.getId())) {
                res.add(edge.getToNode());
            } else if (edge.getToNode().getId().equals(node.getId())) {
                res.add(edge.getFromNode());
            }
        });
        return res;
    }

}
