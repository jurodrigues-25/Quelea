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
package org.quelea.windows.main.schedule;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.quelea.data.displayable.Displayable;
import org.quelea.data.displayable.TextDisplayable;
import org.quelea.data.displayable.TimerDisplayable;
import org.quelea.services.utils.Utils;
import org.quelea.windows.main.actionhandlers.EditThemeScheduleActionHandler;
import org.quelea.windows.main.actionhandlers.EditTimerThemeActionHandler;

/**
 * @author Michael
 */
public class ScheduleListNode extends HBox {

    private FadeTransition fade;
    private Button themeButton;
    private ImageView liveIcon;

    public ScheduleListNode(Displayable displayable) {
        super(10);
        setAlignment(Pos.CENTER_LEFT);
        Node icon = displayable.getPreviewIcon();
        liveIcon = new ImageView(new Image("file:icons/recordingssettingsicon.png"));
        liveIcon.setFitHeight(10);
        liveIcon.setFitWidth(10);
        liveIcon.setVisible(false);
    
        if (icon != null) {
            getChildren().add(icon);
        } else {
            // Handle the case where the preview icon is null.
            // You could add a placeholder or just not add anything.
            // Logging a warning here might also be helpful.
            //LOGGER.log(Level.WARNING, "Preview icon was null for displayable: {0}", displayable.getPreviewText());
            // If you want to add a placeholder:
            // ImageView placeholder = new ImageView(new Image("file:icons/image_placeholder.png", 30, 30, true, true));
            // getChildren().add(placeholder);
        }
    
        getChildren().add(new Label(displayable.getPreviewText()));
        getChildren().add(liveIcon);
    
        if (displayable instanceof TextDisplayable || displayable instanceof TimerDisplayable) {
            themeButton = new Button("", new ImageView(new Image("file:icons/theme.png", 16, 16, false, true)));
            if (displayable instanceof TextDisplayable) {
                themeButton.setOnAction(new EditThemeScheduleActionHandler((TextDisplayable) displayable));
            } else {
                themeButton.setOnAction(new EditTimerThemeActionHandler((TimerDisplayable) displayable));
            }
            Utils.setToolbarButtonStyle(themeButton);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            getChildren().add(spacer);
            getChildren().add(themeButton);
            themeButton.setOpacity(0);
            fade = new FadeTransition(Duration.millis(100), themeButton);
            setOnMouseEntered((event) -> {
                fade.stop();
                fade.setToValue(1);
                fade.play();
            });
            setOnMouseExited((event) -> {
                fade.stop();
                fade.setToValue(0);
                fade.play();
            });
        }
    }

    public void setLive(boolean live) {
        if (liveIcon != null) {
            liveIcon.setVisible(live);
        }
    }

}
