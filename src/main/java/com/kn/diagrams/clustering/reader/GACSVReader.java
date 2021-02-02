package com.kn.diagrams.clustering.reader;


import com.kn.diagrams.clustering.model.GAEdge;
import com.kn.diagrams.clustering.model.GANode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GACSVReader {

    private final List<GANode> nodes;
    private final List<GAEdge> edges;

    public GACSVReader() {
        nodes = new ArrayList<>();
        edges = new ArrayList<>();
    }

    public void readFiles(String nodesFilePath, String edgesFilePath) {
        readNodesFromCSVFile(new File(nodesFilePath));
        readEdgesFromCSVFile(new File(edgesFilePath));
    }

    private void readNodesFromCSVFile(File nodesCSVFile) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(nodesCSVFile))) {
            String line;
            //skip first line
            bufferedReader.readLine();
            while ((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(",");
                nodes.add(new GANode(values[0], values[2], values[3], Boolean.getBoolean(values[6])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readEdgesFromCSVFile(File edgesCSVFile) {
    }

    private GANode getNode(String id) {
        return nodes.stream().filter(node -> node.getId().equals(id)).findFirst().orElse(null);
    }

    public List<GANode> getNodes() {
        return nodes;
    }

    public List<GAEdge> getEdges() {
        return edges;
    }
}
