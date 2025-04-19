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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.FileInputStream;

public class LibraryImagePanel extends BorderPane {

    private final ImageListPanel imagePanel;
    private final ToolBar toolbar;
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private MongoClient mongoClient;
    private MongoDatabase database;
    private GridFSBucket gridFSBucket;

    /**
     * Create a new library image panel.
     */
    public LibraryImagePanel() {
        LOGGER.log(Level.INFO, "Initializing LibraryImagePanel with MongoDB...");
        try {
            mongoClient = MongoClients.create("mongodb://localhost:27017"); // Consider configuration
            database = mongoClient.getDatabase("quelea_db"); // Consider configuration
            gridFSBucket = GridFSBuckets.create(database, "images"); // 'images' is the GridFS bucket name
            imagePanel = new ImageListPanel(mongoClient, database, gridFSBucket);
            setCenter(imagePanel);
            toolbar = new ToolBar();

            Button addButton = new Button("", new ImageView(new Image("file:icons/add.png")));
            addButton.setTooltip(new Tooltip(LabelGrabber.INSTANCE.getLabel("add.images.panel")));
            addButton.setOnAction(t -> {
                LOGGER.log(Level.INFO, "Add button clicked. Opening file chooser for MongoDB...");
                FileChooser chooser = new FileChooser();
                if (QueleaProperties.get().getLastDirectory() != null) {
                    chooser.setInitialDirectory(QueleaProperties.get().getLastDirectory());
                }
                chooser.getExtensionFilters().add(FileFilters.IMAGES);
                chooser.setInitialDirectory(QueleaProperties.get().getImageDir().getAbsoluteFile());
                List<File> files = chooser.showOpenMultipleDialog(QueleaApp.get().getMainWindow());
                if (files != null) {
                    LOGGER.log(Level.INFO, "Files selected for MongoDB upload: " + files);
                    for (File f : files) {
                        QueleaProperties.get().setLastDirectory(f.getParentFile());
                        try (FileInputStream inputStream = new FileInputStream(f)) {
                            LOGGER.log(Level.INFO, "Uploading file to MongoDB GridFS: " + f.getName());
                            gridFSBucket.uploadFromStream(f.getName(), inputStream);
                            // Optionally save metadata if needed
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Could not read file for MongoDB upload: " + f.getName(), e);
                        }
                    }
                    LOGGER.log(Level.INFO, "Refreshing image panel after MongoDB upload...");
                    imagePanel.refresh();
                } else {
                    LOGGER.log(Level.INFO, "No files selected for MongoDB upload.");
                }
            });

            HBox toolbarBox = new HBox();
            toolbar.setOrientation(Orientation.VERTICAL);
            toolbarBox.getChildren().add(toolbar);
            Utils.setToolbarButtonStyle(addButton);
            toolbar.getItems().add(addButton);
            setLeft(toolbarBox);
            LOGGER.log(Level.INFO, "LibraryImagePanel initialized successfully with MongoDB support.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing LibraryImagePanel with MongoDB", e);
            throw new RuntimeException("Failed to initialize LibraryImagePanel with MongoDB", e);
        }
    }

    private void saveImageMetadata(String fileId, String originalName) {
        try {
            com.mongodb.client.MongoCollection<Document> metadataCollection = database.getCollection("imageMetadata"); // Consider configuration
            Document metadata = new Document("gridFSFileId", fileId)
                    .append("originalName", originalName)
                    .append("uploadDate", java.time.LocalDateTime.now().toString());
            metadataCollection.insertOne(metadata);
            LOGGER.log(Level.INFO, "Image metadata saved to MongoDB: " + metadata);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not save image metadata to MongoDB", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (mongoClient != null) {
            mongoClient.close();
        }
        super.finalize();
    }

    public ImageListPanel getImagePanel() {
        LOGGER.log(Level.INFO, "Getting image panel (now potentially MongoDB-backed)...");
        return imagePanel;
    }
}