package com.pfe.musmed.etum;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.DirectionManeuver;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class MainActivity extends AppCompatActivity {
    String[] reqPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission
            .ACCESS_COARSE_LOCATION};
    private final String TAG = MainActivity.class.getSimpleName();
    private int requestCode = 2;
    MapView mMapView;
    private LocationDisplay mLocationDisplay;
    private ProgressDialog mProgressDialog;
    private RouteParameters mRouteParams;
    private Point mSourcePoint;
    private Point mDestinationPoint;
    private Route mRoute;
    private SimpleLineSymbol mRouteSymbol;
    private GraphicsOverlay mGraphicsOverlay;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    @Override
    protected void onPause(){
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.directions_drawer);

        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 35.931488, 0.092265, 16);
        mMapView.setMap(map);

        boolean permissionCheck1 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[0]) == PackageManager.PERMISSION_GRANTED;
        boolean permissionCheck2 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[1]) == PackageManager.PERMISSION_GRANTED;

        if (!(permissionCheck1 && permissionCheck2)) {
            // If permissions are not already granted, request permission from the user.
            ActivityCompat.requestPermissions(MainActivity.this, reqPermissions, requestCode);
        }

        // get the MapView's LocationDisplay
        mLocationDisplay = mMapView.getLocationDisplay();
        loadGeodatabase();

        // Listen to changes in the status of the location data source.
        mLocationDisplay.addDataSourceStatusChangedListener(new LocationDisplay.DataSourceStatusChangedListener() {
            @Override
            public void onStatusChanged(LocationDisplay.DataSourceStatusChangedEvent dataSourceStatusChangedEvent) {

                // If LocationDisplay started OK, then continue.
                if (dataSourceStatusChangedEvent.isStarted())
                    return;

                // No error is reported, then continue.
                if (dataSourceStatusChangedEvent.getError() == null)
                    return;

                // If an error is found, handle the failure to start.
                // Check permissions to see if failure may be due to lack of permissions.
                boolean permissionCheck1 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[0]) ==
                        PackageManager.PERMISSION_GRANTED;
                boolean permissionCheck2 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[1]) ==
                        PackageManager.PERMISSION_GRANTED;

                if (!(permissionCheck1 && permissionCheck2)) {
                    // If permissions are not already granted, request permission from the user.
                    ActivityCompat.requestPermissions(MainActivity.this, reqPermissions, requestCode);
                } else {
                    // Report other unknown failure types to the user - for example, location services may not
                    // be enabled on the device.
                    String message = String.format("Error in DataSourceStatusChangedListener: %s", dataSourceStatusChangedEvent
                            .getSource().getLocationDataSource().getError().getMessage());
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();

                    mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                    if (!mLocationDisplay.isStarted())
                        mLocationDisplay.startAsync();
                }
            }
        });
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        if (!mLocationDisplay.isStarted())
            mLocationDisplay.startAsync();


        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        FloatingActionButton mDirectionFab = (FloatingActionButton) findViewById(R.id.directionFAB);

        // update UI when attribution view changes
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mDirectionFab.getLayoutParams();
        mMapView.addAttributionViewLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(
                    View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int heightDelta = (bottom - oldBottom);
                params.bottomMargin += heightDelta;
            }
        });

        setupDrawer();
        setupSymbols();

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getString(R.string.progress_title));
        mProgressDialog.setMessage(getString(R.string.progress_message));

        mDirectionFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mProgressDialog.show();

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setHomeButtonEnabled(true);
                    setTitle(getString(R.string.app_name));
                }
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

                // create RouteTask instance
                RouteTask mRouteTask = new RouteTask(getApplicationContext(),Environment.getExternalStorageDirectory() + getString(R.string.config_data_sdcard_offline_dir)
                        + getString(R.string.config_geodb_name), "test_test_ND");

                final ListenableFuture<RouteParameters> listenableFuture = mRouteTask.createDefaultParametersAsync();
                listenableFuture.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (listenableFuture.isDone()) {
                                int i = 0;
                                mRouteParams = listenableFuture.get();

                                // create stops
                                Stop stop1 = new Stop(new Point(35.931488, 0.092265, SpatialReferences.getWgs84()));
                                Stop stop2 = new Stop(new Point(35.933488, 0.093265, SpatialReferences.getWgs84()));

                                List<Stop> routeStops = new ArrayList<>();
                                // add stops
                                routeStops.add(stop1);
                                routeStops.add(stop2);
                                mRouteParams.setStops(routeStops);

                                // set return directions as true to return turn-by-turn directions in the result of
                                // getDirectionManeuvers().
                                mRouteParams.setReturnDirections(true);

                                // solve
                                RouteResult result = mRouteTask.solveRouteAsync(mRouteParams).get();
                                final List routes = result.getRoutes();
                                mRoute = (Route) routes.get(0);
                                // create a mRouteSymbol graphic
                                Graphic routeGraphic = new Graphic(mRoute.getRouteGeometry(), mRouteSymbol);
                                // add mRouteSymbol graphic to the map
                                mGraphicsOverlay.getGraphics().add(routeGraphic);
                                // get directions
                                // NOTE: to get turn-by-turn directions Route Parameters should set returnDirection flag as true
                                final List<DirectionManeuver> directions = mRoute.getDirectionManeuvers();

                                String[] directionsArray = new String[directions.size()];

                                for (DirectionManeuver dm : directions) {
                                    directionsArray[i++] = dm.getDirectionText();
                                }
                                Log.d(TAG, directions.get(0).getGeometry().getExtent().getXMin() + "");
                                Log.d(TAG, directions.get(0).getGeometry().getExtent().getYMin() + "");

                                // Set the adapter for the list view
                                mDrawerList.setAdapter(new ArrayAdapter<>(getApplicationContext(),
                                        R.layout.directions_drawer, directionsArray));

                                if (mProgressDialog.isShowing()) {
                                    mProgressDialog.dismiss();
                                }
                                mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                        if (mGraphicsOverlay.getGraphics().size() > 3) {
                                            mGraphicsOverlay.getGraphics().remove(mGraphicsOverlay.getGraphics().size() - 1);
                                        }
                                        mDrawerLayout.closeDrawers();
                                        DirectionManeuver dm = directions.get(position);
                                        Geometry gm = dm.getGeometry();
                                        Viewpoint vp = new Viewpoint(gm.getExtent(), 20);
                                        mMapView.setViewpointAsync(vp, 3);
                                        SimpleLineSymbol selectedRouteSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID,
                                                Color.GREEN, 5);
                                        Graphic selectedRouteGraphic = new Graphic(directions.get(position).getGeometry(),
                                                selectedRouteSymbol);
                                        mGraphicsOverlay.getGraphics().add(selectedRouteGraphic);
                                    }
                                });

                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                });
            }
        });


    }

    private void loadGeodatabase() {


        String path =
                Environment.getExternalStorageDirectory() + getString(R.string.config_data_sdcard_offline_dir)
                        + getString(R.string.config_geodb_name);

        // create a new geodatabase from local path
        final Geodatabase geodatabase = new Geodatabase(path);
        // load the geodatabase
        geodatabase.loadAsync();
        Log.e(TAG, geodatabase.getLoadStatus()+"....................................................................................");
        // create feature layer from geodatabase and add to the map
        geodatabase.addDoneLoadingListener(() -> {
            if (geodatabase.getLoadStatus() == LoadStatus.LOADED) {
                // access the geodatabase's feature table Trailheads
                GeodatabaseFeatureTable geodatabaseFeatureTable = geodatabase.getGeodatabaseFeatureTable("");
                geodatabaseFeatureTable.loadAsync();
                // create a layer from the geodatabase feature table and add to map
                final FeatureLayer featureLayer = new FeatureLayer(geodatabaseFeatureTable);
                featureLayer.addDoneLoadingListener(() -> {
                    if (featureLayer.getLoadStatus() == LoadStatus.LOADED) {
                        // set viewpoint to the feature layer's extent
                        mMapView.setViewpointAsync(new Viewpoint(featureLayer.getFullExtent()));
                    } else {
                        Toast.makeText(MainActivity.this, "Feature Layer failed to load!", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Feature Layer failed to load!");
                    }
                });
                // add feature layer to the map
                mMapView.getMap().getOperationalLayers().add(featureLayer);
            } else {
                Toast.makeText(MainActivity.this, "Geodatabase failed to load!", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Geodatabase failed to load!");
            }
        });
    }



    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }


    private void setupSymbols() {

        mGraphicsOverlay = new GraphicsOverlay();

        //add the overlay to the map view
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);

        //[DocRef: Name=Picture Marker Symbol Drawable-android, Category=Fundamentals, Topic=Symbols and Renderers]
        //Create a picture marker symbol from an app resource
        BitmapDrawable startDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.ic_source);
        final PictureMarkerSymbol pinSourceSymbol;
        try {
            pinSourceSymbol = PictureMarkerSymbol.createAsync(startDrawable).get();
            pinSourceSymbol.loadAsync();
            pinSourceSymbol.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    //add a new graphic as start point
                    mSourcePoint = new Point(-117.15083257944445, 32.741123367963446, SpatialReferences.getWgs84());
                    Graphic pinSourceGraphic = new Graphic(mSourcePoint, pinSourceSymbol);
                    mGraphicsOverlay.getGraphics().add(pinSourceGraphic);
                }
            });
            pinSourceSymbol.setOffsetY(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        //[DocRef: END]
        BitmapDrawable endDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.ic_destination);
        final PictureMarkerSymbol pinDestinationSymbol;
        try {
            pinDestinationSymbol = PictureMarkerSymbol.createAsync(endDrawable).get();
            pinDestinationSymbol.loadAsync();
            pinDestinationSymbol.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    //add a new graphic as end point
                    mDestinationPoint = new Point(-117.15557279683529, 32.703360305883045, SpatialReferences.getWgs84());
                    Graphic destinationGraphic = new Graphic(mDestinationPoint, pinDestinationSymbol);
                    mGraphicsOverlay.getGraphics().add(destinationGraphic);
                }
            });
            pinDestinationSymbol.setOffsetY(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        //[DocRef: END]
        mRouteSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5);
    }
}