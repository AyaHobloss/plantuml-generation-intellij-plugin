package examples.customization;

import static examples.customization.replace.Modification.modify;

import examples.customization.replace.EdgeModification;
import examples.customization.replace.NodeModification;

// class needs to be compiled before generating the diagram!
public class EdgeCustomization {

    public static String changeEdges(String diagram){
        return modify(diagram,
                new EdgeModification("ToDoList439207095 -> ToDoItem439207095")
                        .composition()
                        .cardinality("[0..n]"),
                new NodeModification("ToDoList439207095")
                        .cardinality("items", "[0..n]")

        );
    }
}

