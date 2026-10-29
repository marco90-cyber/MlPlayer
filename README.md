# MLPlayer

Lettore musicale per Android con grafica da **impianto hi-fi Hi-Res**: frontale metallico, VU meter a lancetta, display VFD e manopole vere. Tutta l'interfaccia è in italiano. Il motore audio, la libreria, l'equalizzatore e l'analizzatore vengono da MLPod / MLEqualizer.

## Caratteristiche

- Solo in verticale. Sezioni nella barra in basso: Libreria · In riproduzione · EQ · Analizzatore · Uscita · Impostazioni
- **Sei finiture** (Impostazioni → Aspetto, con anteprima): Nero e blu, Champagne, Argento, Noce, Bianco, Retrò 97 (ispirata ai lettori per PC di fine anni '90)
- Opzioni di aspetto: luce degli strumenti, velocità delle lancette VU (300 ms / 120 ms), bagliore del display, texture spazzolata, copertine nei menu, schermo sempre acceso
- **In riproduzione**: due VU meter (RMS con balistica VU, 0 VU = -18 dBFS) con spia di picco, display con copertina, brano, formato, bit/kHz e barra a segmenti (tocca o trascina per spostarti), tasti di trasporto, manopole **Bassi / Volume / Alti** che si girano col dito con tic tattile e sonoro
- Spie del frontale: HI-RES, DSP, DAC (uscita USB), BYPASS
- Libreria: Artisti, Album, Brani, Generi, Cartelle, Mescola brani, Mescola album; copertine incorporate o `cover.jpg` / `folder.jpg`
- Formati: MP3, FLAC, WAV, OGG, Opus, AAC, M4A, MKA, AMR (M4A/ALAC dipende dal telefono)
- **Equalizzatore** di MLEqualizer (3-32 bande, Param., preset Poweramp, true-peak limiter, tono caldo/freddo, sezione Stereo con bilanciamento/espansione/mono, ReplayGain brano/album con anti-clipping) e **analizzatore** (picchi, RMS, PPM, goniometro, 1/3 d'ottava) con i colori della finitura
- Uscita adattiva al DAC (bit e kHz) con ricampionatore soxr
- **Aggiornamenti** (Impostazioni → Aggiornamenti): controlla l'ultima release su GitHub e scarica l'APK
- Riproduzione in background con notifica e controlli multimediali

## Impostazioni

Aspetto · Suoni e tatto · Libreria (selezione cartelle, scansione) · Equalizzatore · Riproduzione · Aggiornamenti · Debug (log, esportazione, informazioni audio/libreria) · Informazioni

## Firma dell'APK

Il workflow firma l'APK con una chiave privata presa dai segreti del repository
(Settings → Secrets and variables → Actions):

| Segreto | Contenuto |
|---|---|
| `KEYSTORE_BASE64` | il file `.jks` codificato in base64 (`base64 -w0 chiave.jks`) |
| `KEYSTORE_PASSWORD` | password del keystore |
| `KEY_ALIAS` | alias della chiave |
| `KEY_PASSWORD` | password della chiave |

Senza segreti l'APK viene firmato con la chiave di debug (e la build lo segnala con un avviso).
Per installare gli aggiornamenti sopra la versione precedente la chiave deve restare sempre la stessa.

## Compilazione

L'APK si compila con GitHub Actions a ogni push su `main` (workflow `Build APK`) e viene
pubblicato nelle **Releases** del repository, già firmato e pronto da installare.

Build locale: `gradle :app:assembleRelease` (Gradle 8.9, JDK 17, Android SDK 35).

## Tecnologie

Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer + MediaSession).

## Motore audio (DSP nativo)

MLPlayer resta su **Media3/ExoPlayer**; tra decoder e uscita c'è un unico processore audio che chiama un motore C++17 (JNI, `app/src/main/cpp`):

- **Filtri**: cascata di biquad (formule dell'*Audio EQ Cookbook*) calcolati in Kotlin e processati in **doppia precisione**; i campioni in ingresso/uscita sono a 32 bit float (24 bit esatti).
- **Formati in ingresso**: PCM 16, 24, 32 bit e float, da 1 a 8 canali. Uscita: 16 bit se la sorgente è a 16 bit (con dither TPDF solo se il DSP è attivo), altrimenti float 32 bit.
- **Bypass reale**: con equalizzatore spento e manopole al centro l'audio non viene elaborato (PCM16 bit-exact, verificato dai test nativi).
- **Limiter look-ahead** (5 ms, soglia -0,5 dBFS, rilascio 80 ms), attivo solo quando c'è elaborazione. Il guadagno applicato è sempre minore o uguale a quello necessario per rispettare la soglia (verificato dai test). Non gestisce i picchi inter-campione (true peak): la soglia a -0,5 dBFS lascia un piccolo margine.
- **Auto-headroom** (opzionale): abbassa il preamp del massimo boost della curva.
- **Latenza**: il limiter introduce circa 5 ms; la coda viene svuotata a fine brano.

### Uscita audio e limiti

La schermata **Uscita audio** mostra solo dati realmente comunicati da Media3, dal motore DSP e da Android. L'audio passa dal mixer di Android, quindi **non è dichiarato bit-perfect**. **La modalità USB DAC diretta non è implementata** (richiederebbe un driver USB audio nativo): un DAC USB funziona solo come normale uscita Android. Il ricampionamento operato dal sistema non è verificabile dall'app.

### Licenza

MLPlayer è distribuito con licenza **GPL-3.0-or-later** (file `LICENSE`). L'algoritmo del limiter si ispira a `LimiterProcessor` di [Tryptify](https://github.com/tryptz/Tryptify) (GPL-3.0); il codice è stato riscritto (vedi `NOTICE`).

### Test

- Nativi: `g++ -std=c++17 -O2 app/src/test/cpp/dsp_selftest.cpp -o dsp_selftest && ./dsp_selftest`
- Kotlin: `gradle :app:testDebugUnitTest`

Entrambi vengono eseguiti ad ogni build su GitHub Actions. Non sono stati fatti test su hardware (DAC, cuffie, dispositivi diversi).
