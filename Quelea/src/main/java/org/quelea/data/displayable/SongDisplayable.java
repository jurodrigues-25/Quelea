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

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DataFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.quelea.data.Background;
import org.quelea.data.ThemeDTO;
import org.quelea.data.db.SongManager;
import org.quelea.services.languages.LabelGrabber;
import org.quelea.services.utils.LineTypeChecker;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.services.utils.QueleaProperties;
import org.quelea.services.utils.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A song that contains a number of sections (verses, choruses, etc.)
 * <p/>
 *
 * @author Michael
 */
public class SongDisplayable implements TextDisplayable, Comparable<SongDisplayable>, Serializable {

    public int count = 0;
    public static final DataFormat SONG_DISPLAYABLE_FORMAT = new DataFormat("songdisplayable");
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private boolean updateInDB = true;
    private String title = "";
    private String author = "";
    private String ccli = "";
    private String year = "";
    private String publisher = "";
    private String copyright = "";
    private String key = "";
    private String capo = "";
    private String info = "";
    private boolean quickInsert;
    private List<TextSection> sectionsInSequence = new ArrayList<>();
    private List<TextSection> sectionsWithoutSequence = new ArrayList<>();
    private HashMap<String, String> translations = new HashMap<>();
    private String currentTranslation;
    private ThemeDTO theme;
    private long id = 0;
    private boolean printChords;
    private String lastSearch = "";
    private String sequence = "";
    private Map<Dimension, Double> fontSizeCache;

    /**
     * The builder responsible for building this song.
     */
    public static class Builder {

        private final SongDisplayable song;

        /**
         * Create a new builder with the required fields.
         * <p/>
         *
         * @param title  the title of the song.
         * @param author the author of the song.
         */
        public Builder(String title, String author) {
            song = new SongDisplayable(title, author);
        }

        /**
         * Set the id of the song.
         * <p/>
         *
         * @param id the song's id.
         * @return this builder.
         */
        public Builder id(long id) {
            song.id = id;
            return this;
        }

        /**
         * Set the ccli number of the song.
         * <p/>
         *
         * @param ccli the song's ccli number.
         * @return this builder.
         */
        public Builder ccli(String ccli) {
            if (ccli == null) {
                ccli = "";
            }
            song.ccli = ccli;
            return this;
        }

        /**
         * Set the year of the song.
         * <p/>
         *
         * @param year the song's year.
         * @return this builder.
         */
        public Builder year(String year) {
            if (year == null) {
                year = "";
            }
            song.year = year;
            return this;
        }

        /**
         * Set the publisher of the song.
         * <p/>
         *
         * @param publisher the song's publisher.
         * @return this builder.
         */
        public Builder publisher(String publisher) {
            if (publisher == null) {
                publisher = "";
            }
            song.publisher = publisher;
            return this;
        }

        /**
         * Set the theme of this song..
         * <p/>
         *
         * @param theme the song's theme.
         * @return this builder.
         */
        public Builder theme(ThemeDTO theme) {
            song.theme = theme;
            return this;
        }

        /**
         * Set the lyrics of this song..
         * <p/>
         *
         * @param lyrics the song's lyrics.
         * @return this builder.
         */
        public Builder lyrics(String lyrics) {
            song.setLyrics(lyrics);
            return this;
        }

        /**
         * Set the copyright info of this song..
         * <p/>
         *
         * @param copyright the song's copyright info.
         * @return this builder.
         */
        public Builder copyright(String copyright) {
            if (copyright == null) {
                copyright = "";
            }
            song.copyright = copyright;
            return this;
        }

        /**
         * Set the key of this song..
         * <p/>
         *
         * @param key the song's key.
         * @return this builder.
         */
        public Builder key(String key) {
            if (key == null) {
                key = "";
            }
            song.key = key;
            return this;
        }

        public Builder translations(HashMap<String, String> translations) {
            song.translations = translations;
            return this;
        }

        /**
         * Set the capo of this song..
         * <p/>
         *
         * @param capo the song's capo.
         * @return this builder.
         */
        public Builder capo(String capo) {
            if (capo == null) {
                capo = "";
            }
            song.capo = capo;
            return this;
        }

        /**
         * Set the info string of this song..
         * <p/>
         *
         * @param info the song's information field.
         * @return this builder.
         */
        public Builder info(String info) {
            if (info == null) {
                info = "";
            }
            song.info = info;
            return this;
        }

        /**
         * Set the song sequence of this song.
         * <p/>
         *
         * @param sequence the song's sequence order.
         * @return this builder.
         */
        public Builder sequence(String sequence) {
            song.sequence = sequence == null ? "" : sequence;
            return this;
        }

