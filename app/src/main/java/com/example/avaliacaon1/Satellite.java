package com.example.avaliacaon1;

/**
 * Classe de Modelo (POJO) para armazenar os dados de um único satélite.
 * Isso ajuda a manter o código organizado e legível.
 */
public class Satellite {

    private final int svid; // Satellite Vehicle ID
    private final int constellationType;
    private final float azimuthDegrees;
    private final float elevationDegrees;
    private final boolean usedInFix;

    public Satellite(int svid, int constellationType, float azimuthDegrees, float elevationDegrees, boolean usedInFix) {
        this.svid = svid;
        this.constellationType = constellationType;
        this.azimuthDegrees = azimuthDegrees;
        this.elevationDegrees = elevationDegrees;
        this.usedInFix = usedInFix;
    }

    // Getters para acessar os dados
    public int getSvid() {
        return svid;
    }

    public int getConstellationType() {
        return constellationType;
    }

    public float getAzimuthDegrees() {
        return azimuthDegrees;
    }

    public float getElevationDegrees() {
        return elevationDegrees;
    }

    public boolean isUsedInFix() {
        return usedInFix;
    }
}
