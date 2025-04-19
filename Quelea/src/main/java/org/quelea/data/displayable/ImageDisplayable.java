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
package org.quelea.data.displayable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.services.utils.QueleaProperties;
import org.quelea.services.utils.Utils;
import org.w3c.dom.Node;

/**
 * A displayable that's an image.
 *
 * @author Michael
 */
public class ImageDisplayable implements Displayable {

    public static final int ICON_WIDTH = 60;
    public static final int ICON_HEIGHT = 60;
    private final File file;
    private Image image;
    private static final Logger LOGGER = LoggerUtils.getLogger();
    // Add necessary fields (if they don't exist):
    private String filename;
    private String objectId;

    // Add getter methods for the new fields:
    public String getFilename() {
        return filename;
    }

    public String getObjectId() {
        return objectId;
    }

    /**
     * Create a new image displayable.
     *
     * @param file the file for the displayable.
     */
    public ImageDisplayable(File file) {
        this.file = file;
        image = new Image("file:" + file.getAbsolutePath());
    }

    public ImageDisplayable(Image image) {
        this.file = null;
        this.image = image;
    }

    public ImageDisplayable(String filename, String objectId) {
        // Initialize the ImageDisplayable with the filename and objectId
        this.filename = filename; // Assuming you have a 'filename' field
        this.objectId = objectId; // Assuming you have an 'objectId' field
        // You might need to adjust how the image is loaded or referenced later
        this.file = null;
    }

    /**
     * Get the displayable file.
     *
     * @return the displayable file.
     */
    public File getFile() {
        return file;
    }

    /**
     * Get the displayable image.
     *
     * @return the displayable image.
     */
    public Image getImage() {
        return image;
    }

    /**
     * Parse some XML representing this object and return the object it
     * represents.
     *
     * @param node the XML node representing this object.
     * @return the object as defined by the XML.
     */
    public static ImageDisplayable parseXML(Node node, Map<String, String> fileChanges) {
        File file = new File(node.getTextContent());
        File imgFile = Utils.getChangedFile(node, fileChanges);
        if (!imgFile.exists()) {
            imgFile = new File(QueleaProperties.get().getImageDir(), file.getName());
        }
        if (!imgFile.exists()) {
            imgFile = new File(QueleaProperties.get().getDownloadPath(), file.getName());
        }
        if (!imgFile.exists()) {
            LOGGER.log(Level.WARNING, "Image file {0} doesn''t exist.", imgFile.getAbsolutePath());
            return null;
        }
        return new ImageDisplayable(imgFile);
    }

    /**
     * Get the XML that forms this image displayable.
     *
     * @return the XML.
     */
    @Override
    public String getXML() {
        StringBuilder ret = new StringBuilder();
        ret.append("<fileimage>");
        if (QueleaProperties.get().getEmbedMediaInScheduleFile()) {
            ret.append(Utils.escapeXML(Utils.toRelativeStorePath(file)));
        } else {
            ret.append(Utils.escapeXML(file.getAbsolutePath()));
        }
        ret.append("</fileimage>");
        return ret.toString();
    }

    /**
     * Get the preview icon for this displayable (30x30.)
     *
     * @return the preview icon.
     */
   /*  @Override
    public ImageView getPreviewIcon() {
        ImageView small = new ImageView(new Image("file:" + file.getAbsolutePath(), 30, 30, false, true));
        return small;
    } */


    @Override
public ImageView getPreviewIcon() {
    if (file != null) {
        return new ImageView(new Image("file:" + file.getAbsolutePath(), 30, 30, false, true));
    } else if (objectId != null) {
        // If the file is null, but we have an objectId, it means the image
        // was likely loaded from MongoDB. We need to retrieve the image data.
        // **Important:** You need access to your GridFSBucket here to load the data.
        // Assuming you have a way to access it (e.g., through a static instance
        // or by passing it to the ImageDisplayable or this method).

        // **Placeholder - Replace with your actual MongoDB retrieval logic**
        byte[] imageData = loadImageDataFromMongoDB(objectId);

        if (imageData != null) {
            Image previewImage = new Image(new java.io.ByteArrayInputStream(imageData), 30, 30, true, true);
            return new ImageView(previewImage);
        } else {
            LOGGER.log(Level.WARNING, "Could not load preview icon from MongoDB for ObjectId: {0}", objectId);
            return null; // Or return a default error icon
        }
    }
    return null; // If both file and objectId are null
}

// **You need to implement this method to fetch image data from MongoDB**
private byte[] loadImageDataFromMongoDB(String objectIdString) {
    // Access your MongoClient, database, and GridFSBucket here.
    // Convert objectIdString to ObjectId.
    // Open a download stream from GridFS using the ObjectId.
    // Read the stream into a byte array.
    // Handle potential IOExceptions.
    // Return the byte array, or null if there's an error.
    return null; // Placeholder
}
    /**
     * Get the preview text for the image.
     *
     * @return the file name.
     */
    @Override
public String getPreviewText() {
    if (file != null) {
        return file.getName();
    } else if (filename != null) {
        return filename;
    } else {
        return "Image"; // Or some other default text if both file and filename are null
    }
}

    /**
     * Get any resources this displayable needs (in this case the image.)
     *
     * @return the image backing this displayable.
     */
    @Override
    public Collection<File> getResources() {
        List<File> files = new ArrayList<>();
        files.add(file);
        return files;
    }

    /**
     * Images don't support clearing of text (they contain no text) so false,
     * always.
     *
     * @return false, always.
     */
    @Override
    public boolean supportClear() {
        return false;
    }

    @Override
    public void dispose() {
        // Nothing needed here.
    }
}
