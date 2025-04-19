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
package org.quelea.data.db;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.application.Platform;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.quelea.data.ThemeDTO;
import org.quelea.data.db.model.Song;
import org.quelea.data.db.model.Theme;
import org.quelea.data.displayable.SongDisplayable;
import org.quelea.data.displayable.TextSection;
import org.quelea.services.lucene.SongSearchIndex;
import org.quelea.services.utils.DatabaseListener;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.windows.main.widgets.LoadingPane;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

/**
 * Manage songs persistent operations.
 * <p/>
 *
 * @author Michael
 */
public final class SongManager {

    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static volatile SongManager INSTANCE;
    private final SongSearchIndex index;
    private boolean indexIsClear;
    private SoftReference<SongDisplayable[]> cacheSongs = new SoftReference<>(null);
    private final Set<DatabaseListener> listeners;
    private boolean useMongoDB;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> songCollection;

    /**
     * Initialise the song database.
     */
    private SongManager(boolean useMongoDB) {
        listeners = new HashSet<>();
        indexIsClear = true;
        index = new SongSearchIndex();

        this.useMongoDB = useMongoDB;

        if (useMongoDB) {
            mongoClient = MongoClients.create("mongodb://localhost:27017"); // Change as needed
            database = mongoClient.getDatabase("quelea_db");
            songCollection = database.getCollection("songs");
        }
    }

    /**
     * Get the singleton instance of this class. Return null if there was an
     * error with the database.
     * <p/>
     *
     * @return the singleton instance of this class.
     */
    public static synchronized SongManager get(boolean useMongoDB) {
        if (INSTANCE == null) {
            if (HibernateUtil.init()) {
                INSTANCE = new SongManager(useMongoDB);
            } else {
                return null;
            }
        }
        return INSTANCE;
    }

    /**
     * Get the underlying search index used by this database.
     * <p/>
     *
     * @return the search index.
     */
    public SongSearchIndex getIndex() {
        return index;
    }

    /**
     * Register a database listener with this database.
     * <p/>
     *
     * @param listener the listener.
     */
    public void registerDatabaseListener(DatabaseListener listener) {
        listeners.add(listener);
    }

    /**
     * Fire off the database listeners.
     */
    public synchronized void fireUpdate() {
        for (DatabaseListener listener : listeners) {
            listener.databaseChanged();
        }
    }

    public synchronized SongDisplayable[] getSongs() {
        return getSongs(null);
    }

    /**
     * Get all the songs in the database.
     * <p/>
     *
     * @return an array of all the songs in the database.
     */
    public synchronized SongDisplayable[] getSongs(LoadingPane loadingPane) {
        if (cacheSongs.get() != null) {
            return cacheSongs.get();
        }

        if (useMongoDB) {

            List<SongDisplayable> songs = new ArrayList<>();
            for (Document doc : songCollection.find()) {
                LOGGER.log(Level.INFO, "Getting the ID of the document", doc.getLong("id"));

                SongDisplayable song = new SongDisplayable.Builder(doc.getString("title"), doc.getString("author"))
                        .lyrics(doc.getString("lyrics"))
                        .year(doc.getString("year"))
                        .publisher(doc.getString("publisher"))
                        .key(doc.getString("key"))
                        .info(doc.getString("info"))
                        .theme(ThemeDTO.getDTO(ThemeDTO.DEFAULT_THEME.getTheme()))
                        .id(doc.getLong("id")) // Ensure ID is set correctly
                        .get();

                final Theme theme = doc.containsKey("theme") ? new Theme(song.getSections()[0].getTheme().getTheme())
                        : ThemeDTO.DEFAULT_THEME.getTheme();

                final ThemeDTO themedto = ThemeDTO.getDTO(theme);
                for (TextSection section : song.getSections()) {
                    section.setTheme(themedto);
                }
                song.setTheme(themedto);
                songs.add(song);
            }
            return songs.toArray(new SongDisplayable[0]);
        } else{
            final Set<SongDisplayable> songs = new TreeSet<>();
            HibernateUtil.execute((Session session) -> {
                List<Song> songsList = new SongDao(session).getSongs();
                for (int si = 0; si < songsList.size(); si++) {
                    final int finalSi = si;
                    if (loadingPane != null) {
                        Platform.runLater(() -> {
                            loadingPane.setProgress((double) finalSi / songsList.size());
                        });
                    }
                    Song song = songsList.get(si);
                    try {
                        song.getTitle();
                    } catch (Exception ex) {
                        /*
                         * Sometimes (rarely) a song can become corrupt - not entirely
                         * sure why, but this allows us to load the database ok whilst
                         * still skipping over the corrupt entries.
                         */
                        LOGGER.log(Level.WARNING, "Song with id " + song.getId() + " is corrupt, skipping...", ex);
                        continue;
                    }
                    final SongDisplayable songDisplayable = new SongDisplayable.Builder(song.getTitle(),
                            song.getAuthor())
                            .ccli(song.getCcli())
                            .year(song.getYear())
                            .publisher(song.getPublisher())
                            .copyright(song.getCopyright())
                            .key(song.getKey())
                            .info(song.getInfo())
                            .capo(song.getCapo())
                            .translations(song.getTranslations())
                            .sequence(song.getSequence())
                            .lyrics(song.getLyrics())
                            .id(song.getId()).get();
                    final Theme theme = song.getTheme();
                    final ThemeDTO themedto = ThemeDTO.getDTO(theme);
                    for (TextSection section : songDisplayable.getSections()) {
                        section.setTheme(themedto);
                    }
                    songDisplayable.setTheme(themedto);
                    songs.add(songDisplayable);
                }
                if (loadingPane != null) {
                    Platform.runLater(() -> {
                        loadingPane.setProgress(-1);
                    });
                }
            });
            if (indexIsClear) {
                indexIsClear = false;
                LOGGER.log(Level.INFO, "Adding {0} songs to index", songs.size());
                index.addAll(songs);
            }
            SongDisplayable[] songArr = songs.toArray(new SongDisplayable[songs.size()]);
            cacheSongs = new SoftReference<>(songArr);
            return songArr;
        }
    }

