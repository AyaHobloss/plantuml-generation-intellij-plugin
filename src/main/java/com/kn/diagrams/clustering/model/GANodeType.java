package com.kn.diagrams.clustering.model;

public enum GANodeType {
    ENTITY("entity"), RICHCLIENT("richclient"), SERVICE("service"), SUPPORT("support"), USECASEHANDLER("usecasehandler"),
    ASSEMBLER("assembler");

    public final String type;
    GANodeType(String type) {
        this.type = type;
    }
}
