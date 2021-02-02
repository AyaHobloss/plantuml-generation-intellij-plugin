package com.kn.diagrams.clustering.model;

public class GANode {

    private String id;
    private String name;
    private String pkg;
    private boolean mdm;

    public GANode(String id, String name, String pkg, boolean mdm) {
        this.id = id;
        this.name = name;
        this.pkg = pkg;
        this.mdm = mdm;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPkg() {
        return pkg;
    }

    public boolean isMdm() {
        return mdm;
    }

    @Override
    public String toString() {
        return "GANode{id='" + id + "', name='" + name + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GANode node = (GANode) o;

        return id.equals(node.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
