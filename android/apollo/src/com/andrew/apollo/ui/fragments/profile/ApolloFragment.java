/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments.profile;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.*;
import android.widget.*;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.adapters.ApolloFragmentAdapter;
import com.andrew.apollo.menu.CreateNewPlaylist;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.model.*;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.activities.BaseActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.widgets.ProfileTabCarousel;
import com.andrew.apollo.widgets.VerticalScrollListener;
import com.frostwire.android.R;
import com.frostwire.logging.Logger;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.List;

/**
 * Created by gubatron on 1/26/16 on a plane.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class ApolloFragment<T extends ApolloFragmentAdapter<I>, I>
        extends Fragment implements
        LoaderManager.LoaderCallbacks<List<I>>,
        AdapterView.OnItemClickListener,
        AbsListView.OnScrollListener,
        MusicStateListener {

    public volatile static long LAST_REFRESH_TIMESTAMP;

    private static Logger LOGGER = Logger.getLogger(ApolloFragment.class);

    private final int GROUP_ID;
    /**
     * LoaderCallbacks identifier
     */
    protected final int LOADER_ID;
    /**
     * The list view
     */
    protected ListView mListView;

    /**
     * The grid view
     */
    protected GridView mGridView;

    /**
     * The adapter for the list
     */
    protected T mAdapter;
    /**
     * Represents a song/album/
     */
    protected I mItem;

    /**
     * Song list. The playlist's, the album's, the artist's discography available.
     */
    protected long[] mSongList;

    /**
     * Id of a context menu item
     */
    protected long mSelectedId;

    /**
     * The Id of the playlist the song belongs to
     */
    protected long mPlaylistId;

    /**
     * Song, album, and artist name used in the context menu
     */
    protected String mSongName, mAlbumName, mArtistName;

    /**
     * Profile header
     */
    protected ProfileTabCarousel mProfileTabCarousel;

    protected ViewGroup mRootView;

    protected abstract T createAdapter();

    protected abstract String getLayoutTypeName();

    public abstract void onItemClick(final AdapterView<?> parent, final View view, final int position,
                                     final long id);

    protected ApolloFragment(int groupId, int loaderId) {
        LOGGER.info(getClass().getName() + ": Constructor("+groupId+","+" "+loaderId+")");
        this.GROUP_ID = groupId;
        this.LOADER_ID = loaderId;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(final Activity activity) {
        LOGGER.info(getClass().getName() + ": onAttach()");
        super.onAttach(activity);
        mProfileTabCarousel = (ProfileTabCarousel) activity.findViewById(R.id.activity_profile_base_tab_carousel);

        // Register the music status listener
        ((BaseActivity)activity).setMusicStateListenerListener(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        LOGGER.info(getClass().getName() + ": onCreateView()");
        // The View for the fragment's UI
        if (isSimpleLayout()) {
            mRootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
            initListView();
        } else {
            // this inflate here is crashing.
            LOGGER.info(getClass().getName() + ": About to inflate grid_base.");
            mRootView = (ViewGroup) inflater.inflate(R.layout.grid_base, null);
            LOGGER.info(getClass().getName() + ": Inflated it, got it in mRootView.");

            LOGGER.info(getClass().getName() + ": About to initGridView().");
            initGridView();
            LOGGER.info(getClass().getName() + ": Done with initGridView().");
        }
        return mRootView;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        LOGGER.info(getClass().getName() + ": onCreate()");
        super.onCreate(savedInstanceState);
        // Create the adapter
        mAdapter = createAdapter();
    }

    public T getAdapter() {
        LOGGER.info(getClass().getName() + ": getAdapter()");
        return mAdapter;
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        LOGGER.info(getClass().getName() + ": onCreateContextMenu()");
        super.onCreateContextMenu(menu, v, menuInfo);

        // Get the position of the selected item
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int mSelectedPosition = info.position - mAdapter.getOffset();

        // Create a new song
        mItem = mAdapter.getItem(mSelectedPosition);

        // TODO: Remove these mutable properties, parametrize the onMenuEvent handlers.
        if (mItem instanceof Song) {
            Song mSong = (Song) mItem;
            mSelectedId = mSong.mSongId;
            mSongName = mSong.mSongName;
            mAlbumName = mSong.mAlbumName;
            mArtistName = mSong.mArtistName;
            mSongList = null;
        } else if (mItem instanceof Album) {
            Album mAlbum = (Album) mItem;
            mSelectedId = mAlbum.mAlbumId;
            mSongName = null;
            mAlbumName = mAlbum.mAlbumName;
            mArtistName = mAlbum.mArtistName;
            mSongList = MusicUtils.getSongListForAlbum(getActivity(), mAlbum.mAlbumId);
        } else if (mItem instanceof Artist) {
            Artist mArtist = (Artist) mItem;
            mSelectedId = mArtist.mArtistId;
            mSongName = null;
            mArtistName = mArtist.mArtistName;
            mSongList = MusicUtils.getSongListForArtist(getActivity(), mArtist.mArtistId);
        } else if (mItem instanceof Genre) {
            Genre mGenre = (Genre) mItem;
            mSelectedId = mGenre.mGenreId;
            mSongList = MusicUtils.getSongListForGenre(getActivity(), mGenre.mGenreId);
        } else if (mItem instanceof Playlist) {
            Playlist mPlaylist = (Playlist) mItem;
            mSelectedId = mPlaylist.mPlaylistId;
            mSongList = MusicUtils.getSongListForPlaylist(getActivity(), mPlaylist.mPlaylistId);
        }

        // Play the selected songs
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE, getString(R.string.context_menu_play_selection));

        // Play the next song
        menu.add(GROUP_ID, FragmentMenuItems.PLAY_NEXT, Menu.NONE, getString(R.string.context_menu_play_next));

        // Add the song/album to the queue
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, getString(R.string.add_to_queue));

        // Add the song to favorite's playlist
        menu.add(GROUP_ID, FragmentMenuItems.ADD_TO_FAVORITES, Menu.NONE, R.string.add_to_favorites);

        // Add the song/album to a playlist
        final SubMenu subMenu =
                menu.addSubMenu(GROUP_ID, FragmentMenuItems.ADD_TO_PLAYLIST, Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), GROUP_ID, subMenu, true);

        if (mItem instanceof Song) {
            menu.add(GROUP_ID, FragmentMenuItems.USE_AS_RINGTONE, Menu.NONE, getString(R.string.context_menu_use_as_ringtone));
        }

        // More by artist
        menu.add(GROUP_ID, FragmentMenuItems.MORE_BY_ARTIST, Menu.NONE, getString(R.string.context_menu_more_by_artist));

        // Delete the album
        menu.add(GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE,
                getString(R.string.context_menu_delete));
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        LOGGER.info(getClass().getName() + ": onContextItemSelected()");
        if (item.getGroupId() == GROUP_ID) {
            final long[] songList = mSongList != null ?
                    mSongList :
                    new long[] { mSelectedId };

            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    MusicUtils.playAll(songList, 0, false);
                    return true;
                case FragmentMenuItems.PLAY_NEXT:
                    MusicUtils.playNext(songList);
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    MusicUtils.addToQueue(getActivity(), songList);
                    return true;
                case FragmentMenuItems.ADD_TO_FAVORITES:
                    onAddToFavorites();
                    return true;
                case FragmentMenuItems.REMOVE_FROM_FAVORITES:
                    onRemoveFromFavorites();
                    return true;
                case FragmentMenuItems.NEW_PLAYLIST:
                    CreateNewPlaylist.getInstance(songList).show(getFragmentManager(), "CreatePlaylist");
                    return true;
                case FragmentMenuItems.PLAYLIST_SELECTED:
                    final long playlistId = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(getActivity(), songList, playlistId);
                    return true;
                case FragmentMenuItems.USE_AS_RINGTONE:
                    MusicUtils.setRingtone(getActivity(), mSelectedId);
                    return true;
                case FragmentMenuItems.DELETE:
                    return onDelete(songList);
                case FragmentMenuItems.MORE_BY_ARTIST:
                    NavUtils.openArtistProfile(getActivity(), mArtistName);
                    return true;
                case FragmentMenuItems.REMOVE_FROM_PLAYLIST:
                    return onRemoveFromPlaylist();
                case FragmentMenuItems.REMOVE_FROM_RECENT:
                    return onRemoveFromRecent();
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    private boolean onRemoveFromRecent()  {
        LOGGER.info(getClass().getName() + ": onRemoveFromRecent()");
        RecentStore.getInstance(getActivity()).removeItem(mSelectedId);
        MusicUtils.refresh();
        refresh();
        return true;
    }

    private boolean onDelete(long[] songList) {
        LOGGER.info(getClass().getName() + ": onDelete()");
        if (songList == null || songList.length == 0) {
            return false;
        }

        String title = getResources().getString(R.string.unknown);

        if (mItem instanceof Song) {
            title = ((Song) mItem).mSongName;
        } else if (mItem instanceof Album) {
            title = ((Album) mItem).mAlbumName;
        } else if (mItem instanceof Artist) {
            title = ((Artist) mItem).mArtistName;
        }

        DeleteDialog.newInstance(title, songList, null).setOnDeleteCallback(new DeleteDialog.DeleteDialogCallback() {
            @Override
            public void onDelete(long[] id) {
                refresh();
            }
        }).show(getFragmentManager(), "DeleteDialog");
        return true;
    }

    private boolean onRemoveFromPlaylist() {
        LOGGER.info(getClass().getName() + ": onRemoveFromPlaylist()");
        mAdapter.remove(mItem);
        mAdapter.notifyDataSetChanged();
        if (mItem instanceof Song) {
            Song song = (Song) mItem;
            MusicUtils.removeFromPlaylist(getActivity(), song.mSongId, mPlaylistId);
            refresh();
            return true;
        }
        return false;
    }

    private void onAddToFavorites() {
        if (mSongList != null) {
            for (Long songId : mSongList) {
                try {
                    final Song song = MusicUtils.getSong(getActivity(), songId);
                    if (song != null) {
                        FavoritesStore.getInstance(getActivity()).addSongId(songId, song.mSongName, song.mAlbumName, song.mArtistName);
                    }
                } catch (Throwable ignored) {
                    ignored.printStackTrace();
                }
            }
        } else if (mSelectedId != -1){
            FavoritesStore.getInstance(getActivity()).addSongId(
                    mSelectedId, mSongName, mAlbumName, mArtistName);
        }
    }

    private void onRemoveFromFavorites() {
        mAdapter.remove(mItem);
        mAdapter.notifyDataSetChanged();
        FavoritesStore.getInstance(getActivity()).removeItem(mSelectedId);
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        LOGGER.info(getClass().getName() + ": onActivityCreated()");
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);

        // Start the loader
        final Bundle arguments = getArguments();
        try {
            getLoaderManager().initLoader(LOADER_ID, arguments, this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        LOGGER.info(getClass().getName() + ": onSaveInstanceState()");
        super.onSaveInstanceState(outState);
        outState.putAll(getArguments() != null ? getArguments() : new Bundle());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<List<I>> loader, final List<I> data) {
        LOGGER.info(getClass().getName() + ": onLoadFinished()");
        // Check for any errors
        if (data == null || data.isEmpty()) {
            mAdapter.unload();
            mAdapter.notifyDataSetChanged();

            // Set the empty text
            final TextView empty = (TextView) mRootView.findViewById(R.id.empty);
            if (empty != null) {
                empty.setText(getString(R.string.empty_music));

                if (isSimpleLayout()) {
                    mListView.setEmptyView(empty);
                } else {
                    mGridView.setEmptyView(empty);
                }
            }
            return;
        }

        if (mAdapter == null) {
            mAdapter = createAdapter();
            if (isSimpleLayout()) {
                mListView.setAdapter(mAdapter);
            } else {
                mGridView.setAdapter(mAdapter);
            }
        }

        // Start fresh
        if (mAdapter != null) {
            mAdapter.unload();
            mAdapter.setDataList(data);

            for (final I item : data) {
                mAdapter.add(item);
            }

            if (mAdapter instanceof ApolloFragmentAdapter.Cacheable) {
                ((ApolloFragmentAdapter.Cacheable) mAdapter).buildCache();
            }

            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoaderReset(final Loader<List<I>> loader) {
        LOGGER.info(getClass().getName() + ": onLoaderReset()");
        // Clear the data in the adapter
        if (mAdapter != null) {
            mAdapter.unload();
        }
    }

    /**
     * Restarts the loader.
     * (Don't do so until 10 seconds later if you refreshed already)
     */
    public void refresh() {
        long start = System.currentTimeMillis();

        if (start - LAST_REFRESH_TIMESTAMP < 10000) {
            LOGGER.info(getClass().getName() + " - too early to refresh() aborting.");
            return;
        }
        LAST_REFRESH_TIMESTAMP = start;

        LOGGER.info(getClass().getName() + " - refresh() started.");
        //SystemClock.sleep(2);

        // Scroll to the stop of the list before restarting the loader.
        // Otherwise, if the user has scrolled enough to move the header, it
        // becomes misplaced and needs to be reset.
        if (mListView != null) {
            mListView.setSelection(0);
        } else if (mGridView != null) {
            mGridView.setSelection(0);
        }
        getLoaderManager().restartLoader(LOADER_ID, getArguments(), this);

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        long total = System.currentTimeMillis() - start;
        LOGGER.info(getClass().getName() + " - refresh() finished. ("+total+" ms)");
    }

    @Override
    public void onPause() {
        LOGGER.info(getClass().getName() + ": onPause()");
        super.onPause();
        if (mAdapter != null) {
            mAdapter.flush();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }

    public void restartLoader() {
        LOGGER.info(getClass().getName() + ": restartLoader()");
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    public void onMetaChanged() {
        restartLoader();
    }

    protected boolean isSimpleLayout() {
        return PreferenceUtils.getInstance(getActivity()).isSimpleLayout(getLayoutTypeName());
    }

    protected boolean isDetailedLayout() {
        return PreferenceUtils.getInstance(getActivity()).isDetailedLayout(getLayoutTypeName());
    }

    /**
     * Sets up the grid view
     */
    protected void initGridView() {
        LOGGER.info(getClass().getName() + ": initGridView()");
        // Initialize the grid
        mGridView = (GridView) mRootView.findViewById(R.id.grid_base);

        if (mGridView != null && mAdapter != null) {
            // Set the data behind the grid
            mGridView.setAdapter(mAdapter);
            // Set up the helpers
            initAbsListView(mGridView);
        }
        if (ApolloUtils.isLandscape(getActivity())) {
            if (isDetailedLayout()) {
                if (mAdapter != null) {
                    mAdapter.setLoadExtraData(true);
                }
                mGridView.setNumColumns(2);
            } else {
                mGridView.setNumColumns(4);
            }
        } else {
            if (isDetailedLayout()) {
                if (mAdapter != null) {
                    mAdapter.setLoadExtraData(true);
                }
                mGridView.setNumColumns(1);
            } else {
                mGridView.setNumColumns(2);
            }
        }
    }

    /**
     * Sets up various helpers for both the list and grid
     *
     * @param list The list or grid
     */
    private void initAbsListView(final AbsListView list) {
        LOGGER.info(getClass().getName() + ": initAbsListView()");
        // Release any references to the recycled Views
        list.setRecyclerListener(new RecycleHolder());
        // Listen for ContextMenus to be created
        list.setOnCreateContextMenuListener(this);
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);

        // To help make scrolling smooth
        // from initAbsListView original code.
        list.setOnScrollListener(this);

        // To help make scrolling smooth
        if (mProfileTabCarousel != null) {
            list.setOnScrollListener(new VerticalScrollListener(null, mProfileTabCarousel, 0));
            // Remove the scrollbars and padding for the fast scroll
            list.setVerticalScrollBarEnabled(false);
            list.setFastScrollEnabled(false);
            list.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * Sets up the list view
     */
    protected void initListView() {
        LOGGER.info(getClass().getName() + ": initListView()");
        // Initialize the grid
        mListView = (ListView) mRootView.findViewById(R.id.list_base);

        // Set the data behind the list
        if (mAdapter == null) {
            LOGGER.warn(this.getClass().getName() + " doesn't have an adapter ready and the listView will be empty.");
        }


        if (mAdapter != null) {
            mListView.setAdapter(mAdapter);
        }

        // Set up the helpers
        initAbsListView(mListView);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.setPauseDiskCache(true);
        } else {
            mAdapter.setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Pause disk cache access to ensure smoother scrolling
     */
    protected final VerticalScrollListener.ScrollableHeader mScrollableHeader = new VerticalScrollListener.ScrollableHeader() {
        @Override
        public void onScrollStateChanged(final AbsListView view, final int scrollState) {
            if (mAdapter == null) {
                return;
            }
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                    || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                mAdapter.setPauseDiskCache(true);
            } else {
                mAdapter.setPauseDiskCache(false);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * @return The position of an item in the list or grid based on the id of
     *         the currently playing album.
     */
    protected int getItemPositionByAlbum() {
        final long albumId = MusicUtils.getCurrentAlbumId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (((Album) mAdapter.getItem(i)).mAlbumId == albumId) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Scrolls the list to the currently playing album when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentAlbum() {
        final int currentAlbumPosition = getItemPositionByAlbum();

        if (currentAlbumPosition != 0) {
            if (isSimpleLayout()) {
                mListView.setSelection(currentAlbumPosition);
            } else {
                mGridView.setSelection(currentAlbumPosition);
            }
        }
    }

    /**
     * Scrolls the list to the currently playing song when the user touches the
     * header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentSong() {
        final int currentSongPosition = getItemPositionBySong();

        if (currentSongPosition != 0) {
            mListView.setSelection(currentSongPosition);
        }
    }

    /**
     * @return The position of an item in the list based on the name of the
     *         currently playing song.
     */
    protected int getItemPositionBySong() {
        final long trackId = MusicUtils.getCurrentAudioId();
        if (mAdapter == null) {
            return 0;
        }
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (((Song) mAdapter.getItem(i)).mSongId == trackId) {
                return i;
            }
        }
        return 0;
    }
}
