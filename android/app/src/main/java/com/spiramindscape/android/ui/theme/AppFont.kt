package com.spiramindscape.android.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.spiramindscape.android.R

/**
 * The app's **body** face, switchable at runtime — the Settings page's Fonts tab (GRO-122).
 *
 * The owner is choosing Spira's sans and wants to judge each candidate **in the product**: a face
 * that looks handsome in a specimen paragraph can fall apart in a goal card, a badge and a 10sp
 * label. So the choice re-fonts the whole app at once and survives a restart.
 *
 * It swaps the body face only — headings stay on ITC Clearface, which is not what is being chosen.
 *
 * The web twin is `src/lib/spira/app-font.ts`. **Keep the two lists in step**, or the same phone
 * and the same laptop will disagree about what a candidate looks like, which defeats the comparison.
 */
enum class AppFont(
    val label: String,
    /** One line on what it is, so a name in a list means something. */
    val note: String,
    /**
     * Whether the file carries the Russian alphabet.
     *
     * GCentra does **not**, and neither does Archivo, so Cyrillic in those is drawn by the system
     * sans and the row is quietly showing two faces at once. The Fonts tab groups by this rather
     * than letting a candidate be judged against Roboto by accident.
     */
    val cyrillic: Boolean = false,
) {
    GCentra("GCentra", "The current body face — Book and Medium."),
    TildaSans("Tilda Sans", "ParaType/Tilda. Geometric grotesque, Cyrillic for 140+ languages.", cyrillic = true),
    PtRootUi("PT Root UI", "ParaType. Geometric with a humanist bend and open apertures — the closest here to GCentra.", cyrillic = true),
    Fixel("Fixel", "MacPaw. Geometric-humanist; this is the Text cut, drawn for running text.", cyrillic = true),
    Garet("Garet", "Type Forward. Geometric, tall x-height, closed oval shapes. Book and Heavy only, so its bold is heavy.", cyrillic = true),
    Bartina("Bartina", "Geometric grotesque, five weights Thin–Bold.", cyrillic = true),
    LiberationSans("Liberation Sans", "Red Hat / SIL. Neo-grotesque, metric-compatible with Arial. Not on Google Fonts.", cyrillic = true),
    DejaVuSans("DejaVu Sans", "Humanist grotesque in the Verdana mould. Not on Google Fonts.", cyrillic = true),
    Archivo("Archivo", "Grotesque for headlines and small text alike."),
    Arimo("Arimo", "Neo-grotesque, metric-compatible with Arial.", cyrillic = true),
    IbmPlexSans("IBM Plex Sans", "IBM's corporate grotesque; neutral, with a few engineered details.", cyrillic = true),
    Onest("Onest", "Contemporary geometric grotesque drawn for screens.", cyrillic = true),
    GolosText("Golos Text", "Russian-first grotesque; its Cyrillic is the point rather than an afterthought.", cyrillic = true),
    Montserrat("Montserrat", "Geometric sans inspired by old Buenos Aires signage; even, wide letterforms. Cyrillic included.", cyrillic = true),
    Futura("Futura (Jost*)", "Futura, via indestructible type's free Jost* revival. Geometric with a low waist. Cyrillic included.", cyrillic = true),

    /**
     * The real Futura rather than the Jost* revival above — ParaType's Cyrillic cut, supplied by
     * the owner (2026-08-17). Both are in the list on purpose: a revival and its original differ
     * most at exactly the small sizes this app is made of.
     */
    FuturaFuturis(
        "Futura Futuris",
        "ParaType's Cyrillic Futura — the real face, not the Jost* revival. Set a weight lighter than it ships: Light carries the body, its regular the bold.",
        cyrillic = true,
    );

    /**
     * The family this choice resolves to.
     *
     * Where a candidate ships one weight, that file is registered for Normal, Medium **and** Bold:
     * Compose trusts the declared weight of a [Font] entry, so this is what stops a bold label
     * silently falling back to the system sans (which would show the wrong face in the one place
     * the comparison matters most).
     */
    val fontFamily: FontFamily
        get() = when (this) {
            GCentra -> FontFamily(
                Font(R.font.gcentra_book, FontWeight.Normal),
                Font(R.font.gcentra_medium, FontWeight.Medium),
                Font(R.font.gcentra_medium, FontWeight.Bold),
            )
            TildaSans -> twoWeights(R.font.tilda_sans_regular, R.font.tilda_sans_medium)
            PtRootUi -> twoWeights(R.font.pt_root_ui_regular, R.font.pt_root_ui_medium)
            Fixel -> twoWeights(R.font.fixel_regular, R.font.fixel_medium)
            Garet -> twoWeights(R.font.garet_regular, R.font.garet_medium)
            Bartina -> twoWeights(R.font.bartina_regular, R.font.bartina_medium)
            LiberationSans -> twoWeights(R.font.liberation_sans_regular, R.font.liberation_sans_medium)
            DejaVuSans -> twoWeights(R.font.dejavu_sans_regular, R.font.dejavu_sans_medium)
            Archivo -> twoWeights(R.font.archivo_regular, R.font.archivo_medium)
            Arimo -> twoWeights(R.font.arimo_regular, R.font.arimo_medium)
            IbmPlexSans -> twoWeights(R.font.ibm_plex_sans_regular, R.font.ibm_plex_sans_medium)
            Onest -> twoWeights(R.font.onest_regular, R.font.onest_medium)
            GolosText -> twoWeights(R.font.golos_text_regular, R.font.golos_text_medium)
            Montserrat -> twoWeights(R.font.montserrat_regular, R.font.montserrat_medium)
            Futura -> twoWeights(R.font.jost_regular, R.font.jost_medium)
            // **Shifted down a weight** (owner, 2026-08-17: it read too heavy). `..._regular` is
            // the family's LIGHT cut and `..._medium` its regular; the real Bold is unused. The
            // file names keep their role rather than the foundry's name, so the pairing here reads
            // the same as every other candidate's.
            FuturaFuturis -> twoWeights(R.font.futura_futuris_regular, R.font.futura_futuris_medium)
        }

    /** The GCentra arrangement: the heavier file carries Medium *and* Bold, as GCentra's does. */
    private fun twoWeights(regular: Int, medium: Int) = FontFamily(
        Font(regular, FontWeight.Normal),
        Font(medium, FontWeight.Medium),
        Font(medium, FontWeight.Bold),
    )
}