    public boolean addSong(final SongDisplayable song, final boolean fireUpdate) {
        return addSong(new SongDisplayable[] { song }, fireUpdate);
    }

    public boolean addSong(final Collection<SongDisplayable> song, final boolean fireUpdate) {
        return addSong(song.toArray(new SongDisplayable[song.size()]), fireUpdate);
    }

    /**
     * Add a song to the database.
     * <p/>
     *
     * @param songs      the songs to add.
     * @param fireUpdate true if the update should be fired to listeners when
     *                   adding this song, false otherwise.
     * @return true if the operation succeeded, false otherwise.
     */
    public synchronized boolean addSong(final SongDisplayable[] songs, final boolean fireUpdate) {
        cacheSongs.clear();
        clearIndex();

        if (useMongoDB) {
            for (SongDisplayable song : songs) {
                if (song.getSectionsWithoutSequence().length > 0) {

                    Document songDoc = new Document("title", song.getTitle())
                            .append("author", song.getAuthor())
                            .append("lyrics", song.getLyrics(true, true, false))
                            .append("year", song.getYear())
                            .append("publisher", song.getPublisher())
                            .append("key", song.getKey())
                            .append("capo", song.getCapo())
                            .append("info", song.getInfo())
                            .append("translations", song.getTranslations())
                            .append("sequence", song.getSequence())
                            .append("ccli", song.getCcli())
                            .append("copyright", song.getCopyright());
                            
                    // Insert the song into MongoDB
                    InsertOneResult  songInsertionResult = songCollection.insertOne(songDoc);

                    LOGGER.log(Level.INFO, "Added the song to DB", songInsertionResult.getInsertedId());

                    String mongoId = songDoc.getObjectId("_id").toHexString(); // Extract the hex string representation of the ObjectId
                    LOGGER.log(Level.INFO, "Generated mongoID", mongoId);
                    long mongoIdLong = new java.math.BigInteger(mongoId, 16).longValue();

                    songDoc.append("id", mongoIdLong);
                    // Update the inserted document with the ID field
                    UpdateResult  updatedResults = songCollection.updateOne(Filters.eq("_id", songDoc.getObjectId("_id")), new Document("$set", new Document("id", mongoIdLong)));
                    LOGGER.log(Level.INFO, "Generated mongoID", updatedResults.getModifiedCount());

                }
            }

        } else {

            final List<SongDisplayable> adjustedSongs = new ArrayList<>();
            for (SongDisplayable song : songs) {
                if (song.getSectionsWithoutSequence().length > 0) {
                    adjustedSongs.add(song);
                }
            }
            if (adjustedSongs.isEmpty()) {
                return false;
            }
            try {
                HibernateUtil.execute((Session session) -> {
                    for (SongDisplayable song : adjustedSongs) {
                        final boolean nullTheme = song.getSections()[0].getTheme() == null;
                        Song newSong = new Song(song.getTitle(),
                                song.getAuthor(),
                                song.getLyrics(true, true, false),
                                song.getCcli(),
                                song.getCopyright(),
                                song.getYear(),
                                song.getPublisher(),
                                song.getKey(),
                                song.getCapo(),
                                song.getInfo(),
                                nullTheme ? ThemeDTO.DEFAULT_THEME.getTheme()
                                        : new Theme(song.getSections()[0].getTheme().getTheme()),
                                song.getTranslations(),
                                song.getSequence());
                        session.save(newSong);
                    }
                });
            } catch (IllegalStateException ex) {
                LOGGER.log(Level.WARNING, "Couldn't add song", ex);
            }

        }

        getSongs();
        if (fireUpdate) {
            fireUpdate();
        }
        return true;
    }

