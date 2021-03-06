package pl.edu.mimuw.forum.ui.controllers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import com.thoughtworks.xstream.io.xml.StaxDriver;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import pl.edu.mimuw.forum.data.Container;
import pl.edu.mimuw.forum.data.Comment;
import pl.edu.mimuw.forum.data.Suggestion;
import pl.edu.mimuw.forum.data.Survey;
import pl.edu.mimuw.forum.data.Task;
import pl.edu.mimuw.forum.example.Dummy;
import pl.edu.mimuw.forum.exceptions.ApplicationException;
import pl.edu.mimuw.forum.ui.bindings.MainPaneBindings;
import pl.edu.mimuw.forum.ui.helpers.DialogHelper;
import pl.edu.mimuw.forum.ui.models.CommentViewModel;
import pl.edu.mimuw.forum.ui.models.NodeViewModel;
import pl.edu.mimuw.forum.ui.tree.ForumTreeItem;
import pl.edu.mimuw.forum.ui.tree.TreeLabel;


import java.io.*;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import sun.misc.JavaIOFileDescriptorAccess;

/**
 * Kontroler glownego widoku reprezentujacego forum.
 * Widok sklada sie z drzewa zawierajacego wszystkie wezly forum oraz
 * panelu z polami do edycji wybranego wezla.
 * @author konraddurnoga
 */
public class MainPaneController implements Initializable {

	/**
	 * Korzen drzewa-modelu forum.
	 */
	private NodeViewModel document;

	/**
	 * Wiazania stosowane do komunikacji z {@link pl.edu.mimuw.forum.ui.controllers.ApplicationController }.
	 */
	private MainPaneBindings bindings;

	/**
	 * Widok drzewa forum (wyswietlany w lewym panelu).
	 */
	@FXML
	private TreeView<NodeViewModel> treePane;

	/**
	 * Kontroler panelu wyswietlajacego pola do edycji wybranego wezla w drzewie.
	 */
	@FXML
	private DetailsPaneController detailsController;

	private Stack undoList;
	private Stack redoList;

	public MainPaneController() {
		bindings = new MainPaneBindings();
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		BooleanBinding nodeSelectedBinding = Bindings.isNotNull(treePane.getSelectionModel().selectedItemProperty());
		bindings.nodeAdditionAvailableProperty().bind(nodeSelectedBinding);
		bindings.nodeRemovaleAvailableProperty()
				.bind(nodeSelectedBinding.and(
						Bindings.createBooleanBinding(() -> getCurrentTreeItem().orElse(null) != treePane.getRoot(),
								treePane.rootProperty(), nodeSelectedBinding)));

		bindings.hasChangesProperty().set(true);        // TODO Nalezy ustawic na true w przypadku, gdy w widoku sa zmiany do
		// zapisania i false wpp, w odpowiednim miejscu kontrolera (niekoniecznie tutaj)
		// Spowoduje to dodanie badz usuniecie znaku '*' z tytulu zakladki w ktorej
		// otwarty jest plik - '*' oznacza niezapisane zmiany
		bindings.undoAvailableProperty().set(false);
		bindings.redoAvailableProperty().set(false);        // Podobnie z undo i redo
	}

	public MainPaneBindings getPaneBindings() {
		return bindings;
	}

	private XStream setupXStream() {
		XStream xstream = new XStream();
		xstream.autodetectAnnotations(true);
		xstream.alias("Forum", Container.class);
		xstream.addImplicitCollection(pl.edu.mimuw.forum.data.Node.class, "children");
		xstream.addImplicitCollection(Container.class, "children");
	return xstream;
	}

