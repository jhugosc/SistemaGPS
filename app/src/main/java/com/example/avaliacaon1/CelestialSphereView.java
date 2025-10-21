package com.example.avaliacaon1;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * View customizada que desenha uma projeção da esfera celeste e os satélites GNSS.
 * Esta view gerencia seu próprio estado, incluindo filtros de visualização e persistência
 * de preferências do usuário.
 * Ela também responde ao clique para mostrar um diálogo de configurações.
 */
public class CelestialSphereView extends View {

    // Constantes para SharedPreferences
    private static final String PREFS_NAME = "GnssViewPrefs";
    private static final String KEY_SHOW_UNUSED = "showUnused";
    private static final String KEY_CONSTELLATIONS = "constellations";

    // --- Pincéis e Objetos de Desenho ---
    private Paint paintSphere, paintText, paintSatellite;
    private Rect textBounds = new Rect(); // Objeto reutilizado para medir texto
    private StringBuilder infoTextBuilder = new StringBuilder(40); // Reutilizado para performance no onDraw
    private String strInfoVisible; // String "Visíveis: "
    private String strInfoInUse;   // String " / Em uso: "

    // --- Dados e Contagem ---
    private List<Satellite> satellites = new ArrayList<>();
    private int visibleSatellitesCount = 0;
    private int usedSatellitesCount = 0;

    // --- Configurações de Filtro ---
    private boolean showUnusedSatellites = true;
    private Set<Integer> visibleConstellations = new HashSet<>();

    // --- CONSTRUTORES ---
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

        // Carrega as strings de recurso para performance
        strInfoVisible = getContext().getString(R.string.info_visible);
        strInfoInUse = getContext().getString(R.string.info_in_use);

        // Carrega as configurações salvas
        loadPreferences();

