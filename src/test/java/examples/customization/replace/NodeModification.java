package examples.customization.replace;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;

public class NodeModification extends Modification {

    public NodeModification(String key) {
        super(key);
    }

    public String replaceAll(String diagram) {
        try {
            String[] split = diagram.split("diagram meta data end '/");
            String diagramHeader = split[0] + "diagram meta data end '/\n\n";
            String diagramContent = split[1]
                    .replace("@enduml", "")
                    .replaceAll("'.*", "");

            MutableGraph graph = new Parser().read(diagramContent);
            graph.nodes().stream()
                    .filter(n -> n.name().value().contains(key))
                    .filter(n -> n.attrs().get("label") != null)
                    .forEach(node -> {
                        Label label = (Label) node.attrs().get("label");
                        String labelString = label.value();

                        for (Function<String, String> step : steps) {
                            labelString = step.apply(labelString);
                        }
                        if (label.isHtml()) {
                            node.attrs().add("label", Label.html(labelString));
                        } else {
                            node.attrs().add("label", Label.of(labelString));
                        }
                    });

            // unfortunately this destroys the diagram formatting
            diagram = diagramHeader + Graphviz.fromGraph(graph).render(Format.DOT) + "\n@endurml";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return diagram;
    }

    public NodeModification cardinality(String field, String cardinality) {
        steps.add(node -> Arrays.stream(node.split("\n"))
                .map(row -> {
                    if (row.contains(field)) {
                        return row.replaceAll("\\[[0-9\\.\\*]*\\]", cardinality);
                    } else {
                        return row;
                    }
                })
                .collect(Collectors.joining("\n"))
        );
        return this;
    }

    public NodeModification mandatory(String field) {
        cardinality(field, "[1]");
        return this;
    }

}
