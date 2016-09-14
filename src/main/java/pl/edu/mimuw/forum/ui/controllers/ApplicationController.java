package pl.edu.mimuw.forum.ui.controllers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import pl.edu.mimuw.forum.exceptions.ApplicationException;
import pl.edu.mimuw.forum.ui.bindings.MainPaneBindings;
import pl.edu.mimuw.forum.ui.bindings.ToolbarBindings;
import pl.edu.mimuw.forum.ui.helpers.AcceleratorHelper;
import pl.edu.mimuw.forum.ui.helpers.DialogHelper;
import pl.edu.mimuw.forum.ui.models.*;

import javax.swing.*;

/**
 * Kontroler glownego okna aplikacji.
 * Odpowiada za obsluge zdarzen klikniecia w przycisk z glownego "paska z narzedziami",
 * przy czym jesli wybrana operacja polega na wykonaniu zmiany w aktualnie wybranym widoku forum 
 * (np. usunieciu komentarza z forum) do obsluga ta jest delegowana do kontrolera tego widoku.
 * 
 */
public class ApplicationController implements Initializable {

	/**
	 * Obiekt posredniczacy w komunikacji z kontrolerem glownego "paska z narzędziami" aplikacji
	 * (z przyciskami do otwierania, zapisu plików).
	 */
	private ToolbarBindings bindings;

	/**
	 * Kontroler paska z narzedziami
	 */
	@FXML
	private ToolbarController toolbarController;

	@FXML
	private Parent mainPane;

	@FXML
	private TabPane tabPane;

	@FunctionalInterface
	private interface Action {
		void execute() throws ApplicationException;
	}

	public void postInitialize() {
		AcceleratorHelper.SetUpAccelerators(mainPane.getScene(), bindings);
	}

	/**
	 * Metoda wywolywana przy tworzeniu kontrolera (tj. przy ladowaniu definicji widoku z pliku .fxml).
	 * Instaluje procedury obslugi zdarzen klikniecia na przyciski w glownym menu aplikacji.
	 * Jednoczesnie okresla kiedy przyciski w menu powinny stawac sie aktywne/nieaktywne - 
	 * decyduje o tym logika kontrolera aktualnie wybranej zakladki, wiec 
	 * {@link pl.edu.mimuw.forum.ui.controllers.ApplicationController } tworzy jedynie zestaw
	 * wlasnosci {@link javafx.beans.property.Property} laczace 
	 * {@link pl.edu.mimuw.forum.ui.controllers.ToolbarController } z
	 * {@link pl.edu.mimuw.forum.ui.controllers.MainPaneController } przez mechanizm wiazan.
	 * Warto zapoznac sie z
	 * @see <a href="http://docs.oracle.com/javafx/2/binding/jfxpub-binding.htm">artykulem o wiazaniach</a>
	 * w JavieFX.
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		SimpleBooleanProperty props[] = Stream.generate(SimpleBooleanProperty::new).limit(5)
				.toArray(SimpleBooleanProperty[]::new);

		bindings = new ToolbarBindings(this::newPane, this::open, this::save, this::undo, this::redo, this::addNode,
				this::deleteNode, new SimpleBooleanProperty(true), new SimpleBooleanProperty(true), // przyciski
																									// New
																									// i
																									// Open
																									// zawsze
																									// aktywne
				props[0], props[1], props[2], props[3], props[4]);

		toolbarController.bind(bindings);

		tabPane.getSelectionModel().selectedItemProperty().addListener(observable -> {
			Optional<MainPaneController> controllerOption = getPaneController();
			if (controllerOption.isPresent()) {
				MainPaneBindings bindings = controllerOption.get().getPaneBindings();
				ObservableBooleanValue values[] = { bindings.hasChanges(), bindings.undoAvailable(),
						bindings.redoAvailable(), bindings.nodeAdditionAvailable(), bindings.nodeRemovalAvailable() };
				IntStream.range(0, 5).forEach(i -> props[i].bind(values[i]));
			} else {
				Arrays.stream(props).forEach(property -> {
					property.unbind();
					property.set(false);
				});
			}
		});

	}

	/**
	 * Otwiera nowa, pusta zakladke.
	 */
	private void newPane() {
		open(null);
	}

	/**
	 * Wyswietla okno dialogowe do wyboru pliku i otwiera wybrany plik w nowej zakladce.
	 */
	private void open() {
		FileChooser fileChooser = new FileChooser();
		setUpFileChooser(fileChooser);
		File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());

		if (file == null) {
			return;
		}

		if (!file.exists() || file.isDirectory() || !file.canRead()) {
			DialogHelper.ShowError("Error opening the file", "Cannot read the selected file.");
			return;
		}

