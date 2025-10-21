package com.example.avaliacaon1;

/**
 * Classe de Modelo (POJO) para armazenar os dados de um único satélite.
 * Esta classe é imutável e ajuda a manter o código organizado.
 */
public class Satellite {

    /**
     * Constantes para tipos de constelação, baseadas nos valores de GnssStatus.
     */
    public static final int CONSTELLATION_UNKNOWN = 0;
    public static final int CONSTELLATION_GPS = 1;
    public static final int CONSTELLATION_GLONASS = 3;
    public static final int CONSTELLATION_BEIDOU = 5;
    public static final int CONSTELLATION_GALILEO = 6;


    private final int svid; // Satellite Vehicle ID
    private final int constellationType;
    private final float azimuthDegrees;
    private final float elevationDegrees;
    private final boolean usedInFix;

    /**
     * Constrói um novo objeto Satellite.
     *
     * @param svid Identificador (SVID) do satélite.
     * @param constellationType O tipo da constelação (ex: CONSTELLATION_GPS).
     * @param azimuthDegrees Azimute em graus (0-360).
     * @param elevationDegrees Elevação em graus (0-90).
     * @param usedInFix Verdadeiro se o satélite está sendo usado no cálculo do Fix.
     */
    public Satellite(int svid, int constellationType, float azimuthDegrees, float elevationDegrees, boolean usedInFix) {
        this.svid = svid;
        this.constellationType = constellationType;
        this.azimuthDegrees = azimuthDegrees;
        this.elevationDegrees = elevationDegrees;
        this.usedInFix = usedInFix;
    }

    // --- Getters ---

    /**
     * @return O SVID (Identificador) do satélite.
     */
    public int getSvid() {
        return svid;
    }

    /**
     * @return O tipo da constelação (ex: {@link Satellite#CONSTELLATION_GPS}).
     */
    public int getConstellationType() {
        return constellationType;
    }

    /**
     * @return O Azimute do satélite em graus (0-360), onde 0 é o Norte.
     */
    public float getAzimuthDegrees() {
        return azimuthDegrees;
    }

    /**
     * @return A Elevação do satélite em graus (0-90), onde 90 é o Zênite.
     */
    public float getElevationDegrees() {
        return elevationDegrees;
    }

    /**
     * @return true se o satélite foi usado no cálculo do último Fix, false caso contrário.
     */
    public boolean isUsedInFix() {
        return usedInFix;
    }
}