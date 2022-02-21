package examples.customization.replace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class Modification {
    protected final String key;
    protected final List<Function<String, String>> steps = new ArrayList<>();

    public Modification(String key) {
        this.key = key;
    }

    abstract public String replaceAll(String diagram);


    public static String modify(String diagram, Modification... modifications){
        for (Modification modification: modifications) {
            diagram = modification.replaceAll(diagram);
        }
        return diagram;
    }
}
