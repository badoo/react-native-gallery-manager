/**
 * Created by pentarex on 26.01.18.
 */

package org.pentarex.rngallerymanager;

import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

public class RNGalleryManagerModule extends ReactContextBaseJavaModule {

    public static final String RNGALLERY_MANAGER = "RNGalleryManager";
    private static ReactApplicationContext reactContext = null;


    public RNGalleryManagerModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
    }

    @Override
    public String getName() {
        return RNGALLERY_MANAGER;
    }


    @ReactMethod
    public WritableMap getAssets(final ReadableMap params, final Promise promise) {
        if (isJellyBeanOrLater()) {

            if (promise != null) {
                promise.reject(new Exception("Version of Android must be > JellyBean"));
            }

            return null;
        }

        String requestedType = "all";
        if (params.hasKey("type")) {
            requestedType = params.getString("type");
        }

        Integer limit = 10;
        if (params.hasKey("limit")) {
            limit = params.getInt("limit");
        }
        Integer startFrom = 0;
        if (params.hasKey("startFrom")) {
            startFrom = params.getInt("startFrom");
        }
        String albumName = null;
        if (params.hasKey("albumName")) {
            albumName = params.getString("albumName");
        }

        WritableMap response = new WritableNativeMap();

        Cursor gallery = null;
        try {
            gallery = GalleryCursorManager.getAssetCursor(requestedType, albumName, reactContext);
            WritableArray assets = new WritableNativeArray();

            if (gallery.getCount() <= startFrom) {

                if (promise != null) {
                    promise.reject("gallery index out of bound", "");
                }

                return null;
            } else {
                response.putInt("totalAssets", gallery.getCount());
                boolean hasMore = gallery.getCount() > startFrom + limit;
                response.putBoolean("hasMore", hasMore);
                if(hasMore) {
                    response.putInt("next", startFrom + limit);
                } else {
                    response.putInt("next", gallery.getCount());
                }
                gallery.moveToPosition(startFrom);
            }

            do {
                WritableMap asset = getAsset(gallery);

                // Only add assets which file exists
                if (isValidAsset(asset)) {
                    assets.pushMap(asset);
                }

                if (gallery.getPosition() == (startFrom + limit) - 1) break;
            } while (gallery.moveToNext());

            response.putArray("assets", assets);

            if (promise == null) {
                return response;
            } else {
                promise.resolve(response);
            }

        } catch (SecurityException ex) {
            System.err.println(ex);
        } finally {
            if (gallery != null) gallery.close();
        }

        return null;
    }


    @ReactMethod
    public void getAlbums(final ReadableMap params, final Promise promise) {
        if (isJellyBeanOrLater()) {
            promise.reject(new Exception("Version of Android must be > JellyBean"));
            return;
        }

        WritableMap response = new WritableNativeMap();

        String requestedType = "all";
        if (params.hasKey("type")) {
            requestedType = params.getString("type");
        }

        Cursor gallery = null;
        try {
            gallery = GalleryCursorManager.getAlbumCursor(requestedType, reactContext);
            WritableArray albums = new WritableNativeArray();
            gallery.moveToFirst();
            do {
                WritableMap album = getAlbum(gallery);

                // XXX: some devices like Samsung might have a dummy asset in an
                // empty gallery so we check if the actual asset exists
                if (album.getInt("assetCount") == 1) {
                    WritableMap paramsAssets = new WritableNativeMap();
                    paramsAssets.putString("type", requestedType);

                    if (album.hasKey("albumName")) {
                        paramsAssets.putString("albumName", album.getString("albumName"));
                    }

                    WritableMap albumAssets = getAssets(paramsAssets, null);
                    ReadableArray assets = albumAssets.getArray("assets");

                    if (assets.size() > 0) {
                        ReadableMap uniqueAsset = assets.getMap(0);

                        if (!isValidAsset(uniqueAsset)) {
                            continue;
                        }
                    }

                }

                albums.pushMap(album);
            } while (gallery.moveToNext());

            response.putInt("totalAlbums", albums.size());
            response.putArray("albums", albums);

            promise.resolve(response);

        } catch (SecurityException ex) {
            System.err.println(ex);
        } finally {
            if (gallery != null) gallery.close();
        }

    }

    private WritableMap getAsset(Cursor gallery) {
        WritableMap asset = new WritableNativeMap();
        int mediaType = gallery.getInt(gallery.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE));
        String mimeType = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE));
        String creationDate = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED));
        String fileName = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
        Double height = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT));
        Double width = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns.WIDTH));
        String originalUri = gallery.getString(gallery.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        String uri = "file://" + originalUri;
        Double id = gallery.getDouble(gallery.getColumnIndex(MediaStore.Files.FileColumns._ID));


        asset.putString("mimeType", mimeType);
        asset.putString("creationDate", creationDate);
        asset.putDouble("height", height);
        asset.putDouble("width", width);
        asset.putString("filename", fileName);
        asset.putDouble("id", id);
        asset.putString("uri", uri);
        asset.putString("originalUri", originalUri);

        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
            asset.putDouble("duration", 0);
            asset.putString("type", "image");

        } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            Double duration = gallery.getDouble(gallery.getColumnIndex(MediaStore.Video.VideoColumns.DURATION));
            asset.putDouble("duration", duration / 1000);
            asset.putString("type", "video");
        }
        return asset;
    }

    private WritableMap getAlbum(Cursor gallery) {
        WritableMap album = new WritableNativeMap();
        String albumName = gallery.getString(gallery.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME));
        int assetCount = gallery.getInt(gallery.getColumnIndex("assetCount"));
        album.putString("title", albumName);
        album.putInt("assetCount", assetCount);
        return album;
    }


    private Boolean isJellyBeanOrLater() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
    }

    private Boolean isValidAsset(ReadableMap asset) {
        String uri = null;

        if (asset != null && asset.hasKey("originalUri")) {
            uri = asset.getString("originalUri");
        }

        if (uri != null) {
            try {
                File assetFile = new File(uri);
                return !isForbiddenAssetName(assetFile.getName()) && assetFile.exists();
            } catch (Exception e) {}
        }

        return false;
    }

    private Boolean isForbiddenAssetName(String filename) {
        // !$&Welcome@#Image.jpg is a filename of an asset that Samsung
        // devices use in empty albums
        return filename.equals("!$&Welcome@#Image.jpg");
    }
}