	/**
	 * Otwiera plik z zapisem forum i tworzy reprezentacje graficzna wezlow forum.
	 *
	 * @param file
	 * @return
	 * @throws ApplicationException
	 */
	public Node open(File file) throws ApplicationException {
		if (file != null) {
			try {
				XStream xstream = setupXStream();
				Reader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
				Container forum = (Container) xstream.fromXML(rdr);
				document = new NodeViewModel(forum.getRoot());
				//document = Dummy.Create().getModel();
			} catch (java.io.IOException e) {
				e.printStackTrace();
			}
		} else {
			document = new CommentViewModel("Welcome to a new forum", "Admin");
		}
		/** Dzieki temu kontroler aplikacji bedzie mogl wyswietlic nazwe pliku jako tytul zakladki.
		 * Obsluga znajduje sie w {@link pl.edu.mimuw.forum.ui.controller.ApplicationController#createTab }
		 */
		getPaneBindings().fileProperty().set(file);
		bindings.hasChangesProperty().set(false);
		return openInView(document);
	}

	/**
	 * Zapisuje aktualny stan forum do pliku.
	 *
	 * @throws ApplicationException
	 */
	public void save() throws ApplicationException {

		try {
			XStream xstream = setupXStream();
			Container forum = new Container();
			forum.setRoot(document.toNode());
			PrintWriter pw = new PrintWriter(getPaneBindings().fileProperty().get(), "UTF-8");
			xstream.toXML(forum, pw);
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		if(document != null) {
			System.out.println("On save " + document.toNode());    //Tak tworzymy drzewo do zapisu z modelu aplikacji
		}
		bindings.hasChangesProperty().set(false);
	}

	
	/**
	 * Cofa ostatnio wykonana operacje na forum.
	 * @throws ApplicationException
	 */
	public void undo() throws ApplicationException {
		System.out.println("On undo");	//TODO Tutaj umiescic obsluge undo
	}

	/**
	 * Ponawia ostatnia cofnieta operacje na forum.
	 * @throws ApplicationException
	 */
	public void redo() throws ApplicationException {
		System.out.println("On redo");	//TODO Tutaj umiescic obsluge redo
	}

	/**
	 * Podaje nowy wezel jako ostatnie dziecko aktualnie wybranego wezla.
	 * @param node
	 * @throws ApplicationException
	 */
	public void addNode(NodeViewModel node) throws ApplicationException {
		bindings.redoAvailableProperty().set(false);
		if (redoList != null) {
			redoList.clear();
		}
		getCurrentNode().ifPresent(currentlySelected -> {
			currentlySelected.getChildren().add(node);		// Zmieniamy jedynie model, widok (TreeView) jest aktualizowany z poziomu
															// funkcji nasluchujacej na zmiany w modelu (zob. metode createViewNode ponizej)
		});
	}

	/**
	 * Usuwa aktualnie wybrany wezel.
	 */
	public void deleteNode() {
		bindings.redoAvailableProperty().set(false);
		if (redoList != null) {
			redoList.clear();
		}
		getCurrentTreeItem().ifPresent(currentlySelectedItem -> {
			TreeItem<NodeViewModel> parent = currentlySelectedItem.getParent();

			NodeViewModel parentModel;
			NodeViewModel currentModel = currentlySelectedItem.getValue();
			if (parent == null) {
				return; // Blokujemy usuniecie korzenia - TreeView bez korzenia jest niewygodne w obsludze
			} else {
				parentModel = parent.getValue();
				parentModel.getChildren().remove(currentModel); // Zmieniamy jedynie model, widok (TreeView) jest aktualizowany z poziomu
																// funkcji nasluchujacej na zmiany w modelu (zob. metode createViewNode ponizej)
			}

		});
	}

	private Node openInView(NodeViewModel document) throws ApplicationException {
		Node view = loadFXML();

		treePane.setCellFactory(tv -> {
			try {
				//Do reprezentacji graficznej wezla uzywamy niestandardowej klasy wyswietlajacej 2 etykiety
				return new TreeLabel();
			} catch (ApplicationException e) {
				DialogHelper.ShowError("Error creating a tree cell.", e);
				return null;
			}
		});

		ForumTreeItem root = createViewNode(document);
		root.addEventHandler(TreeItem.<NodeViewModel> childrenModificationEvent(), event -> {
			//TODO Moze przydac sie do wykrywania usuwania/dodawania wezlow w drzewie (widoku)
			if (event.wasAdded()) {
				bindings.hasChangesProperty().set(true);
				System.out.println("Adding to " + event.getSource());
			}
			
			if (event.wasRemoved()) {
				bindings.hasChangesProperty().set(true);
				System.out.println("Removing from " + event.getSource());
			}
		});

		treePane.setRoot(root);

		for (NodeViewModel w : document.getChildren()) {
			addToTree(w, root);
		}

		expandAll(root);

		treePane.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> onItemSelected(oldValue, newValue));

		return view;
	}
	