        /**
         * Get the song from this builder with all the fields set appropriately.
         * <p/>
         *
         * @return the song.
         */
        public SongDisplayable get() {
            return song;
        }
    }

    /**
     * Copy constructor - creates a shallow copy.
     * <p/>
     *
     * @param song the song to copy to create the new song.
     */
    public SongDisplayable(SongDisplayable song) {
        this.fontSizeCache = new HashMap<>();
        this.title = song.title;
        this.author = song.author;
        this.sectionsInSequence = new ArrayList<>();
        this.sectionsWithoutSequence = new ArrayList<>();
        for (TextSection section : song.getSectionsWithoutSequence()) {
            this.sectionsWithoutSequence.add(new TextSection(section));
        }
        this.theme = song.theme;
        this.id = song.id;
        this.ccli = song.ccli;
        this.year = song.year;
        this.publisher = song.publisher;
        this.copyright = song.copyright;
        this.key = song.key;
        this.info = song.info;
        this.capo = song.capo;
        this.lastSearch = song.lastSearch;
        this.translations = song.translations;
        this.count = song.count++;
        this.sequence = song.sequence;
    }

    /**
     * Create a new, empty song.
     * <p/>
     *
     * @param title  the title of the song.
     * @param author the author of the song.
     */
    public SongDisplayable(String title, String author) {
        this(title, author, new ThemeDTO(ThemeDTO.DEFAULT_FONT,
                ThemeDTO.DEFAULT_FONT_COLOR, ThemeDTO.DEFAULT_FONT, ThemeDTO.DEFAULT_TRANSLATE_FONT_COLOR,
                ThemeDTO.DEFAULT_BACKGROUND, ThemeDTO.DEFAULT_SHADOW, false, false, false, true, -1, 0));
    }

    /**
     * Create a new, empty song.
     * <p/>
     *
     * @param title  the title of the song.
     * @param author the author of the song.
     * @param theme  the theme of the song.
     */
    public SongDisplayable(String title, String author, ThemeDTO theme) {
        this.fontSizeCache = new HashMap<>();
        id = -1;
        this.title = title;
        this.author = author;
        this.theme = theme;
        sectionsInSequence = new ArrayList<>();
        sectionsWithoutSequence = new ArrayList<>();
        sequence = "";
    }

    @Override
    public Double getCachedUniformFontSize(Dimension dimension) {
        return fontSizeCache.get(dimension);
    }

    @Override
    public void setCachedUniformFontSize(Dimension dimension, double size) {
        fontSizeCache.put(dimension, size);
    }

    /**
     * Ensure changes to this song are not updated in the database.
     */
    public void setNoDBUpdate() {
        updateInDB = false;
    }

    /**
     * Check whether changes to this song should be persisted to the database.
     *
     * @return true if changes should be persisted, false otherwise.
     */
    public boolean checkDBUpdate() {
        return updateInDB;
    }

    /**
     * Set that this song is a quick insert song, and should not be updated in
     * the database.
     */
    public void setQuickInsert() {
        quickInsert = true;
    }

    /**
     * Determine if this song was entered via quick insert, and thus should not
     * be updated in the database.
     * <p>
     *
     * @return true if this is a "quick insert" song, false otherwise.
     */
    public boolean isQuickInsert() {
        return quickInsert;
    }

    /**
     * Get the current translation lyrics that should be displayed for this song
     * and section, or null if none should be displayed.
     *
     * @param index the index of the section.
     * @return the current translation that should be displayed for this song
     * and section.
     */
    public String getCurrentTranslationSection(int index) {
        String val = getCurrentTranslationLyrics();
        if (val == null) {
            return null;
        }
        index = sectionsWithoutSequence.indexOf(sectionsInSequence.get(index));
        String[] parts = val.split("\n\n");
        if (parts.length > index) {
            return parts[index].trim();
        }
        return null;
    }

    /**
     * Set the translation that should be displayed alongside the default
     * lyrics. It should match a key in the translations map.
     *
     * @param currentTranslation the translation that should be displayed
     *                           alongside the default lyrics.
     */
    public void setCurrentTranslationLyrics(String currentTranslation) {
        this.currentTranslation = currentTranslation;
    }

    /**
     * Get the full translation lyrics for this song, or null if no translation
     * is selected.
     *
     * @return the full translation lyrics for this song, or null if no
     * translation is selected.
     */
    public String getCurrentTranslationLyrics() {
        if (translations == null) {
            return null;
        }
        String val = translations.get(currentTranslation);
        if (val == null) {
            return null;
        }
        return val;
    }

