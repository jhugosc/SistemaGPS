package com.example.avaliacaon1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity principal que gerencia as permissões de localização
 * e fornece os dados de satélite (GNSS) para a CelestialSphereView.
 */
public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "GNSS_DEBUG";

    private LocationManager locationManager;
    private GnssStatus.Callback gnssStatusCallback;
    private LocationListener locationListener;
    private CelestialSphereView celestialSphereView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        celestialSphereView = findViewById(R.id.celestialSphereView);
        Log.d(TAG, "Activity criada. Solicitando permissão...");
        requestLocationPermission();
    }

    /**
     * Verifica se a permissão ACCESS_FINE_LOCATION foi concedida.
     * Se sim, inicia as atualizações de GNSS.
     * Se não, solicita a permissão ao usuário.
     */
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissão já concedida. Iniciando updates.");
            startGnssUpdates();
        } else {
            Log.d(TAG, "Permissão ainda não concedida. Solicitando ao usuário.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Callback para o resultado da solicitação de permissão.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissão foi concedida pelo usuário.");
                // Verificação extra de segurança
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "É uma permissão PRECISA. Iniciando updates.");
                    startGnssUpdates();
                } else {
                    Log.w(TAG, "Permissão APROXIMADA concedida. App não pode funcionar.");
                    Toast.makeText(this, getString(R.string.permission_needed_precise), Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "Permissão NEGADA pelo usuário.");
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Registra os listeners para LocationManager e GnssStatus
     * após a permissão ser concedida.
     */
    private void startGnssUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Callback para status de satélites
        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                super.onSatelliteStatusChanged(status);
                Log.d(TAG, "onSatelliteStatusChanged. Satélites encontrados: " + status.getSatelliteCount());
                int satelliteCount = status.getSatelliteCount();
                int satellitesUsedInFix = 0;
                List<Satellite> visibleSatellites = new ArrayList<>();

                for (int i = 0; i < satelliteCount; i++) {
                    // Cria nosso objeto Satellite com os dados do GnssStatus
                    Satellite sat = new Satellite(
                            status.getSvid(i),
                            status.getConstellationType(i),
                            status.getAzimuthDegrees(i),
                            status.getElevationDegrees(i),
                            status.usedInFix(i)
                    );
                    if (sat.isUsedInFix()) {
                        satellitesUsedInFix++;
                    }
                    visibleSatellites.add(sat);
                }

                final int finalSatellitesUsedInFix = satellitesUsedInFix;

                // Envia os dados para a View na thread principal (UI)
                celestialSphereView.post(() -> celestialSphereView.updateSatellites(
                        visibleSatellites,
                        satelliteCount,
                        finalSatellitesUsedInFix
                ));
            }
        };

        // Listener para atualizações de localização (necessário para "acordar" o GPS)
        locationListener = location -> Log.i(TAG, "NOVA LOCALIZAÇÃO RECEBIDA: Lat " + location.getLatitude() + ", Lon " + location.getLongitude());

        // Verificação de permissão é necessária para o Android Studio não reclamar
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Registra o callback de status
            locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
            Log.i(TAG, "Monitoramento de status de satélites registrado.");

            // Solicita ativamente a localização para forçar atualizações
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, locationListener, Looper.getMainLooper());
            Log.i(TAG, "Solicitação ATIVA de localização iniciada.");

            Toast.makeText(this, getString(R.string.searching_satellites), Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Tentativa de registrar listeners sem permissão!");
        }
    }

    /**
     * Limpa os listeners quando a Activity é destruída para evitar memory leaks.
     */
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