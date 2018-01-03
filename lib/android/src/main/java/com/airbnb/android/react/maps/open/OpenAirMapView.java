package com.airbnb.android.react.maps.open;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.airbnb.android.react.maps.open.polyline.OpenAirMapPolyline;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class OpenAirMapView extends MapView {
    public MapView map;
    private ProgressBar mapLoadingProgressBar;
    private RelativeLayout mapLoadingLayout;
    private ImageView cacheImageView;
    private Boolean isMapLoaded = false;
    private Integer loadingBackgroundColor = null;
    private Integer loadingIndicatorColor = null;
    private final int baseMapPadding = 50;

    private boolean showUserLocation = false;
    private boolean handlePanDrag = false;
    private boolean moveOnMarkerPress = true;
    private boolean cacheEnabled = false;
    private boolean initialRegionSet = false;
    private int cameraMoveReason = 0;

    private static final String[] PERMISSIONS = new String[] {
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"
    };

    private final List<OpenAirMapFeature> features = new ArrayList<>();
    private final Map<Polyline, OpenAirMapPolyline> polylineMap = new HashMap<>();
    private final GestureDetectorCompat gestureDetector;
    private final OpenAirMapManager manager;
    private LifecycleEventListener lifecycleListener;
    private boolean paused = false;
    private boolean destroyed = false;
    private final ThemedReactContext context;
    private final EventDispatcher eventDispatcher;

    private static boolean contextHasBug(Context context) {
        return context == null ||
        context.getResources() == null ||
        context.getResources().getConfiguration() == null;
    }

    // We do this to fix this bug:
    // https://github.com/airbnb/react-native-maps/issues/271
    //
    // which conflicts with another bug regarding the passed in context:
    // https://github.com/airbnb/react-native-maps/issues/1147
    //
    // Doing this allows us to avoid both bugs.
//    private static Context getNonBuggyContext(ThemedReactContext reactContext, ReactApplicationContext appContext) {
//        Context superContext = reactContext;
//        if (!contextHasBug(appContext.getCurrentActivity())) {
//            superContext = appContext.getCurrentActivity();
//        } else if (contextHasBug(superContext)) {
//        // we have the bug! let's try to find a better context to use
//            if (!contextHasBug(reactContext.getCurrentActivity())) {
//            superContext = reactContext.getCurrentActivity();
//            } else if (!contextHasBug(reactContext.getApplicationContext())) {
//            superContext = reactContext.getApplicationContext();
//            } else {
//            // ¯\_(ツ)_/¯
//            }
//        }
//        return superContext;
//    }

    public OpenAirMapView(ThemedReactContext reactContext,
                      ReactApplicationContext appContext,
                      OpenAirMapManager manager) {
        super(reactContext);

        this.manager = manager;
        this.context = reactContext;

//        super.onCreate(null);
        // TODO(lmr): what about onStart????
//        super.onResume();
//        super.getMapAsync(this);

    final OpenAirMapView view = this;

        gestureDetector = new GestureDetectorCompat(reactContext, new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent e1,
                                MotionEvent e2,
                                float distanceX,
                                float distanceY) {
            if (handlePanDrag) {
//                onPanDrag(e2);
            }
            return false;
        }
    });

        this.addOnLayoutChangeListener(new OnLayoutChangeListener() {
@Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
        int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (!paused) {
//                OpenAirMapView.this.cacheView();
            }
        }
        });

        eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    }

    private boolean hasPermissions() {
        int permission0 = checkSelfPermission(getContext(), PERMISSIONS[0]);
        int permission1 = checkSelfPermission(getContext(), PERMISSIONS[1]);

        return permission0 == PackageManager.PERMISSION_GRANTED ||
               permission1 == PackageManager.PERMISSION_GRANTED;
    }

    /*
      onDestroy is final method so I can't override it.
   */
    public synchronized void doDestroy() {
        if (destroyed) {
            return;
        }

        destroyed = true;

        if (lifecycleListener != null && context != null) {
            context.removeLifecycleEventListener(lifecycleListener);
            lifecycleListener = null;
        }
        if (!paused) {
//            onPause();
            paused = true;
        }
//        onDestroy();
    }

    public void setInitialRegion(ReadableMap initialRegion) {
        if (!initialRegionSet && initialRegion != null) {
            setRegion(initialRegion);
            initialRegionSet = true;
        }
    }

    public void setRegion(ReadableMap region) {
        if (region == null) return;

        Double lng = region.getDouble("longitude");
        Double lat = region.getDouble("latitude");
        Double lngDelta = region.getDouble("longitudeDelta");
        Double latDelta = region.getDouble("latitudeDelta");
        GeoPoint southwest = new GeoPoint(lat - latDelta / 2, lng - lngDelta / 2);
        GeoPoint northeast = new GeoPoint(lat + latDelta / 2, lng + lngDelta / 2);

        Bounds bounds = new Bounds(southwest, northeast);

        if (super.getHeight() <= 0 || super.getWidth() <= 0) {
            // in this case, our map has not been laid out yet, so we save the bounds in a local
            // variable, and make a guess of zoomLevel 10. Not to worry, though: as soon as layout
            // occurs, we will move the camera to the saved bounds. Note that if we tried to move
            // to the bounds now, it would trigger an exception.
//            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 10));
//            boundsToMove = bounds;
        } else {
//            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
//            boundsToMove = null;
        }
    }

    public void setShowsUserLocation(boolean showUserLocation) {
        // hold onto this for lifecycle handling
        this.showUserLocation = showUserLocation;
        if (hasPermissions()) {
            //noinspection MissingPermission
            map.setEnabled(showUserLocation);
//            map.setMyLocationEnabled(showUserLocation);
        }
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void enableMapLoading(boolean loadingEnabled) {
        if (loadingEnabled && !this.isMapLoaded) {
        this.getMapLoadingLayoutView().setVisibility(View.VISIBLE);
        }
    }

    public void setMoveOnMarkerPress(boolean moveOnPress) {
        this.moveOnMarkerPress = moveOnPress;
    }

    public void setLoadingBackgroundColor(Integer loadingBackgroundColor) {
        this.loadingBackgroundColor = loadingBackgroundColor;

        if (this.mapLoadingLayout != null) {
        if (loadingBackgroundColor == null) {
        this.mapLoadingLayout.setBackgroundColor(Color.WHITE);
        } else {
        this.mapLoadingLayout.setBackgroundColor(this.loadingBackgroundColor);
        }
        }
    }

    public void setLoadingIndicatorColor(Integer loadingIndicatorColor) {
        this.loadingIndicatorColor = loadingIndicatorColor;
        if (this.mapLoadingProgressBar != null) {
            Integer color = loadingIndicatorColor;
            if (color == null) {
                color = Color.parseColor("#606060");
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList progressTintList = ColorStateList.valueOf(loadingIndicatorColor);
            ColorStateList secondaryProgressTintList = ColorStateList.valueOf(loadingIndicatorColor);
            ColorStateList indeterminateTintList = ColorStateList.valueOf(loadingIndicatorColor);

            this.mapLoadingProgressBar.setProgressTintList(progressTintList);
            this.mapLoadingProgressBar.setSecondaryProgressTintList(secondaryProgressTintList);
            this.mapLoadingProgressBar.setIndeterminateTintList(indeterminateTintList);
        } else {
            PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
                mode = PorterDuff.Mode.MULTIPLY;
            }
            if (this.mapLoadingProgressBar.getIndeterminateDrawable() != null)
                this.mapLoadingProgressBar.getIndeterminateDrawable().setColorFilter(color, mode);
            if (this.mapLoadingProgressBar.getProgressDrawable() != null)
                this.mapLoadingProgressBar.getProgressDrawable().setColorFilter(color, mode);
            }
        }
    }

    public void setHandlePanDrag(boolean handlePanDrag) {
            this.handlePanDrag = handlePanDrag;
            }

    public int getFeatureCount() {
        return features.size();
        }

    public View getFeatureAt(int index) {
        return features.get(index);
        }

    public void removeFeatureAt(int index) {
        OpenAirMapFeature feature = features.remove(index);
//        if (feature instanceof OpenAirMapMarker) {
//            markerMap.remove(feature.getFeature());
//        }
        feature.removeFromMap(map);
    }

    public WritableMap makeClickEventData(GeoPoint point) {
        WritableMap event = new WritableNativeMap();

        WritableMap coordinate = new WritableNativeMap();
        coordinate.putDouble("latitude", point.getLatitude());
        coordinate.putDouble("longitude", point.getLongitude());
        event.putMap("coordinate", coordinate);

        Projection projection = map.getProjection();
        Point screenPoint = projection.toProjectedPixels(point, null);

        WritableMap position = new WritableNativeMap();
        position.putDouble("x", screenPoint.x);
        position.putDouble("y", screenPoint.y);
        event.putMap("position", position);

        return event;
    }

    public void animateToRegion(GeoPoint bounds, int duration) {
        if (map == null) return;
        map.getController().animateTo(bounds);
    }

    public void animateToCoordinate(GeoPoint coordinate, int duration) {
        if (map == null) return;
    }

    private ProgressBar getMapLoadingProgressBar() {
        if (this.mapLoadingProgressBar == null) {
        this.mapLoadingProgressBar = new ProgressBar(getContext());
        this.mapLoadingProgressBar.setIndeterminate(true);
        }
        if (this.loadingIndicatorColor != null) {
        this.setLoadingIndicatorColor(this.loadingIndicatorColor);
        }
        return this.mapLoadingProgressBar;
    }

    private RelativeLayout getMapLoadingLayoutView() {
        if (this.mapLoadingLayout == null) {
        this.mapLoadingLayout = new RelativeLayout(getContext());
        this.mapLoadingLayout.setBackgroundColor(Color.LTGRAY);
        this.addView(this.mapLoadingLayout,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        this.mapLoadingLayout.addView(this.getMapLoadingProgressBar(), params);

        this.mapLoadingLayout.setVisibility(View.INVISIBLE);
        }
        this.setLoadingBackgroundColor(this.loadingBackgroundColor);
        return this.mapLoadingLayout;
    }

    private ImageView getCacheImageView() {
        if (this.cacheImageView == null) {
            this.cacheImageView = new ImageView(getContext());
            this.addView(this.cacheImageView,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
            this.cacheImageView.setVisibility(View.INVISIBLE);
        }
        return this.cacheImageView;
    }

    private void removeCacheImageView() {
        if (this.cacheImageView != null) {
        ((ViewGroup) this.cacheImageView.getParent()).removeView(this.cacheImageView);
        this.cacheImageView = null;
        }
    }

    private void removeMapLoadingProgressBar() {
        if (this.mapLoadingProgressBar != null) {
        ((ViewGroup) this.mapLoadingProgressBar.getParent()).removeView(this.mapLoadingProgressBar);
        this.mapLoadingProgressBar = null;
        }
    }

    private void removeMapLoadingLayoutView() {
        this.removeMapLoadingProgressBar();
        if (this.mapLoadingLayout != null) {
        ((ViewGroup) this.mapLoadingLayout.getParent()).removeView(this.mapLoadingLayout);
        this.mapLoadingLayout = null;
        }
    }
}