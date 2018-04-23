package com.pfe.musmed.etum;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;

import com.esri.arcgisruntime.mapping.view.MapView;


public class MainActivity extends AppCompatActivity {


    private MapView mMapView;

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
        setContentView(R.layout.activity_main);

        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 35.931488, 0.092265, 16);
        mMapView.setMap(map);
    }
}