    /**
     * Update a song in the database.
     * <p/>
     *
     * @param song the song to update.
     * @return true if the operation succeeded, false otherwise.
     */
    public synchronized boolean updateSong(final SongDisplayable song) {
        if (useMongoDB) {
            if (song.getID() == -1) {
                LOGGER.log(Level.WARNING, "Invalid song ID (-1), update aborted.");
                LOGGER.log(Level.INFO, "Updating song that doesn't exist, adding instead.");
                addSong(song, true);
                return true;
            }

            Document filter = new Document("id", song.getID());
            Document update = new Document("$set", new Document("title", song.getTitle())
                    .append("author", song.getAuthor())
                    .append("lyrics", song.getLyrics(true, true, false))
                    .append("year", song.getYear())
                    .append("publisher", song.getPublisher())
                    .append("key", song.getKey())
                    .append("info", song.getInfo()));

            UpdateResult result = songCollection.updateOne(filter, update);

            if (result.getMatchedCount() == 0) {
                LOGGER.log(Level.WARNING, "Song update failed, no matching song found: {0}", song.getID());
                return false;
            }
            return true;
        }

        return updateSong(song, true);
    }

    /**
     * Update a song in the database.
     * <p/>
     *
     * @param song          the song to update.
     * @param addIfNotFound true if the song should be added if it's not found,
     *                      false otherwise.
     * @return true if the operation succeeded, false otherwise.
     */
    public synchronized boolean updateSong(final SongDisplayable song, boolean addIfNotFound) {
        index.remove(song);
        try {
            HibernateUtil.execute((Session session) -> {
                Song updatedSong;
                final boolean nullTheme = song.getSections()[0].getTheme() == null;
                try {
                    updatedSong = new SongDao(session).getSongById(song.getID());
                    updatedSong.setAuthor(song.getAuthor());
                    updatedSong.setYear(song.getYear());
                    updatedSong.setCapo(song.getCapo());
                    updatedSong.setCcli(song.getCcli());
                    updatedSong.setCopyright(song.getCopyright());
                    updatedSong.setInfo(song.getInfo());
                    updatedSong.setSequence(song.getSequence());
                    updatedSong.setLyrics(song.getLyrics(true, true, false));
                    updatedSong.setKey(song.getKey());
                    updatedSong.setPublisher(song.getPublisher());
                    updatedSong.setTitle(song.getTitle());
                    updatedSong.setTranslations(song.getTranslations());
                    updatedSong.setTheme(nullTheme ? ThemeDTO.DEFAULT_THEME.getTheme()
                            : new Theme(song.getSections()[0].getTheme().getTheme()));
                    session.update(updatedSong);
                    index.add(song);
                } catch (ObjectNotFoundException e) {
                    LOGGER.log(Level.INFO, "Updating song that doesn't exist, adding instead");
                    addSong(song, true);
                }
            });
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Error with database update, try to remove and add...", ex);
            try {
                removeSong(song);
                LOGGER.log(Level.WARNING, "Try to add...", ex);
                addSong(song, false);
                return true;
            } catch (IllegalStateException ex2) {
                LOGGER.log(Level.WARNING, "Remove / add trick not working.", ex2);
                return false;
            }
        }

        return true;
    }

    /**
     * Remove a song from the database.
     * <p/>
     *
     * @param song the song to remove.
     * @return true if the operation succeeded, false otherwise.
     */
    public synchronized boolean removeSong(final SongDisplayable song) {
        return removeSongs(List.of(song));
    }

    /**
     * Remove songs from the database.
     * <p/>
     *
     * @param songs the songs to remove.
     * @return true if the operation succeeded, false otherwise.
     */
    public synchronized boolean removeSongs(final List<SongDisplayable> songs) {
        List<Long> ids = songs.stream().map(SongDisplayable::getID).collect(Collectors.toList());
        LOGGER.log(Level.INFO, "Removing songs {0}", ids);

        cacheSongs.clear();

        if (useMongoDB) {
            List<ObjectId> mongoIds = new ArrayList<>();
            for (SongDisplayable song : songs) {
                Document songDoc = songCollection.find(new Document("id", song.getID())).first();
                if (songDoc != null) {
                    mongoIds.add(songDoc.getObjectId("_id"));
                }
            }

            if (!mongoIds.isEmpty()) {
                songCollection.deleteMany(Filters.in("_id", mongoIds));
                fireUpdate();
                return true;
            } else {
                LOGGER.log(Level.WARNING, "No matching songs found in MongoDB for removal: {0}", ids);
                return false;
            }
        } else {

            // Hibernate-based removal for non-MongoDB storage
            try {
                HibernateUtil.execute((Session session) -> {
                    for (SongDisplayable song : songs) {
                        Song deletedSong = new SongDao(session).getSongById(song.getID());
                        if (deletedSong != null) {
                            session.delete(deletedSong);
                        }
                    }
                });
            } catch (IllegalStateException ex) {
                LOGGER.log(Level.WARNING, "Couldn't remove songs " + ids, ex);
                return false;
            }

        }

        for (SongDisplayable song : songs) {
            index.remove(song);
        }
        fireUpdate();
        LOGGER.log(Level.INFO, "Removed song {0}", ids);
        return true;
    }

    private void clearIndex() {
        index.clear();
        indexIsClear = true;
    }

    public void close() {
        if (useMongoDB && mongoClient != null) {
            mongoClient.close();
        }
    }
}