    /**
     * Get the name of the current translation in use, or null if none exists.
     *
     * @return the name of the current translation in use, or null if none
     * exists.
     */
    public String getCurrentTranslationName() {
        if (getCurrentTranslationLyrics() != null) {
            return currentTranslation;
        }
        return null;
    }

    /**
     * Try and give this song an ID based on the ID in the database. If this
     * can't be done, leave it as -1.
     */
    public void matchID() {
        if (id == -1 && updateInDB) {
            for (SongDisplayable song : SongManager.get(true).getSongs()) {
                if (this.title.equals(song.title) && getLyrics(true, true, false).equals(song.getLyrics(true, true, false)) && this.author.equals(song.author)) {
                    id = song.getID();
                }
            }
        }
    }

    /**
     * Determine whether this song contains any lines of chords.
     * <p/>
     *
     * @return true if it contains chords, false otherwise.
     */
    public boolean hasChords() {
        String[] lyrics = getLyrics(true, true, false).split("\n");
        for (String line : lyrics) {
            if (new LineTypeChecker(line).getLineType() == LineTypeChecker.Type.CHORDS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the unique ID of the song.
     * <p/>
     *
     * @return the ID of the song.
     */
    public long getID() {
        return id;
    }

    /**
     * Set the unique ID of this song.
     * <p/>
     *
     * @param id the id of the song.
     */
    public void setID(long id) {
        this.id = id;
    }

    /**
     * Get the title of this song.
     * <p/>
     *
     * @return the title of this song.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the title of the song.
     * <p/>
     *
     * @param title the new song title.
     */
    public void setTitle(String title) {
        this.title = title;
        refreshLyrics();
    }

    public void setTranslations(HashMap<String, String> translations) {
        fontSizeCache.clear();
        this.translations = translations;
    }

    /**
     * Get the author of this song.
     * <p/>
     *
     * @return the author of the song.
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Set the author of the song.
     * <p/>
     *
     * @param author the new song author.
     */
    public void setAuthor(String author) {
        this.author = author;
        refreshLyrics();
    }

    /**
     * Return true because songs can be cleared.
     * <p/>
     *
     * @return true, always.
     */
    @Override
    public boolean supportClear() {
        return true;
    }

    /**
     * Get the CCLI number of this song.
     * <p/>
     *
     * @return the CCLI number of this song.
     */
    public String getCcli() {
        return ccli;
    }

    /**
     * Get the publisher of this song.
     * <p/>
     *
     * @return the publisher of this song.
     */
    public String getPublisher() {
        return publisher;
    }

    /**
     * Get the year of this song.
     * <p/>
     *
     * @return the year of this song.
     */
    public String getYear() {
        return year;
    }

    /**
     * Retrieve assigned theme
     * <p/>
     *
     * @return assigned theme
     */
    public ThemeDTO getTheme() {
        return this.theme;
    }

    /**
     * Get the copyright information of this song.
     * <p/>
     *
     * @return the copyright information of this song.
     */
    public String getCopyright() {
        return copyright;
    }

    /**
     * Get the key of this song.
     * <p/>
     *
     * @return the key of this song.
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the general information about this song.
     * <p/>
     *
     * @return the general information about this song.
     */
    public String getInfo() {
        return info;
    }

    /**
     * Get the capo of this song.
     * <p/>
     *
     * @return the capo of this song.
     */
    public String getCapo() {
        return capo;
    }

    /**
     * Set the capo of this song.
     * <p/>
     *
     * @param capo the capo of this song.
     */
    public void setCapo(String capo) {
        this.capo = capo;
    }

    /**
     * Set whether to print the chords of this song - temporary field used when
     * printing chords.
     * <p/>
     *
     * @param printChords true if chords should be printed, false otherwise.
     */
    public void setPrintChords(boolean printChords) {
        this.printChords = printChords;
    }

    /**
     * Set the info of this song.
     * <p/>
     *
     * @param info the info of this song.
     */
    public void setInfo(String info) {
        this.info = info;
    }

    /**
     * Set the key of this song.
     * <p/>
     *
     * @param key the key of this song.
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Set the ccli number of this song.
     * <p/>
     *
     * @param ccli the ccli number of this song.
     */
    public void setCcli(String ccli) {
        this.ccli = ccli;
    }

    /**
     * Set the publisher of this song.
     * <p/>
     *
     * @param publisher the publisher of this song.
     */
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    /**
     * Set the year of this song.
     * <p/>
     *
     * @param year the year of this song.
     */
    public void setYear(String year) {
        this.year = year;
    }

    /**
     * Set the copyright field of this song.
     * <p/>
     *
     * @param copyright the copyright field of this song.
     */
    public void setCopyright(String copyright) {
        this.copyright = copyright;
        refreshLyrics();
    }

    /**
     * Set the sequence order of this song.
     * <p/>
     *
     * @param sequence the copyright field of this song.
     */
    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    /**
     * Get the sequence order of this song.
     * <p/>
     *
     * @return the song sequence order
     */
    public String getSequence() {
        return sequence;
    }

    private void refreshLyrics() {
        fontSizeCache.clear();
        ThemeDTO theme = ThemeDTO.DEFAULT_THEME;
        for (TextSection section : sectionsInSequence) {
            theme = section.getTheme();
        }
        setLyrics(getLyrics(true, true, false));
        for (TextSection section : sectionsInSequence) {
            section.setTheme(theme);
        }
    }

    /**
     * Get all the lyrics to this song as a string. This can be parsed using the
     * setLyrics() method.
     * <p/>
     *
     * @param chords     true if any chords should be included, false otherwise.
     * @param comments   true if any comments should be included, false otherwise.
     * @param inSequence true if lyrics should be returned according to stored sequence, false otherwise.
     * @return the lyrics to this song.
     */
    public String getLyrics(boolean chords, boolean comments, boolean inSequence) {
        StringBuilder ret = new StringBuilder();
        for (TextSection section : inSequence ? sectionsInSequence : sectionsWithoutSequence) {
            if (section.getTitle() != null && !section.getTitle().equals("")) {
                ret.append(section.getTitle()).append("\n");
            }
            for (String line : section.getText(chords, comments)) {
                ret.append(line).append("\n");
            }
            ret.append("\n");
        }
        return ret.toString().replaceAll("\\s+$", "").replace(" ", "<>");
    }

    public void addTranslation(String translationName, String translationText) {
        fontSizeCache.clear();
        translations.put(translationName, translationText.trim());
    }

    public HashMap<String, String> getTranslations() {
        return translations;
    }

    /**
     * Set the lyrics to this song as a string. This will erase any sections
     * currently in the song and parse the given lyrics into a number of song
     * sections.
     * <p/>
     *
     * @param lyrics the lyrics to set as this song's lyrics.
     */
    public void setLyrics(String lyrics) {
        sectionsWithoutSequence.clear();
        sectionsInSequence.clear();
        fontSizeCache.clear();
        boolean foundTitle = !(title == null || title.isEmpty());
        lyrics = lyrics.replaceAll("\n\n+", "\n\n");
        lyrics = lyrics.replace("<>", " ");
        for (String section : lyrics.split("(\n\n)|(\r\r)|(\r\n\r\n)")) {
            String[] sectionLines = section.split("\n");
            String[] newLyrics = section.split("\n");
            String sectionTitle = "";
            if (sectionLines.length == 0) {
                continue;
            }
            if (new LineTypeChecker(sectionLines[0]).getLineType() == LineTypeChecker.Type.TITLE) {
                sectionTitle = sectionLines[0];
                newLyrics = new String[sectionLines.length - 1];
                System.arraycopy(sectionLines, 1, newLyrics, 0, newLyrics.length);
            }
            if (!foundTitle) {
                for (String line : sectionLines) {
                    if (new LineTypeChecker(line).getLineType() == LineTypeChecker.Type.NORMAL) {
                        title = line;
                        foundTitle = true;
                        break;
                    }
                }
            }
            String churchCcliNum = QueleaProperties.get().getChurchCcliNum();
            String[] smallLines;
            if (churchCcliNum == null || churchCcliNum.isEmpty()) {
                smallLines = new String[]{
                        title,
                        author + ((ccli.equals("")) ? " " : (" (" + ccli + ")"))
                };
            } else {
                String cpText = null;
                if (copyright != null) {
                    cpText = copyright.trim();
                }
                if (cpText != null && !cpText.trim().isEmpty() && !cpText.startsWith("©")) {
                    cpText = "©" + cpText;
                }
                String firstLine = "\"" + title + "\"";
                if (author != null && !author.trim().isEmpty()) {
                    firstLine += " by " + author;
                }
                List<String> smallLinesList = new ArrayList<>();
                smallLinesList.add(firstLine);
                if (cpText != null && !cpText.isEmpty()) {
                    smallLinesList.add(cpText);
                }
                smallLinesList.add(LabelGrabber.INSTANCE.getLabel("ccli.licence") + " #" + churchCcliNum);
                smallLines = smallLinesList.toArray(new String[smallLinesList.size()]);
            }
            sectionsWithoutSequence.add(new TextSection(sectionTitle, newLyrics, smallLines, true));
        }
        setSectionsInSequence(sectionsWithoutSequence);
    }

    private void setSectionsInSequence(List<TextSection> sectionsWithoutSequence) {
        sectionsInSequence.clear();
        if (sequence != null && !sequence.equals("")) {
            for (String s : sequence.split(" ")) {
                for (TextSection ts : sectionsWithoutSequence) {
                    if (ts.getTitle() != null && !ts.getTitle().equals("")) {
                        String[] title = ts.getTitle().split(" ");
                        StringBuilder sb = new StringBuilder();
                        for (String t : title) {
                            sb.append(t.charAt(0));
                        }
                        if (sb.toString().equals(s)) {
                            sectionsInSequence.add(ts);
                        }
                    }
                }
            }
        } else {
            sectionsInSequence.addAll(sectionsWithoutSequence);
        }
    }

    /**
     * Add a section to this song.
     * <p/>
     *
     * @param section the section to add.
     */
    public void addSection(TextSection section) {
        fontSizeCache.clear();
        if (section.getTheme() == null) {
            section.setTheme(theme);
        }
        sectionsWithoutSequence.add(section);
    }

    /**
     * Add a section to this song at the specified index.
     * <p/>
     *
     * @param index   the index to add the song at.
     * @param section the section to add.
     */
    public void addSection(int index, TextSection section) {
        sectionsInSequence.clear();
        fontSizeCache.clear();
        if (section.getTheme() == null) {
            section.setTheme(theme);
        }
        sectionsWithoutSequence.add(index, section);
    }

    /**
     * Add a number of sections to this song.
     * <p/>
     *
     * @param sections the sections to add.
     */
    public void addSections(TextSection[] sections) {
        fontSizeCache.clear();
        for (TextSection section : sections) {
            addSection(section);
        }
        getSections(); //Initialise sequence order
    }

    /**
     * Replace the text section at the given index with the new section.
     * <p/>
     *
     * @param newSection the new section to use to replace the existing one.
     * @param index      the index of the section to replace.
     */
    public void replaceSection(TextSection newSection, int index) {
        sectionsInSequence.clear();
        sectionsWithoutSequence.set(index, newSection);
        fontSizeCache.clear();
    }

    /**
     * Remove the given text section.
     * <p/>
     *
     * @param index the index of the text section to remove.
     */
    public void removeSection(int index) {
        sectionsInSequence.clear();
        sectionsWithoutSequence.remove(index);
        fontSizeCache.clear();
    }

    /**
     * Get an array of all the sections in this song, including sequence order.
     * <p/>
     *
     * @return the song sections.
     */
    @Override
    public TextSection[] getSections() {
        if (sectionsInSequence.isEmpty()) {
            setSectionsInSequence(sectionsWithoutSequence);
        }
        return sectionsInSequence.toArray(new TextSection[sectionsInSequence.size()]);
    }

    /**
     * Get an array of all the sections in this song, excluding sequence order.
     * <p/>
     *
     * @return the song sections.
     */
    public TextSection[] getSectionsWithoutSequence() {
        return sectionsWithoutSequence.toArray(new TextSection[sectionsWithoutSequence.size()]);
    }

    /**
     * Set the last search text (for highlighting.)
     * <p/>
     *
     * @param lastSearch
     */
    public void setLastSearch(String lastSearch) {
        this.lastSearch = lastSearch;
    }

    /**
     * Get the HTML that should be displayed in the library song list. This
     * depends on what was searched for last, it bolds the search term in the
     * title (if it appears as such.)
     * <p/>
     *
     * @return the appropriate HTML to display the song in the list.
     */
    public String getListHTML() {//@todo wrong method name
        return getTitle();
    }

    public String getLastSearch() {
        return lastSearch;
    }

    /**
     * Get a representation of this song in XML format.
     * <p/>
     *
     * @return the song in XML format.
     */
    @Override
    public String getXML() {
        StringBuilder xml = new StringBuilder();
        xml.append("<song>");
        xml.append("<updateInDB>");
        xml.append(updateInDB);
        xml.append("</updateInDB>");
        xml.append("<title>");
        xml.append(Utils.escapeXML(title));
        xml.append("</title>");
        xml.append("<author>");
        xml.append(Utils.escapeXML(author));
        xml.append("</author>");
        xml.append("<ccli>");
        xml.append(Utils.escapeXML(ccli));
        xml.append("</ccli>");
        xml.append("<copyright>");
        xml.append(Utils.escapeXML(copyright));
        xml.append("</copyright>");
        xml.append("<year>");
        xml.append(Utils.escapeXML(year));
        xml.append("</year>");
        xml.append("<publisher>");
        xml.append(Utils.escapeXML(publisher));
        xml.append("</publisher>");
        xml.append("<key>");
        xml.append(Utils.escapeXML(key));
        xml.append("</key>");
        xml.append("<capo>");
        xml.append(Utils.escapeXML(capo));
        xml.append("</capo>");
        xml.append("<notes>");
        xml.append(Utils.escapeXML(info));
        xml.append("</notes>");
        xml.append("<sequence>");
        xml.append(Utils.escapeXML(sequence));
        xml.append("</sequence>");
        xml.append("<lyrics>");
        for (TextSection section : sectionsWithoutSequence) {
            xml.append(section.getXML());
        }
        xml.append("</lyrics>");

        xml.append("<translation>");
        if (getCurrentTranslationLyrics() != null) {
            xml.append("<name>");
            xml.append(currentTranslation);
            xml.append("</name>");
            xml.append("<tlyrics>");
            xml.append(Utils.escapeXML(translations.get(currentTranslation)));
            xml.append("</tlyrics>");
        } else {
            xml.append("");
        }
        xml.append("</translation>");

        xml.append("<translationoptions>");
        if (translations != null) {
            for (Entry<String, String> translation : translations.entrySet()) {
                String translationLang = translation.getKey();
                String translationLyrics = translation.getValue();
                xml.append("<lang>");
                xml.append(Utils.escapeXML(translationLang));
                xml.append("</lang>");
                xml.append("<lyrics>");
                xml.append(Utils.escapeXML(translationLyrics));
                xml.append("</lyrics>");
            }
        }
        xml.append("</translationoptions>");

        xml.append("</song>");
        return xml.toString();
    }

    /**
     * Get the XML used to print the song (will be transferred via XSLT.)
     *
     * @param includeTranslations true if translations should be included in the
     *                            export, false otherwise.
     * @return the XML used to print the song.
     */
    public String getPrintXML(boolean includeTranslations) {
        StringBuilder xml = new StringBuilder();
        Map<String, String> lyricsMap = new TreeMap<>((String o1, String o2) -> { //Ensure "Default" translation is first
            if (o1.equals("Default")) {
                return -1;
            }
            if (o2.equals("Default")) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        StringBuilder mainLyrics = new StringBuilder();
        for (TextSection section : sectionsInSequence) {
            mainLyrics.append(section.getTitle()).append("\n");
            for (String line : section.getText(printChords, false)) {
                mainLyrics.append(Utils.escapeXML(line)).append("\n");
            }
            mainLyrics.append("\n");
        }
        lyricsMap.put("Default", mainLyrics.toString().trim());
        if (includeTranslations) {
            lyricsMap.putAll(getTranslations());
        }
        xml.append("<songs>");
        for (Entry<String, String> lyricsEntry : lyricsMap.entrySet()) {
            xml.append("<song>");
            xml.append("<title>");
            xml.append(Utils.escapeXML(title));
            if (!lyricsEntry.getKey().equals("Default")) {
                xml.append(" (").append(lyricsEntry.getKey()).append(")");
            }
            xml.append("</title>");
            xml.append("<author>");
            xml.append(Utils.escapeXML(author));
            xml.append("</author>");
            xml.append("<lyrics>");
            xml.append(lyricsEntry.getValue().replace(" ", Character.toString((char) 160)));
            xml.append("</lyrics>");
            xml.append("</song>");
        }
        xml.append("</songs>");
        return xml.toString();
    }

    /**
     * Parse a song in XML format and return the song object.
     * <p/>
     *
     * @param xml the xml string to parse.
     * @return the song, or null if an error occurs.
     */
    public static SongDisplayable parseXML(String xml) {
        try {
            InputStream inputStream = new ByteArrayInputStream(xml.getBytes("UTF8"));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            return parseXML(doc.getFirstChild(), Collections.emptyMap());
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            return null;
        }
    }

    /**
     * Parse a song in XML format and return the song object.
     * <p/>
     *
     * @param inputStream the input stream containing the xml.
     * @return the song, or null if an error occurs.
     */
    public static SongDisplayable parseXML(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            return parseXML(doc.getChildNodes().item(0), Collections.emptyMap());
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            LOGGER.log(Level.INFO, "Couldn't parse the schedule", ex);
            return null;
        }
    }

    /**
     * Parse a song in XML format and return the song object.
     * <p/>
     *
     * @param song the song node to parse.
     * @return the song, or null if an error occurs.
     */
    public static SongDisplayable parseXML(Node song, Map<String, String> fileChanges) {
        NodeList list = song.getChildNodes();
        String title = "";
        String author = "";
        String ccli = "";
        String copyright = "";
        String year = "";
        String publisher = "";
        String key = "";
        String capo = "";
        String notes = "";
        String currentTranslation = "";
        String translationLyrics = "";
        String sequence = "";
        HashMap<String, String> translationOpts = new HashMap<>();
        boolean updateInDB = true;
        List<TextSection> songSections = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeName().equals("updateInDB")) {
                if (node.getTextContent().equals("false")) {
                    updateInDB = false;
                }
            }
            if (node.getNodeName().equals("title")) {
                title = node.getTextContent();
            }
            if (node.getNodeName().equals("ccli")) {
                ccli = node.getTextContent();
            }
            if (node.getNodeName().equals("copyright")) {
                copyright = node.getTextContent();
            }
            if (node.getNodeName().equals("year")) {
                year = node.getTextContent();
            }
            if (node.getNodeName().equals("publisher")) {
                publisher = node.getTextContent();
            }
            if (node.getNodeName().equals("key")) {
                key = node.getTextContent();
            }
            if (node.getNodeName().equals("capo")) {
                capo = node.getTextContent();
            }
            if (node.getNodeName().equals("notes")) {
                notes = node.getTextContent();
            }
            if (node.getNodeName().equals("author")) {
                author = node.getTextContent();
            }
            if (node.getNodeName().equals("lyrics")) {
                NodeList sections = node.getChildNodes();
                for (int j = 0; j < sections.getLength(); j++) {
                    Node sectionNode = sections.item(j);
                    if (sectionNode.getNodeName().equals("section")) {
                        songSections.add(TextSection.parseXML(sectionNode, fileChanges));
                    }
                }
            }
            if (node.getNodeName().equals("translation")) {
                if (node.hasChildNodes()) {
                    NodeList nl = node.getChildNodes();
                    currentTranslation = nl.item(0).getTextContent();
                    translationLyrics = nl.item(1).getTextContent();
                }
            }
            if (node.getNodeName().equals("translationoptions")) {
                NodeList translations = node.getChildNodes();
                String translationOptLang = null;
                String translationOptLyrics = null;
                for (int j = 0; j < translations.getLength(); j++) {
                    Node translationNode = translations.item(j);
                    if (translationNode.getNodeName().equals("lang")) {
                        translationOptLang = translationNode.getTextContent();
                    }
                    if (translationNode.getNodeName().equals("lyrics")) {
                        translationOptLyrics = translationNode.getTextContent();
                        if (translationOptLang != null && translationOptLyrics != null) {
                            translationOpts.put(translationOptLang, translationOptLyrics);
                        }
                    }
                }
            }
            if (node.getNodeName().equals("sequence")) {
                sequence = node.getTextContent();
            }
        }
        SongDisplayable ret = new SongDisplayable(title, author,
                new ThemeDTO(ThemeDTO.DEFAULT_FONT, ThemeDTO.DEFAULT_FONT_COLOR, ThemeDTO.DEFAULT_FONT, ThemeDTO.DEFAULT_TRANSLATE_FONT_COLOR,
                        ThemeDTO.DEFAULT_BACKGROUND, ThemeDTO.DEFAULT_SHADOW, false, false, false, true, -1, 0));
        if (!updateInDB) {
            ret.setNoDBUpdate();
        }
        ret.addSections(songSections.toArray(new TextSection[songSections.size()]));
        ret.setCcli(ccli);
        ret.setCopyright(copyright);
        ret.setYear(year);
        ret.setPublisher(publisher);
        ret.setKey(key);
        ret.setCapo(capo);
        ret.setInfo(notes);
        ret.setTranslations(translationOpts);
        if (!currentTranslation.equals("")) {
            ret.setCurrentTranslationLyrics(currentTranslation);
            ret.addTranslation(currentTranslation, translationLyrics);
        }
        ret.setSequence(sequence);
        ret.refreshLyrics();
        return ret;
    }

    /**
     * Generate a hashcode for this song.
     * <p/>
     *
     * @return the hashcode.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.title != null ? this.title.hashCode() : 0);
        hash = 29 * hash + (this.author != null ? this.author.hashCode() : 0);
        hash = 29 * hash + (this.sectionsWithoutSequence != null ? this.sectionsWithoutSequence.hashCode() : 0);
        hash = 29 * hash + (this.theme != null ? this.theme.hashCode() : 0);
        hash = hash + count;
        return hash;
    }

    /**
     * Determine whether this song equals another object.
     * <p/>
     *
     * @param obj the other object.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SongDisplayable)) {
            return false;
        }
        final SongDisplayable other = (SongDisplayable) obj;
        if ((this.title == null) ? (other.title != null) : !this.title.equals(other.title)) {
            return false;
        }
        if ((this.author == null) ? (other.author != null) : !this.author.equals(other.author)) {
            return false;
        }
        if (this.sectionsWithoutSequence != other.sectionsWithoutSequence && (this.sectionsWithoutSequence == null || !this.sectionsWithoutSequence.equals(other.sectionsWithoutSequence))) {
            return false;
        }
        if (this.theme != other.theme && (this.theme == null || !this.theme.equals(other.theme))) {
            return false;
        }
        return true;
    }

    /**
     * Compare this song to another song, first by title and then by author.
     * <p/>
     *
     * @param other the other song.
     * @return 1 if this song is greater than the other song, 0 if they're the
     * same, and -1 if this is less than the other song.
     */
    @Override
    public int compareTo(SongDisplayable other) {
        Collator collator = Collator.getInstance();
        int result = collator.compare(getTitle(), other.getTitle());
        if (result == 0) {
            if (getAuthor() != null && other.getAuthor() != null) {
                result = collator.compare(getAuthor(), other.getAuthor());
            }
            if (result == 0 && getLyrics(false, false, false) != null && other.getLyrics(false, false, false) != null) {
                result = collator.compare(getLyrics(false, false, false), other.getLyrics(false, false, false));
            }
        }
        return result;
    }

    /**
     * Get a string representation of this song.
     * <p/>
     *
     * @return a string representation of the song.
     */
    @Override
    public String toString() {
        return getXML();
    }

    /**
     * Get the preview icon of this song.
     * <p/>
     *
     * @return the song's preview icon.
     */
    @Override
    public javafx.scene.Node getPreviewIcon() {
        ImageView iv;
        if (getID() < 0) {
            iv = new ImageView(new Image("file:icons/lyricscopy.png"));
        } else if (hasChords()) {
            iv = new ImageView(new Image("file:icons/lyricsandchords.png"));
        } else {
            iv = new ImageView(new Image("file:icons/lyrics.png"));
        }
        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(iv);
        if (!getTranslations().isEmpty()) {
            stackPane.getChildren().add(getTranslationPart());
            stackPane.setAlignment(Pos.BOTTOM_LEFT);
        }
        return stackPane;
    }

    private StackPane getTranslationPart() {
        StackPane ret = new StackPane();
        ImageView iv = new ImageView("file:icons/translate small.png");
        iv.setFitWidth(13);
        iv.setPreserveRatio(true);
        ret.setPadding(new Insets(0,3,0,3));
        ret.getChildren().add(iv);
        ret.setMaxWidth(5);
        ret.setMaxHeight(5);
        ret.setAlignment(Pos.CENTER);
        ret.setStyle("-fx-background-color: #333333;");
        ret.setOpacity(0.9);
        return ret;
    }

    /**
     * Get the preview text of this song.
     * <p/>
     *
     * @return the song's preview text.
     */
    @Override
    public String getPreviewText() {
        String ret = getTitle();
        if (getCurrentTranslationName() != null) {
            ret += " (+ " + getCurrentTranslationName() + ")";
        }
        ret += "\n" + getAuthor();
        return ret;
    }

    /**
     * Remove any duplicate sections in this song.
     */
    public void removeDuplicateSections() {
        Utils.removeDuplicateWithOrder(sectionsWithoutSequence);
        fontSizeCache.clear();
    }

    /**
     * Get all the files used by this song.
     * <p/>
     *
     * @return all the files used by this song.
     */
    @Override
    public Collection<File> getResources() {
        Set<File> ret = new HashSet<>();
        for (TextSection section : getSections()) {
            ThemeDTO sectionTheme = section.getTheme();
            if (sectionTheme != null) {
                Background background = sectionTheme.getBackground();
                ret.addAll(background.getResources());
            }
        }
        return ret;
    }

    public void setTheme(ThemeDTO theme) {
        fontSizeCache.clear();
        this.theme = theme;
    }

    @Override
    public void dispose() {
        //Nothing needed here.
    }
}