	private Node loadFXML() throws ApplicationException {
		FXMLLoader loader = new FXMLLoader();
		loader.setController(this);
		loader.setLocation(getClass().getResource("/fxml/main_pane.fxml"));

		try {
			return loader.load();
		} catch (IOException e) {
			throw new ApplicationException(e);
		}
	}
	
	private Optional<? extends NodeViewModel> getCurrentNode() {
		return getCurrentTreeItem().<NodeViewModel> map(TreeItem::getValue);
	}

	private Optional<TreeItem<NodeViewModel>> getCurrentTreeItem() {
		return Optional.ofNullable(treePane.getSelectionModel().getSelectedItem());
	}

	private void addToTree(NodeViewModel node, ForumTreeItem parentViewNode, int position) {
		ForumTreeItem viewNode = createViewNode(node);

		List<TreeItem<NodeViewModel>> siblings = parentViewNode.getChildren();
		siblings.add(position == -1 ? siblings.size() : position, viewNode);

		node.getChildren().forEach(child -> addToTree(child, viewNode));
	}

	private void addToTree(NodeViewModel node, ForumTreeItem parentViewNode) {
		addToTree(node, parentViewNode, -1);
	}

	private void removeFromTree(ForumTreeItem viewNode) {
		viewNode.removeChildListener();
		TreeItem<NodeViewModel> parent = viewNode.getParent();
		if (parent != null) {
			viewNode.getParent().getChildren().remove(viewNode);
		} else {
			treePane.setRoot(null);
		}
	}

	private ForumTreeItem createViewNode(NodeViewModel node) {
		ForumTreeItem viewNode = new ForumTreeItem(node);
		viewNode.setChildListener(change -> {	// wywolywane, gdy w modelu dla tego wezla zmieni sie zawartosc kolekcji dzieci
			while (change.next()) {
				if (change.wasAdded()) {
					int i = change.getFrom();
					for (NodeViewModel child : change.getAddedSubList()) {
						// TODO Tutaj byc moze nalezy dodac zapisywanie jaka operacja jest wykonywana
						// by mozna bylo ja odtworzyc przy undo/redo
						addToTree(child, viewNode, i);	// uwzgledniamy nowy wezel modelu w widoku
						i++;
					}
				}

				if (change.wasRemoved()) {
					for (int i = change.getFrom(); i <= change.getTo(); ++i) {
						// TODO Tutaj byc moze nalezy dodac zapisywanie jaka operacja jest wykonywana
						// by mozna bylo ja odtworzyc przy undo/redo
						removeFromTree((ForumTreeItem) viewNode.getChildren().get(i)); // usuwamy wezel modelu z widoku
					}
				}
			}
		});

		return viewNode;
	}

	private void expandAll(TreeItem<NodeViewModel> item) {
		item.setExpanded(true);
		item.getChildren().forEach(this::expandAll);
	}

	private void onItemSelected(TreeItem<NodeViewModel> oldItem, TreeItem<NodeViewModel> newItem) {
		detailsController.setModel(newItem != null ? newItem.getValue() : null);
	}

}
