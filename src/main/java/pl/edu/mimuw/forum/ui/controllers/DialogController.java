package pl.edu.mimuw.forum.ui.controllers;



import javafx.scene.control.*;
import javafx.fxml.FXML;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import pl.edu.mimuw.forum.data.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pl.edu.mimuw.forum.ui.controllers.MainPaneController;
import pl.edu.mimuw.forum.ui.models.CommentViewModel;

/**
 * Created by Maciek on 14/09/2016.
 */
public class DialogController{

    @FXML
    private Button accept;

    @FXML
    private Button cancel;

    @FXML
    private TextField author;

    @FXML
    private TextArea content;

    private Node node;

    /**
     * Sets the node to be edited in the dialog.
     *
     * @param node
     */
    public void setNode(Node node) {
        this.node = node;
    }

    /**
     * Called when the user clicks ok.
     */
    @FXML
    private void handleOk() {
        new CommentViewModel(content.getText(), author.getText());
        Stage stage = (Stage) cancel.getScene().getWindow();
        stage.close();
    }

    /**
     * Called when the user clicks cancel.
     */
    @FXML
    private void handleCancel() {
        Stage stage = (Stage) cancel.getScene().getWindow();
        stage.close();
    }
}