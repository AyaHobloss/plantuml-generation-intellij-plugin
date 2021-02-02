package com.kn.diagrams.clustering.model;

public class GAEdge {

    private String id;
    private GANode fromNode;
    private GANode toNode;
    private GAEdgeType type;
    private boolean inverted;
    private boolean required;
    private boolean listener;

    public GAEdge(GANode fromNode, GANode toNode, GAEdgeType type, boolean inverted, boolean required, boolean listener) {
        this.id = fromNode.getId()+"_"+toNode.getId();
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.type = type;
        this.inverted = inverted;
        this.required = required;
        this.listener = listener;
    }

    public String getId() {
        return id;
    }

    public GANode getFromNode() {
        return fromNode;
    }

    public GANode getToNode() {
        return toNode;
    }

    public GAEdgeType getType() {
        return type;
    }

    public boolean getInverted() {
        return inverted;
    }

    public boolean getRequired() {
        return required;
    }

    public boolean getListener() {
        return listener;
    }
}
