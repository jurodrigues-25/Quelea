/*
 * This file is part of Quelea, free projection software for churches.
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.quelea.windows.library;
import org.quelea.services.utils.Utils;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import org.javafx.dialog.Dialog;
import org.quelea.data.displayable.ImageDisplayable;
import org.quelea.services.languages.LabelGrabber;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.windows.main.QueleaApp;
import org.bson.types.ObjectId;

/**
 * The panel displayed on the library to select the list of images.
 * <p/>
 * @author Michael
 */
public class ImageListPanel extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ImageListPanel.class.getName());

    private static final String BORDER_STYLE_SELECTED = "-fx-padding: 0.2em;-fx-border-color: #0093ff;-fx-border-radius: 5;-fx-border-width: 0.1em;";
    private static final String BORDER_STYLE_DESELECTED = "-fx-padding: 0.2em;-fx-border-color: rgb(0,0,0,0);-fx-border-radius: 5;-fx-border-width: 0.1em;";
    private final TilePane imageList;
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final GridFSBucket gridFSBucket;
    private Thread updateThread;

    /**
     * Create a new image list panel that fetches images from MongoDB.
     * <p/>
     * @param mongoClient The MongoClient instance.
     * @param database    The MongoDatabase instance.
     * @param gridFSBucket The GridFSBucket instance for images.
     */
    public ImageListPanel(MongoClient mongoClient, MongoDatabase database, GridFSBucket gridFSBucket) {
        LOGGER.log(Level.INFO, "Initializing ImageListPanel for MongoDB...");

        this.mongoClient = mongoClient;
        this.database = database;
        this.gridFSBucket = gridFSBucket;
        this.imageList = new TilePane();
        this.imageList.setAlignment(Pos.CENTER);
        this.imageList.setHgap(15);
        this.imageList.setVgap(15);
        this.imageList.setOrientation(Orientation.HORIZONTAL);

        // Drag and drop from outside the application (e.g., file system)
        this.imageList.setOnDragOver(dragEvent -> {
            if (dragEvent.getGestureSource() == null && dragEvent.getDragboard().hasFiles()) {
                dragEvent.acceptTransferModes(TransferMode.COPY); // Allow copying files in
            }
        });
        this.imageList.setOnDragDropped(dragEvent -> {
            if (dragEvent.getGestureSource() == null && dragEvent.getDragboard().hasFiles()) {
                List<java.io.File> files = dragEvent.getDragboard().getFiles();
                for (java.io.File f : files) {
                    if (Utils.fileIsImage(f) && !f.isDirectory()) {
                        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(f)) {
                            LOGGER.log(Level.INFO, "Uploading dragged file to MongoDB GridFS: " + f.getName());
                            gridFSBucket.uploadFromStream(f.getName(), inputStream);
                            // Optionally save metadata if needed
                        } catch (IOException ex) {
                            LoggerUtils.getLogger().log(Level.WARNING, "Could not read dragged file for MongoDB upload.", ex);
                        }
                    }
                }
                updateImagesFromMongoDB(); // Refresh the view after upload
            }
        });

        updateImagesFromMongoDB();
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setContent(this.imageList);
        setCenter(scroll);
    }

    /**
     * Refresh the contents of this image list panel by fetching from MongoDB.
     */
    public void refresh() {
        updateImagesFromMongoDB();
    }

    /**
     * Fetch image metadata and display thumbnails from MongoDB GridFS.
     */
    private void updateImagesFromMongoDB() {
        imageList.getChildren().clear();
        if (updateThread != null && updateThread.isAlive()) {
            return;
        }
        updateThread = new Thread(() -> {
            try {
                gridFSBucket.find().forEach(gridFSFile -> {
                    final HBox viewBox = new HBox();
                    final ImageView view = new ImageView();
                    view.setPreserveRatio(true);
                    view.setFitWidth(160);
                    view.setFitHeight(90);

                    try (GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(gridFSFile.getObjectId())) {
                        Image image = new Image(downloadStream);
                        Platform.runLater(() -> view.setImage(image));
                    }

                    view.setOnMouseClicked((MouseEvent t) -> {
                        if (t.getButton() == MouseButton.PRIMARY && t.getClickCount() > 1) {
                            // When double-clicked, add an ImageDisplayable with a reference to the GridFS file ID
                            QueleaApp.get().getMainWindow().getMainPanel().getSchedulePanel().getScheduleList()
                                    .add(new ImageDisplayable(gridFSFile.getFilename(), gridFSFile.getObjectId().toHexString()));
                        } else if (t.getButton() == MouseButton.SECONDARY) {
                            ContextMenu removeMenu = new ContextMenu();
                            MenuItem removeItem = new MenuItem(LabelGrabber.INSTANCE.getLabel("remove.image.text"));
                            removeItem.setOnAction(actionEvent -> {
                                final boolean[] reallyDelete = new boolean[]{false};
                                Dialog.buildConfirmation(LabelGrabber.INSTANCE.getLabel("delete.image.title"),
                                        LabelGrabber.INSTANCE.getLabel("delete.image.confirmation"))
                                        .addYesButton(actionEvent1 -> reallyDelete[0] = true)
                                        .addNoButton(actionEvent1 -> {
                                        }).build().showAndWait();
                                if (reallyDelete[0]) {
                                    ObjectId fileIdToDelete = gridFSFile.getObjectId();
                                    gridFSBucket.delete(fileIdToDelete);
                                    Platform.runLater(() -> imageList.getChildren().remove(viewBox));
                                    LOGGER.log(Level.INFO, "Deleted image from MongoDB GridFS with ID: " + fileIdToDelete.toHexString() + " (filename: " + gridFSFile.getFilename() + ")");
                                }
                            });
                            removeMenu.getItems().add(removeItem);
                            removeMenu.show(view, t.getScreenX(), t.getScreenY());
                        }
                    });
                    view.setOnDragDetected(mouseEvent -> {
                        Dragboard db = startDragAndDrop(TransferMode.ANY);
                        ClipboardContent content = new ClipboardContent();
                        content.putString("mongodb-image://" + gridFSFile.getObjectId().toHexString()); // Custom URI to identify MongoDB image
                        db.setContent(content);
                        mouseEvent.consume();
                    });

                    viewBox.getChildren().add(view);
                    setupHover(viewBox);
                    Platform.runLater(() -> imageList.getChildren().add(viewBox));
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error fetching images from MongoDB", e);
                // Optionally display an error message to the user
            }
        });
        updateThread.start();
    }

    private void setupHover(final Node view) {
        view.setStyle(BORDER_STYLE_DESELECTED);
        view.setOnMouseEntered(mouseEvent -> view.setStyle(BORDER_STYLE_SELECTED));
        view.setOnMouseExited(mouseEvent -> view.setStyle(BORDER_STYLE_DESELECTED));
    }

    // This method is no longer directly used as we are fetching from MongoDB
    // You might want to remove it or repurpose it if needed.
    public void changeDir(java.io.File absoluteFile) {
        LOGGER.log(Level.WARNING, "changeDir method called on MongoDB-backed ImageListPanel. This might not be relevant.");
    }

    // Getter for the GridFSBucket if you need to access it elsewhere
    public GridFSBucket getGridFSBucket() {
        return gridFSBucket;
    }
}