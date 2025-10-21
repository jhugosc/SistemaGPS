package com.example.avaliacaon1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler; // <<< IMPORTAÇÃO NECESSÁRIA
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "GNSS_DEBUG";

    private LocationManager locationManager;
    private GnssStatus.Callback gnssStatusCallback;
    private CelestialSphereView celestialSphereView;
    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        celestialSphereView = findViewById(R.id.celestialSphereView);
        Log.d(TAG, "Activity criada. Solicitando permissão...");
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissão já concedida. Iniciando updates.");
            startGnssUpdates();
        } else {
            Log.d(TAG, "Permissão ainda não concedida. Solicitando ao usuário.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissão foi concedida pelo usuário.");
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "É uma permissão PRECISA. Iniciando updates.");
                    startGnssUpdates();
                } else {
                    Log.w(TAG, "Permissão APROXIMADA concedida. App não pode funcionar.");
                    Toast.makeText(this, "A localização Precisa é necessária.", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "Permissão NEGADA pelo usuário.");
                Toast.makeText(this, "Permissão de localização negada.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startGnssUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                super.onSatelliteStatusChanged(status);
                Log.d(TAG, "onSatelliteStatusChanged FOI CHAMADO! Satélites encontrados: " + status.getSatelliteCount());
                int satelliteCount = status.getSatelliteCount();
                int satellitesUsedInFix = 0;
                List<Satellite> visibleSatellites = new ArrayList<>();
                for (int i = 0; i < satelliteCount; i++) {
                    Satellite sat = new Satellite(status.getSvid(i), status.getConstellationType(i), status.getAzimuthDegrees(i), status.getElevationDegrees(i), status.usedInFix(i));
                    if (sat.isUsedInFix()) {
                        satellitesUsedInFix++;
                    }
                    visibleSatellites.add(sat);
                }
                final int finalSatellitesUsedInFix = satellitesUsedInFix;
                celestialSphereView.post(() -> celestialSphereView.updateSatellites(visibleSatellites, satelliteCount, finalSatellitesUsedInFix));
            }
        };

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.i(TAG, "NOVA LOCALIZAÇÃO RECEBIDA: Lat " + location.getLatitude() + ", Lon " + location.getLongitude());
            }
            @Override
            public void onProviderEnabled(@NonNull String provider) {}
            @Override
            public void onProviderDisabled(@NonNull String provider) {}
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // AQUI ESTÁ A CORREÇÃO: Usamos um Handler para especificar a thread.
            locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
            Log.i(TAG, "Monitoramento de status de satélites registrado.");

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, locationListener, Looper.getMainLooper());
            Log.i(TAG, "Solicitação ATIVA de localização iniciada.");

            Toast.makeText(this, "Procurando sinal de satélite...", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Tentativa de registrar listeners sem permissão!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            if (gnssStatusCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
                Log.d(TAG, "Callback do GnssStatus removido.");
            }
            if (locationListener != null) {
                locationManager.removeUpdates(locationListener);
                Log.d(TAG, "Listener de localização removido.");
            }
        }
    }
}

