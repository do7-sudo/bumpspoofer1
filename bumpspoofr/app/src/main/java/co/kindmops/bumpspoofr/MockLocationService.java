// MockLocationService — pushes fake GPS at 1 Hz via LocationManager.setTestProviderLocation
package co.kindmops.bumpspoofr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MockLocationService extends Service {

    private static final String CHANNEL_ID = "bumpspoofr_channel";
    private static final String PROVIDER    = LocationManager.GPS_PROVIDER;

    private LocationManager lm;
    private ScheduledExecutorService scheduler;
    private double lat, lon;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lat = intent.getDoubleExtra("lat", 0);
        lon = intent.getDoubleExtra("lon", 0);

        startForeground(1, buildNotification());

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            lm.addTestProvider(PROVIDER,
                false, false, false, false, true, true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE);
        } catch (Exception ignored) {
            // already added — fine
        }
        lm.setTestProviderEnabled(PROVIDER, true);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::pushLocation, 0, 1, TimeUnit.SECONDS);

        return START_STICKY;
    }

    private void pushLocation() {
        Location loc = new Location(PROVIDER);
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        loc.setAltitude(0);
        loc.setAccuracy(1.0f);
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        loc.setBearing(0);
        loc.setSpeed(0);
        try {
            lm.setTestProviderLocation(PROVIDER, loc);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        try {
            lm.setTestProviderEnabled(PROVIDER, false);
            lm.removeTestProvider(PROVIDER);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "BumpSpoofr", NotificationManager.IMPORTANCE_LOW));
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BumpSpoofr active")
            .setContentText(String.format("Spoofing %.4f, %.4f", lat, lon))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build();
    }
}