/**
 * The chosen face, remembered across restarts.
 *
 * Its own preference file: this is a look-and-see setting that will be deleted once the choice is
 * made, and it has no business sharing a store with the list view state.
 */
class AppFontPreference(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** A stored name from a build that offered a face this one doesn't falls back silently. */
    var font: AppFont
        get() {
            val stored = prefs.getString(KEY_FONT, null) ?: return AppFont.GCentra
            return AppFont.entries.firstOrNull { it.name == stored } ?: AppFont.GCentra
        }
        set(value) = prefs.edit().putString(KEY_FONT, value.name).apply()

    private companion object {
        const val PREFS_NAME = "spira_app_font"
        const val KEY_FONT = "font"
    }
}

/**
 * The live choice, readable anywhere under [SpiraTheme] and settable from the Fonts tab.
 *
 * A composition local rather than a parameter because the setter is five screens away from the
 * theme that reads it, and threading it through every route would put a look-and-see setting into
 * the signature of the whole app.
 */
class AppFontState internal constructor(
    private val preference: AppFontPreference,
    private val state: MutableState<AppFont>,
) {
    var current: AppFont
        get() = state.value
        set(value) {
            state.value = value
            preference.font = value
        }
}

val LocalAppFont = staticCompositionLocalOf<AppFontState> {
    error("No AppFontState — wrap the app in SpiraTheme")
}

@Composable
fun rememberAppFontState(): AppFontState {
    val context = LocalContext.current
    return remember(context) {
        val preference = AppFontPreference(context)
        AppFontState(preference, mutableStateOf(preference.font))
    }
}
