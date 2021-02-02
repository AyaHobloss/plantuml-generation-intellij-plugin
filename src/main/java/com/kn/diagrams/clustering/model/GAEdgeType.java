package com.kn.diagrams.clustering.model;

public enum GAEdgeType {
    IS_USED("IS_USED"), IS_ATTRIBUTE("IS_ATTRIBUTE"), DIRECT_ENTITY("DIRECT_ENTITY");

    public final String type;

    GAEdgeType(String type) {
        this.type = type;
    }
}