        // Carrega atributos customizados do XML
        if (attrs != null) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.CelestialSphereView, 0, 0);
            try {
                // Permite que o programador defina a cor da esfera no XML
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
     * Chamar este método força a view a se redesenhar (invalidate).
     *
     * @param newSatellites A lista completa de satélites visíveis.
     * @param visibleCount O número total de satélites na lista.
     * @param usedCount O número de satélites que estão sendo usados no Fix.
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
        int radius = Math.min(centerX, centerY) - 60; // Raio da esfera

        // 1. Desenha a esfera celeste (círculos e eixos)
        canvas.drawCircle(centerX, centerY, radius, paintSphere); // Horizonte (0° elevação)
        canvas.drawCircle(centerX, centerY, radius / 2, paintSphere); // 45° elevação
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paintSphere); // Eixo Norte-Sul
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paintSphere); // Eixo Leste-Oeste

        // Desenha o indicador do Norte (fixo no topo)
        paintText.getTextBounds("N", 0, 1, textBounds);
        canvas.drawText("N", centerX - textBounds.width() / 2f, centerY - radius - 10, paintText);

        // 2. Desenha os satélites
        if (satellites != null) {
            for (Satellite sat : satellites) {
                // --- Aplicação dos Filtros ---
                // Filtro 1: Mostrar apenas satélites usados (se a opção estiver desmarcada)
                if (!showUnusedSatellites && !sat.isUsedInFix()) {
                    continue; // Pula para o próximo satélite
                }
                // Filtro 2: Mostrar apenas constelações selecionadas
                if (!visibleConstellations.contains(sat.getConstellationType())) {
                    continue; // Pula para o próximo satélite
                }

                // --- Cálculo da Posição ---
                // Converte coordenadas polares (azimute, elevação) para cartesianas (x, y)
                // elevação 90° (zênite) = centro (raio 0)
                // elevação 0° (horizonte) = borda (raio máximo)
                float r = radius * (1 - (sat.getElevationDegrees() / 90.0f));
                float azimuthRad = (float) Math.toRadians(sat.getAzimuthDegrees());

                // O Norte está no topo (-y), Leste à direita (+x)
                float x = (float) (centerX + r * Math.sin(azimuthRad));
                float y = (float) (centerY - r * Math.cos(azimuthRad));

                // --- Desenho Visual (Requisito de identificação) ---
                // Cor: representa a constelação
                paintSatellite.setColor(getColorForConstellation(sat.getConstellationType()));

                // Forma: Círculo se usado no FIX, Quadrado se não usado
                if (sat.isUsedInFix()) {
                    canvas.drawCircle(x, y, 15f, paintSatellite);
                } else {
                    canvas.drawRect(x - 12f, y - 12f, x + 12f, y + 12f, paintSatellite);
                }

                // Texto: ID (SVID) do satélite
                String idText = String.valueOf(sat.getSvid());
                paintText.getTextBounds(idText, 0, idText.length(), textBounds);
                canvas.drawText(idText, x - textBounds.width() / 2f, y + textBounds.height() / 2f, paintText);
            }
        }

        // 3. Desenha o texto com a contagem de satélites
        // (Usando StringBuilder para otimizar performance e evitar alocação de String no onDraw)
        infoTextBuilder.setLength(0); // Limpa o builder
        infoTextBuilder.append(strInfoVisible).append(visibleSatellitesCount);
        infoTextBuilder.append(strInfoInUse).append(usedSatellitesCount);

        canvas.drawText(infoTextBuilder, 0, infoTextBuilder.length(), 20, 50, paintText);
    }

    /**
     * Retorna uma cor específica para cada constelação, conforme sugerido.
     * @param constellationType O ID da constelação (ex: Satellite.CONSTELLATION_GPS)
     * @return Um valor de Cor (int).
     */
    private int getColorForConstellation(int constellationType) {
        switch (constellationType) {
            case Satellite.CONSTELLATION_GPS:
                return Color.rgb(0, 150, 255); // Azul
            case Satellite.CONSTELLATION_GLONASS:
                return Color.rgb(255, 100, 100); // Vermelho
            case Satellite.CONSTELLATION_GALILEO:
                return Color.rgb(255, 200, 0);   // Amarelo
            case Satellite.CONSTELLATION_BEIDOU:
                return Color.rgb(200, 100, 255); // Roxo
            default:
                return Color.GRAY;
        }
    }

    /**
     * Cria e exibe um diálogo de alerta (AlertDialog) com um layout customizado
     * para o usuário configurar os filtros de visualização.
     * Esta é uma funcionalidade interna e não pode ser configurada pela Activity.
     */
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getContext().getString(R.string.dialog_title));

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

        // Definir o estado inicial dos checkboxes com base nos valores atuais
        checkGps.setChecked(visibleConstellations.contains(Satellite.CONSTELLATION_GPS));
        checkGlonass.setChecked(visibleConstellations.contains(Satellite.CONSTELLATION_GLONASS));
        checkBeidou.setChecked(visibleConstellations.contains(Satellite.CONSTELLATION_BEIDOU));
        checkGalileo.setChecked(visibleConstellations.contains(Satellite.CONSTELLATION_GALILEO));
        checkShowUnused.setChecked(showUnusedSatellites);

        // Botão "OK"
        builder.setPositiveButton(getContext().getString(R.string.dialog_ok), (dialog, which) -> {
            // Limpar o Set de constelações para preencher com os novos valores
            visibleConstellations.clear();

            // Ler o estado dos checkboxes das constelações
            if (checkGps.isChecked()) visibleConstellations.add(Satellite.CONSTELLATION_GPS);
            if (checkGlonass.isChecked()) visibleConstellations.add(Satellite.CONSTELLATION_GLONASS);
            if (checkBeidou.isChecked()) visibleConstellations.add(Satellite.CONSTELLATION_BEIDOU);
            if (checkGalileo.isChecked()) visibleConstellations.add(Satellite.CONSTELLATION_GALILEO);

            // Ler o estado do checkbox de "não usados"
            showUnusedSatellites = checkShowUnused.isChecked();

            // Salva as novas configurações e redesenha a view
            savePreferences();
            invalidate();
        });

        // Botão "Cancelar"
        builder.setNegativeButton(getContext().getString(R.string.dialog_cancel), null);
        builder.create().show();
    }

    /**
     * Salva as configurações de filtro no SharedPreferences.
     */
    private void savePreferences() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(KEY_SHOW_UNUSED, showUnusedSatellites);

        // SharedPreferences não pode salvar Set<Integer>, então convertemos para Set<String>
        Set<String> stringSet = new HashSet<>();
        for (Integer type : visibleConstellations) {
            stringSet.add(String.valueOf(type));
        }
        editor.putStringSet(KEY_CONSTELLATIONS, stringSet);
        editor.apply();
    }

    /**
     * Carrega as configurações de filtro do SharedPreferences.
     * Chamado durante a inicialização (init).
     */
    private void loadPreferences() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        showUnusedSatellites = prefs.getBoolean(KEY_SHOW_UNUSED, true);

        Set<String> stringSet = prefs.getStringSet(KEY_CONSTELLATIONS, null);
        visibleConstellations.clear();
        if (stringSet == null) {
            // Se for a primeira vez, mostra todas as constelações por padrão
            visibleConstellations.add(Satellite.CONSTELLATION_GPS);
            visibleConstellations.add(Satellite.CONSTELLATION_GLONASS);
            visibleConstellations.add(Satellite.CONSTELLATION_BEIDOU);
            visibleConstellations.add(Satellite.CONSTELLATION_GALILEO);
        } else {
            // Converte o Set<String> de volta para Set<Integer>
            for (String typeStr : stringSet) {
                try {
                    visibleConstellations.add(Integer.parseInt(typeStr));
                } catch (NumberFormatException e) {
                    Log.e("CelestialSphereView", "Erro ao carregar SharedPreferences", e);
                }
            }
        }
    }
}