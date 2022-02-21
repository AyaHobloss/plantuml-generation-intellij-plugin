package examples.customization.replace;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EdgeModification extends Modification {

    public EdgeModification(String key) {
        super(key);
    }

    public String replaceAll(String diagram) {
        return replace(diagram, key + "[\\S\\s]*;", matcher -> {
            String edge = matcher.group();

            for (Function<String, String> step : steps) {
                edge = step.apply(edge);
            }

            return edge;
        });
    }

    public EdgeModification mandatory() {
        cardinality("[1]");
        return this;
    }

    public EdgeModification cardinality(String cardinality) {
        steps.add(edge -> edge.replaceAll("\\[[0-9\\.\\*]*\\]", cardinality));
        return this;
    }

    public EdgeModification composition() {
        steps.add(edge -> {
            edge = edge.replaceAll(", arrowhead=[a-z]*", "");
            edge = edge.replaceAll(", arrowtail=[a-z]*", "");
            edge = edge.replaceAll(", dir=[a-z]*", "");
            edge = edge.replace("];", ", arrowtail=diamond, dir=back];");

            return edge;
        });

        return this;
    }

    public EdgeModification aggregation() {
        steps.add(edge -> {
            edge = edge.replaceAll(", arrowhead=[a-z]*", "");
            edge = edge.replaceAll(", arrowtail=[a-z]*", "");
            edge = edge.replaceAll(", dir=[a-z]*", "");
            edge = edge.replace("];", ", arrowtail=odiamond, dir=back];");

            return edge;
        });

        return this;
    }

    public static String replace(String input, String regex, Function<Matcher, String> callback) {
        StringBuffer resultString = new StringBuffer();
        Matcher regexMatcher = Pattern.compile(regex).matcher(input);
        while (regexMatcher.find()) {
            regexMatcher.appendReplacement(resultString, callback.apply(regexMatcher));
        }
        regexMatcher.appendTail(resultString);

        return resultString.toString();
    }

}
