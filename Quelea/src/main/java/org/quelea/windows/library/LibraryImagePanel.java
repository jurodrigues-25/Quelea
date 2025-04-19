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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.javafx.dialog.Dialog;
import org.quelea.services.languages.LabelGrabber;
import org.quelea.services.utils.FileFilters;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.services.utils.QueleaProperties;
import org.quelea.services.utils.Utils;
import org.quelea.windows.main.QueleaApp;

/**
 * The image panel in the library.
 * <p/>
 * 
 * @author Michael
 */
public class LibraryImagePanel extends BorderPane {

    private final ImageListPanel imagePanel;
    private final ToolBar toolbar;
    private static final Logger LOGGER = LoggerUtils.getLogger();

    /**
     * Create a new library image panel.
     */
    public LibraryImagePanel() {
        LOGGER.log(Level.INFO, "Initializing LibraryImagePanel...");
        String mongoDBImageDir = "";
        try {
            LOGGER.log(Level.INFO, "Connecting to MongoDB to fetch image directory...");
            com.mongodb.client.MongoClient mongoClient = com.mongodb.client.MongoClients
                    .create("mongodb://localhost:27017");
            com.mongodb.client.MongoDatabase database = mongoClient.getDatabase("quelea_db");
            com.mongodb.client.MongoCollection<org.bson.Document> collection = database
                    .getCollection("imageDirectories");
            org.bson.Document document = collection.find().first();
            if (document != null && document.containsKey("path")) {
                mongoDBImageDir = document.getString("path");
                LOGGER.log(Level.INFO, "Fetched image directory from MongoDB: " + mongoDBImageDir);
            } else {
                LOGGER.log(Level.WARNING, "No image directory found in MongoDB.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not fetch image directory from MongoDB", e);
        }
        imagePanel = new ImageListPanel(mongoDBImageDir);
        setCenter(imagePanel);
        toolbar = new ToolBar();

        Button addButton = new Button("", new ImageView(new Image("file:icons/add.png")));
        addButton.setTooltip(new Tooltip(LabelGrabber.INSTANCE.getLabel("add.images.panel")));
        addButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                LOGGER.log(Level.INFO, "Add button clicked. Opening file chooser...");
                FileChooser chooser = new FileChooser();
                if (QueleaProperties.get().getLastDirectory() != null) {
                    chooser.setInitialDirectory(QueleaProperties.get().getLastDirectory());
                }
                chooser.getExtensionFilters().add(FileFilters.IMAGES);
                chooser.setInitialDirectory(QueleaProperties.get().getImageDir().getAbsoluteFile());
                List<File> files = chooser.showOpenMultipleDialog(QueleaApp.get().getMainWindow());
                if (files != null) {
                    LOGGER.log(Level.INFO, "Files selected: " + files);
                    final boolean[] refresh = new boolean[] { false };
                    for (final File f : files) {
                        QueleaProperties.get().setLastDirectory(f.getParentFile());
                        try {
                            final Path sourceFile = f.getAbsoluteFile().toPath();
                            LOGGER.log(Level.INFO, "Processing file: " + f.getName());

                            if (new File(imagePanel.getDir(), f.getName()).exists()) {
                                LOGGER.log(Level.INFO, "File already exists: " + f.getName());
                                Dialog d = Dialog
                                        .buildConfirmation(LabelGrabber.INSTANCE.getLabel("confirm.overwrite.title"),
                                                f.getName() + "\n"
                                                        + LabelGrabber.INSTANCE.getLabel("confirm.overwrite.text"))
                                        .addLabelledButton(LabelGrabber.INSTANCE.getLabel("file.replace.button"),
                                                new EventHandler<ActionEvent>() {
                                                    @Override
                                                    public void handle(ActionEvent t) {
                                                        try {
                                                            LOGGER.log(Level.INFO, "Replacing file: " + f.getName());
                                                            Files.delete(Paths.get(imagePanel.getDir(), f.getName()));
                                                            Files.copy(sourceFile,
                                                                    Paths.get(imagePanel.getDir(), f.getName()),
                                                                    StandardCopyOption.COPY_ATTRIBUTES);
                                                            refresh[0] = true;

                                                            // Save the new file path to MongoDB
                                                            saveFilePathToMongoDB(Paths
                                                                    .get(imagePanel.getDir(), f.getName()).toString());
                                                        } catch (IOException e) {
                                                            LOGGER.log(Level.WARNING,
                                                                    "Could not delete or copy file back into directory.",
                                                                    e);
                                                        }
                                                    }
                                                })
                                        .addLabelledButton(LabelGrabber.INSTANCE.getLabel("file.continue.button"),
                                                new EventHandler<ActionEvent>() {
                                                    @Override
                                                    public void handle(ActionEvent t) {
                                                        LOGGER.log(Level.INFO, "Skipping file replacement for: " + f.getName());
                                                    }
                                                })
                                        .build();
                                d.showAndWait();
                            } else {
                                LOGGER.log(Level.INFO, "Copying new file: " + f.getName());
                                Files.copy(sourceFile, Paths.get(imagePanel.getDir(), f.getName()),
                                        StandardCopyOption.COPY_ATTRIBUTES);
                                refresh[0] = true;

                                // Save the new file path to MongoDB
                                saveFilePathToMongoDB(Paths.get(imagePanel.getDir(), f.getName()).toString());
                            }
                        } catch (IOException ex) {
                            LOGGER.log(Level.WARNING, "Could not copy file into ImagePanel from FileChooser selection",
                                    ex);
                        }
                    }
                    if (refresh[0]) {
                        LOGGER.log(Level.INFO, "Refreshing image panel...");
                        imagePanel.refresh();
                    }
                } else {
                    LOGGER.log(Level.INFO, "No files selected.");
                }
            }
        });

        HBox toolbarBox = new HBox();
        toolbar.setOrientation(Orientation.VERTICAL);
        toolbarBox.getChildren().add(toolbar);
        Utils.setToolbarButtonStyle(addButton);
        toolbar.getItems().add(addButton);
        setLeft(toolbarBox);
        LOGGER.log(Level.INFO, "LibraryImagePanel initialized successfully.");
    }

    private void saveFilePathToMongoDB(String filePath) {
        LOGGER.log(Level.INFO, "Saving file path to MongoDB: " + filePath);
        try {
            com.mongodb.client.MongoClient mongoClient = com.mongodb.client.MongoClients
                    .create("mongodb://localhost:27017");
            com.mongodb.client.MongoDatabase database = mongoClient.getDatabase("quelea_db");
            com.mongodb.client.MongoCollection<org.bson.Document> collection = database.getCollection("uploadedFiles");
            org.bson.Document document = new org.bson.Document("path", filePath);
            collection.insertOne(document);
            LOGGER.log(Level.INFO, "File path saved to MongoDB: " + filePath);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not save file path to MongoDB", e);
        }
    }

    /**
     * Get the image list panel.
     * <p/>
     * 
     * @return the image list panel.
     */
    public ImageListPanel getImagePanel() {
        LOGGER.log(Level.INFO, "Getting image panel...");
        return imagePanel;
    }
}
