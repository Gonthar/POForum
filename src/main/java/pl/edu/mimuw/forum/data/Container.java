package pl.edu.mimuw.forum.data;

/**
 * Created by Maciek on 14/09/2016.
 */

import java.util.List;
import java.util.LinkedList;

import pl.edu.mimuw.forum.exceptions.ApplicationException;

public class Container {

    private List<Node> children = new LinkedList<>();

    public Node getRoot() throws ApplicationException {
        if (children.size() != 1) {
            throw new ApplicationException(new Throwable("Can't have more than one root node."));
        }
        return children.get(0);
    }

    public void setRoot(Node rootNode) {
        children.clear();
        children.add(rootNode);
    }
}