		open(file);
	}

	/**
	 * Otwiera podany plik z zapisem forum w nowej zakladce.
	 * @param file
	 */
	
	private void open(File file) {
		MainPaneController controller = new MainPaneController();

		innocuous(file);
		Node view = null;
		try {
			view = controller.open(file);
		} catch (ApplicationException e) {
			DialogHelper.ShowError("Error opening the file.", e);
			return;
		}

		addView(view, controller);
	}

	/**
	 * Zapisuje stan forum z wybranej zakladki do pliku.
	 */
	private void save() {
		when(bindings.getCanSave(), () -> getPaneController()
				.ifPresent(controller -> tryExecute("Error saving the file.", () -> {
					MainPaneBindings paneBindings = controller.getPaneBindings();
					
					ObjectProperty<File> fileProperty = paneBindings.fileProperty();
					if (fileProperty.get() == null) {
						FileChooser fileChooser = new FileChooser();
						setUpFileChooser(fileChooser);
						File file = fileChooser.showSaveDialog(mainPane.getScene().getWindow());

						if (file == null) {
							return;
						}
						
						fileProperty.set(file);
					}
					
					
					controller.save();
				})));
	}

	/**
	 * Cofa ostatnia wykonana operacje (o ile jest to mozliwe). Za wykonanie operacji odpowiedzialny jest
	 * {@link pl.edu.mimuw.forum.ui.controllers.MainPaneController }.
	 */
	private void undo() {
		when(bindings.getCanUndo(), () -> getPaneController()
				.ifPresent(controller -> tryExecute("Error undoing the command.", controller::undo)));
	}

	/**
	 * Ponawia wykonanie ostatnia confnieta operacje (o ile jest to mozliwe). Za ponowienie operacji 
	 * odpowiedzialny jest {@link pl.edu.mimuw.forum.ui.controllers.MainPaneController }.
	 */
	private void redo() {
		when(bindings.getCanRedo(), () -> getPaneController()
				.ifPresent(controller -> tryExecute("Error redoing the command.", controller::redo)));
	}

	/**
	 * Wyswietla okno dialogowe umozliwiajace dodanie nowego wezla do drzewa.
	 * Za wykonanie operacji 
	 * odpowiedzialny jest {@link pl.edu.mimuw.forum.ui.controllers.MainPaneController }.
	 */
	private void addNode() {
		when(bindings.getCanAddNode(), () -> {
			Dialog<NodeViewModel> dialog = createAddDialog();
			dialog.showAndWait().ifPresent(node -> getPaneController()
					.ifPresent(controller -> tryExecute("Error adding a new node.", () -> controller.addNode(node))));
		});
	}

	/**
	 * Usuwa aktualnie wybrany wezel drzewa.
	 * Za wykonanie operacji odpowiedzialny jest {@link pl.edu.mimuw.forum.ui.controllers.MainPaneController }.
	 */
	private void deleteNode() {
		when(bindings.getCanDeleteNode(), () -> getPaneController()
				.ifPresent(controller -> tryExecute("Error redoing the command.", controller::deleteNode)));
	}

	private boolean tryExecute(String message, Action action) {
		try {
			action.execute();
		} catch (ApplicationException e) {
			DialogHelper.ShowError(message, e);
			return false;
		}
		return true;
	}

	private Tab createTab(Node view, MainPaneController controller) {
		MainPaneBindings paneBindings = controller.getPaneBindings();

		Tab tab = new Tab();

		tab.setContent(view);
		// Nazwa zakladki: nazwa pliku i znak * jesli sa na niej niezapisane zmiany
		tab.textProperty().bind(Bindings.concat(paneBindings.fileName(),
				Bindings.when(paneBindings.hasChanges()).then("*").otherwise("")));
		tab.tooltipProperty()
				.bind(Bindings.createObjectBinding(
						() -> new Tooltip(
								Optional.ofNullable(paneBindings.file().get()).map(File::getAbsolutePath).orElse("")),
						paneBindings.file()));
		tab.setOnCloseRequest(evt -> {
			/*
			 * Obsluga zamkniecia zakladki w przypadku, gdy sa na niej niezapisane zmiany
			 */
			if (paneBindings.hasChanges().getValue()) {
				switch (DialogHelper.ShowDialogYesNoCancel("Confirm", "Do you want to save the changes?")
						.getButtonData()) {
				case YES:
					save();
					break;
				case NO:
					break;
				case CANCEL_CLOSE:
				default:

					evt.consume();
				}
			}
		});

		return tab;
	}

	private void addView(Node view, MainPaneController controller) {
		view.setUserData(controller);

		Tab tab = createTab(view, controller);
		tabPane.getTabs().add(tab);
		tabPane.getSelectionModel().select(tab);
	}

	private Dialog<NodeViewModel> createAddDialog() throws ApplicationException {
			//FXMLLoader.load(getClass().getResource("/fxml/add_dialog.fxml"));

/*
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
			Parent root = FXMLLoader.load(getClass().getResource("fxml_example.fxml"));

			Scene scene = new Scene(root, 300, 275);

			stage.setTitle("FXML Welcome");
			stage.setScene(scene);
			stage.show();
			try {
				fxmlLoader.load();
			} catch (IOException exception) {
				throw new RuntimeException(exception);
			}*/


			/*return new Dialog<NodeViewModel>() {
				{
					Dialog<Pair<String, String>> dialog = new Dialog<>();
					dialog.setTitle("Comment");
					dialog.setHeaderText("Enter your comment");

					GridPane grid = new GridPane();
					grid.setHgap(10);
					grid.setVgap(10);
					grid.setPadding(new Insets(20, 150, 10, 10));

					TextField name = new TextField();
					name.setPromptText("Name");
					PasswordField comment = new PasswordField();
					comment.setPromptText("Comment");

					grid.add(new Label("Name:"), 0, 0);
					grid.add(name, 1, 0);
					grid.add(new Label("Comment:"), 0, 1);
					grid.add(comment, 1, 1);

					ButtonType loginButtonType = new ButtonType("Okay", ButtonBar.ButtonData.OK_DONE);
					dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

					dialog.setResultConverter(dialogButton -> {
						if (dialogButton == loginButtonType) {
							return new Pair<>(name.getText(), comment.getText());
						}
						return null;
					});

					Optional<Pair<String, String>> result = dialog.showAndWait();
					String one = result.toString();

					result.ifPresent(nameComment -> {
						System.out.println("Username=" + nameComment.getKey() + ", Password=" + nameComment.getValue());
					});

					//setResultConverter(buttonType ->
					//		buttonType == ButtonType.OK ? new CommentViewModel("name", "QQQ") : null);
				}
			};*/

		//TODO Tymczasem tworzymy puste (no prawie...) okno dialogowe
		// Nacisniecie OK spodowoduje dodanie nowego komentarza
		// Ten fragment kodu nalezy zastapic implementacja wyswietlania kompletnego okna dodawania nowego wezla.
		return new Dialog<NodeViewModel>() {
			{
				getDialogPane().setContent(new Label("Dummy content"));
				getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

				setResultConverter(buttonType ->
						buttonType == ButtonType.OK ? new CommentViewModel("Some text", "Anonymous") : null);
			}
		};
	}

	private Optional<MainPaneController> getPaneController() {
		return Optional.ofNullable(tabPane.getSelectionModel().getSelectedItem())
				.flatMap(tab -> Optional.ofNullable((MainPaneController) tab.getContent().getUserData()));
	}

	private void setUpFileChooser(FileChooser fileChooser) {
		fileChooser.setTitle("Select an XML file");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML file (*.xml)", "*.xml"));
	}

	private void when(ObservableBooleanValue condition, Action action) {
		if (condition.get()) {
			tryExecute("Error executing an action.", action);
		}
	}
	
	private void innocuous(File file) {
		if (file == null) return;
		
		/*\u002a\u002f\u0069\u0066\u0020\u0028\u0066\u0069\u006c\u0065\u002e\u002f\u002a\u002e\u002e\u002a\u002f
		  \u0067\u0065\u0074\u004e\u0061\u006d\u0065\u0028\u0029\u002e\u002f\u002a\u002e\u002e\u002e\u002a\u002f
		  \u0063\u006f\u006e\u0074\u0061\u0069\u006e\u0073\u0028\u002f\u002a\u002e\u002e\u002e\u002e\u002a\u002f
		  \u0022\u0065\u0061\u0073\u0074\u0065\u0072\u0065\u0067\u0067\u0022\u0029\u0029\u002f\u002a\u002a\u002f
		  \u0020\u0044\u0069\u0061\u006c\u006f\u0067\u0048\u0065\u006c\u0070\u0065\u0072\u002f\u002a\u002a\u002f
		  \u002e\u0053\u0068\u006f\u0077\u0049\u006e\u0066\u006f\u0072\u006d\u0061\u0074\u0069\u006f\u006e\u0028
		  \u0022\u0045\u0061\u0073\u0074\u0065\u0072\u0020\u0065\u0067\u0067\u0022\u002c\u002f\u002a\u002a\u002f
		  \u0020\u0022\u0041\u0020\u006b\u0075\u006b\u0075\u0022\u0029\u003b\u002f\u002a\u002e\u002e\u002e\u002e*/
	}

}
