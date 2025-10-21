package com.example.avaliacaon1;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CelestialSphereView extends View {

    // Constantes para SharedPreferences
    private static final String PREFS_NAME = "GnssViewPrefs";
    private static final String KEY_SHOW_UNUSED = "showUnused";
    private static final String KEY_CONSTELLATIONS = "constellations";

    // Pincéis para desenhar. São inicializados uma vez para melhor performance.
    private Paint paintSphere, paintText, paintSatellite;
    private Rect textBounds = new Rect(); // Objeto para medir o tamanho do texto

    // Dados dos satélites e contagens
    private List<Satellite> satellites = new ArrayList<>();
    private int visibleSatellitesCount = 0;
    private int usedSatellitesCount = 0;

    // Configurações de filtro
    private boolean showUnusedSatellites = true;
    private Set<Integer> visibleConstellations = new HashSet<>();


    // --- CONSTRUTORES ---
    // São necessários para que a View possa ser criada tanto via código quanto via XML.

    public CelestialSphereView(Context context) {
        super(context);
        init(null);
    }

    public CelestialSphereView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CelestialSphereView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /**
     * Método de inicialização. Chamado pelos construtores.
     * @param attrs Atributos definidos no arquivo XML do layout.
     */
    private void init(@Nullable AttributeSet attrs) {
        // Inicializa os pincéis (Paint)
        paintSphere = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSphere.setStyle(Paint.Style.STROKE);
        paintSphere.setStrokeWidth(3f);

        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(40f);

        paintSatellite = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSatellite.setStyle(Paint.Style.FILL);

        // Carrega as configurações salvas
        loadPreferences();

        // Carrega atributos customizados do XML
        if (attrs != null) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.CelestialSphereView, 0, 0);
            try {
                int sphereColor = a.getColor(R.styleable.CelestialSphereView_sphereColor, Color.argb(100, 0, 255, 0));
                paintSphere.setColor(sphereColor);
            } finally {
                a.recycle(); // É importante reciclar o TypedArray
            }
        } else {
            paintSphere.setColor(Color.argb(100, 0, 255, 0));
        }

        // Configura o listener de clique para abrir o diálogo de configurações
        setOnClickListener(v -> showSettingsDialog());
    }

    /**
     * Método público para a MainActivity enviar os dados atualizados dos satélites.
     */
    public void updateSatellites(List<Satellite> newSatellites, int visibleCount, int usedCount) {
        this.satellites = newSatellites;
        this.visibleSatellitesCount = visibleCount;
        this.usedSatellitesCount = usedCount;
        invalidate(); // Força a View a se redesenhar com os novos dados
    }

    /**
     * O coração da View. Aqui é onde todo o desenho acontece.
     * @param canvas O "quadro" onde podemos desenhar.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Pega as dimensões da View
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // O raio da esfera será o menor entre a metade da altura e da largura, com uma margem.
        int radius = Math.min(centerX, centerY) - 60;

        // 1. Desenha a esfera celeste (círculos e eixos)
        canvas.drawCircle(centerX, centerY, radius, paintSphere); // Horizonte (0° elevação)
        canvas.drawCircle(centerX, centerY, radius / 2, paintSphere); // 45° elevação
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paintSphere); // Eixo Norte-Sul
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paintSphere); // Eixo Leste-Oeste

        // Desenha o indicador do Norte
        paintText.getTextBounds("N", 0, 1, textBounds);
        canvas.drawText("N", centerX - textBounds.width() / 2f, centerY - radius - 10, paintText);

        // 2. Desenha os satélites
        if (satellites != null) {
            for (Satellite sat : satellites) {
                // Aplica os filtros antes de desenhar
                if (!showUnusedSatellites && !sat.isUsedInFix()) {
                    continue; // Pula para o próximo satélite
                }
                if (!visibleConstellations.contains(sat.getConstellationType())) {
                    continue; // Pula para o próximo satélite
                }

                // Converte coordenadas polares (azimute, elevação) para cartesianas (x, y)
                // O raio da posição do satélite é proporcional à elevação:
                // elevação 90° (zênite) = centro do círculo (raio 0)
                // elevação 0° (horizonte) = borda do círculo (raio máximo)
                float r = radius * (1 - (sat.getElevationDegrees() / 90.0f));
                float azimuthRad = (float) Math.toRadians(sat.getAzimuthDegrees());

                // O Norte está no topo (-y), Leste à direita (+x)
                float x = (float) (centerX + r * Math.sin(azimuthRad));
                float y = (float) (centerY - r * Math.cos(azimuthRad));

                // Define a aparência baseada nos dados do satélite
                paintSatellite.setColor(getColorForConstellation(sat.getConstellationType()));

                // FORMA: Círculo se usado no FIX, Quadrado se não usado
                if (sat.isUsedInFix()) {
                    canvas.drawCircle(x, y, 15f, paintSatellite);
                } else {
                    canvas.drawRect(x - 12f, y - 12f, x + 12f, y + 12f, paintSatellite);
                }

                // TEXTO: ID (SVID) do satélite
                String idText = String.valueOf(sat.getSvid());
                paintText.getTextBounds(idText, 0, idText.length(), textBounds);
                canvas.drawText(idText, x - textBounds.width() / 2f, y + textBounds.height() / 2f, paintText);
            }
        }

        // 3. Desenha o texto com a contagem de satélites
        String infoText = "Visíveis: " + visibleSatellitesCount + " / Em uso: " + usedSatellitesCount;
        canvas.drawText(infoText, 20, 50, paintText);
    }

    /**
     * Retorna uma cor específica para cada constelação.
     */
    private int getColorForConstellation(int constellationType) {
        switch (constellationType) {
            case 1: // CONSTELLATION_GPS
                return Color.rgb(0, 150, 255); // Azul
            case 3: // CONSTELLATION_GLONASS
                return Color.rgb(255, 100, 100); // Vermelho
            case 6: // CONSTELLATION_GALILEO
                return Color.rgb(255, 200, 0);   // Amarelo
            case 5: // CONSTELLATION_BEIDOU
                return Color.rgb(200, 100, 255); // Roxo
            default:
                return Color.GRAY;
        }
    }

    /**
     * Cria e exibe um diálogo de alerta para o usuário configurar os filtros.
     * (Versão corrigida usando um Layout Customizado)
     */
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Configurar Visualização");

        // Inflar o layout customizado (R.layout.dialog_settings)
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);

        // Pegar referências para os CheckBoxes do layout
        final CheckBox checkGps = dialogView.findViewById(R.id.checkGps);
        final CheckBox checkGlonass = dialogView.findViewById(R.id.checkGlonass);
        final CheckBox checkBeidou = dialogView.findViewById(R.id.checkBeidou);
        final CheckBox checkGalileo = dialogView.findViewById(R.id.checkGalileo);
        final CheckBox checkShowUnused = dialogView.findViewById(R.id.checkShowUnused);

        // Constantes dos IDs das constelações
        final int ID_GPS = 1;
        final int ID_GLONASS = 3;
        final int ID_BEIDOU = 5;
        final int ID_GALILEO = 6;

        // Definir o estado inicial dos checkboxes com base nos valores atuais
        checkGps.setChecked(visibleConstellations.contains(ID_GPS));
        checkGlonass.setChecked(visibleConstellations.contains(ID_GLONASS));
        checkBeidou.setChecked(visibleConstellations.contains(ID_BEIDOU));
        checkGalileo.setChecked(visibleConstellations.contains(ID_GALILEO));
        checkShowUnused.setChecked(showUnusedSatellites);

        // Botão "OK"
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Limpar o Set de constelações para preencher com os novos valores
            visibleConstellations.clear();

            // Ler o estado dos checkboxes das constelações
            if (checkGps.isChecked()) {
                visibleConstellations.add(ID_GPS);
            }
            if (checkGlonass.isChecked()) {
                visibleConstellations.add(ID_GLONASS);
            }
            if (checkBeidou.isChecked()) {
                visibleConstellations.add(ID_BEIDOU);
            }
            if (checkGalileo.isChecked()) {
                visibleConstellations.add(ID_GALILEO);
            }

            // Ler o estado do checkbox de "não usados"
            showUnusedSatellites = checkShowUnused.isChecked();

            // Salvar as novas configurações e redesenhar a view
            savePreferences();
            invalidate();
        });

        builder.setNegativeButton("Cancelar", null);

        builder.create().show();
    }

    /**
     * Salva as configurações de filtro no SharedPreferences.
     */
    private void savePreferences() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(KEY_SHOW_UNUSED, showUnusedSatellites);
        Set<String> stringSet = new HashSet<>();
        for (Integer type : visibleConstellations) {
            stringSet.add(String.valueOf(type));
        }
        editor.putStringSet(KEY_CONSTELLATIONS, stringSet);
        editor.apply();
    }

    /**
     * Carrega as configurações de filtro do SharedPreferences.
     */
    private void loadPreferences() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        showUnusedSatellites = prefs.getBoolean(KEY_SHOW_UNUSED, true);

        Set<String> stringSet = prefs.getStringSet(KEY_CONSTELLATIONS, null);
        visibleConstellations.clear();
        if (stringSet == null) {
            // Se for a primeira vez, mostra todas as constelações por padrão
            visibleConstellations.add(1); // GPS
            visibleConstellations.add(3); // GLONASS
            visibleConstellations.add(5); // BEIDOU
            visibleConstellations.add(6); // GALILEO
        } else {
            for (String typeStr : stringSet) {
                visibleConstellations.add(Integer.parseInt(typeStr));
            }
        }
    }
}