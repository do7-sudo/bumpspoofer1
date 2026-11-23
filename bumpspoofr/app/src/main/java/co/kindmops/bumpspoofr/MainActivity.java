// BumpSpoofr — Android 8+, no root required
// Requires: Developer Options → Select mock location app → BumpSpoofr
package co.kindmops.bumpspoofr;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private Marker pin;
    private Button btnSpoof, btnStop;
    private TextView tvCoords;
    private double selectedLat = 0, selectedLon = 0;
    private boolean hasPicked = false;

    private static final int PERM_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid needs a user agent
        Configuration.getInstance().setUserAgentValue(getPackageName());
        SharedPreferences prefs = getSharedPreferences("bumpspoofr", MODE_PRIVATE);
        Configuration.getInstance().load(this, prefs);

        setContentView(R.layout.activity_main);

        map      = findViewById(R.id.map);
        btnSpoof = findViewById(R.id.btn_spoof);
        btnStop  = findViewById(R.id.btn_stop);
        tvCoords = findViewById(R.id.tv_coords);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(20.0, 0.0));

        // restore last picked location
        if (prefs.contains("lat")) {
            selectedLat = Double.longBitsToDouble(prefs.getLong("lat", 0));
            selectedLon = Double.longBitsToDouble(prefs.getLong("lon", 0));
            hasPicked = true;
            dropPin(new GeoPoint(selectedLat, selectedLon));
            updateCoordsLabel();
        }

        // tap map → place pin
        map.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(
            new org.osmdroid.events.MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    selectedLat = p.getLatitude();
                    selectedLon = p.getLongitude();
                    hasPicked = true;
                    dropPin(p);
                    updateCoordsLabel();
                    prefs.edit()
                        .putLong("lat", Double.doubleToLongBits(selectedLat))
                        .putLong("lon", Double.doubleToLongBits(selectedLon))
                        .apply();
                    return true;
                }
                @Override public boolean longPressHelper(GeoPoint p) { return false; }
            }
        ));

        btnSpoof.setOnClickListener(v -> {
            if (!hasPicked) {
                Toast.makeText(this, "Tap the map first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!checkPerms()) return;
            Intent intent = new Intent(this, MockLocationService.class);
            intent.putExtra("lat", selectedLat);
            intent.putExtra("lon", selectedLon);
            ContextCompat.startForegroundService(this, intent);
            btnSpoof.setEnabled(false);
            btnStop.setEnabled(true);
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            btnSpoof.setEnabled(true);
            btnStop.setEnabled(false);
        });

        btnStop.setEnabled(false);
        requestPerms();
    }

    private void dropPin(GeoPoint p) {
        if (pin == null) {
            pin = new Marker(map);
            map.getOverlays().add(pin);
        }
        pin.setPosition(p);
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getController().animateTo(p);
        map.invalidate();
    }

    private void updateCoordsLabel() {
        tvCoords.setText(String.format("%.5f, %.5f", selectedLat, selectedLon));
    }

    private boolean checkPerms() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        if (!checkPerms()) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                             Manifest.permission.ACCESS_COARSE_LOCATION},
                PERM_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
