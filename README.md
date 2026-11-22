# MLPlayer

Lettore musicale per Android Hi-Res in **Material Design 3**, con i colori presi dallo sfondo del telefono o dalla copertina del brano. Tutta l'interfaccia è in italiano. Il motore audio, la libreria, l'equalizzatore e l'analizzatore vengono da MLPod / MLEqualizer.

## Caratteristiche

- Solo in verticale. Barra di navigazione Material 3: Libreria · Lettore · Equalizzatore · Impostazioni; nelle altre sezioni il brano in riproduzione resta sopra la barra
- **Tema** con [MLTheme](https://github.com/marco90-cyber/MLTheme): chiaro, scuro o automatico, colori dal sistema, dallo sfondo o fissi, stile degli interruttori; **colori dalla copertina** del brano (con passaggio morbido)
- **Lettore**: copertina grande, titolo, avanzamento, comandi, volume dell'app, **VU meter** (sinistro e destro, RMS con balistica VU, spia di picco; spenti di default, si attivano in Impostazioni → Aspetto) con riferimento 0 VU = −12, −14, −18, −20 dBFS o dBFS di picco; qualità della sorgente e dispositivo d'uscita in due chip che aprono il percorso del segnale; coda dei brani
- Dal lettore si aprono anche **Analizzatore** e **Uscita audio**
- **Testi** (pulsante sulla copertina): sincronizzati a parole o a righe, che scorrono con la musica (tocca una riga per saltarci). Fonti: tag del file, file `.lrc` accanto al brano, LRCLIB, Apple Music e QQ Music (tramite BetterLyrics), Kugou, Unison, Lyrics.ovh e, solo se le scegli tu, le fonti sperimentali YouTube Music (scheda Testo) e sottotitoli di YouTube (solo quelli caricati a mano); scelta automatica o manuale con "Prova un'altra fonte". Opzioni: dimensione, traduzione nella lingua del telefono, romanizzazione, ritardo di sincronizzazione per brano, modifica a mano, condivisione, salvataggio dentro il file
- **Copertina** (menu ⋮ sulla copertina): ricerca su internet (iTunes, Deezer, Cover Art Archive) o dalla galleria, scritta dentro il file, anche per tutto l'album; in ogni cartella della Libreria "Cerca le copertine" trova da sola la copertina di ogni brano (di base quelli senza) e la scrive nel suo file, in un colpo
- **Modifica tag**: titolo, artista, album, artista dell'album, genere, anno, traccia, disco (MP3, FLAC, M4A, OGG, WAV, AIFF, DSF). Per scrivere nei file Android chiede una volta di riconfermare la cartella della musica
- Libreria: Artisti, Album, Brani, Generi, Cartelle, Mescola brani, Mescola album; copertine incorporate o `cover.jpg` / `folder.jpg`
- Formati: MP3, FLAC, WAV, OGG, Opus, AAC, M4A, MKA, AMR (M4A/ALAC dipende dal telefono)
- **Equalizzatore** di [MLEqualizer](https://github.com/marco90-cyber/Mleq): Parametrico (16 filtri), AutoEQ (oltre 8800 cuffie incluse) e grafico a 3, 5, 7, 10, 15 o 31 bande nella pagina, preamp, toni come Poweramp (frequenza e Q), Analog Bass, Hi-Res Air, Clarity, Surround+ e stereo, DBFB, loudness legata al volume, ReplayGain (tag o misura R128), headroom automatico, filtro anti-DC, saturazione morbida e limiter true-peak, volume digitale (DVC, come Poweramp: i rinforzi non abbassano il suono) e dissolvenze anti-click su play e pausa; curva fissabile in alto; preset AutoEQ/Poweramp e **analizzatore** (picchi, RMS, PPM, goniometro, 1/3 d'ottava) nei colori del tema
- Uscita adattiva al DAC (bit e kHz) con ricampionatore soxr
- **Uscite che saltano il mixer di Android** (da LastWave): **USB esclusiva** con driver diretto al DAC (volume hardware del DAC se c'è), **uscita diretta** (AudioTrack 24 bit alla frequenza del brano su un'uscita DIRECT verificata, oppure **AAudio esclusiva MMAP** con Oboe) e **Bit-perfect** (niente DSP, niente ricampionamento; con il mixer serve Android 14 e un DAC USB compatibile)
- **Percorso del segnale**: Sorgente → DSP → Volume → Uscita → Dispositivo, con le verifiche reali (clock letto dal DAC, uscita DIRECT, MMAP) e il pulsante "Controlla"
- **Decoder FFmpeg** (Jellyfin) per i formati che il telefono non decodifica (ALAC, AC-3, E-AC-3, DTS, TrueHD in M4A/MKA/WebM)
- **Aggiornamenti** (Impostazioni → Aggiornamenti): controlla l'ultima release su GitHub e scarica l'APK
- Riproduzione in background con notifica e controlli multimediali
- **Android Auto**: libreria sfogliabile (Brani, Album, Artisti, Generi) con copertine, ricerca e comandi vocali; i brani proseguono nell'album, artista o genere da cui li scegli

## Impostazioni

Aspetto (tema, colori, VU meter) · Audio (equalizzatore, riproduzione, percorso del segnale, uscita, analizzatore) · Libreria (cartelle, scansione) · Tatto · Aggiornamenti · Debug (log, esportazione, informazioni audio/libreria) · Informazioni

## Vecchia grafica Hi-Fi

Le finiture e i layout Hi-Fi, Copertina, Alluminio, Cassetta e Giradischi sono conservati in
[`archivio/skin-hifi`](archivio/skin-hifi) e nel branch `archivio-skin-hifi` (l'ultima versione completa con le skin).

## Firma dell'APK

Il workflow firma l'APK con una chiave privata presa dai segreti del repository
(Settings → Secrets and variables → Actions):

| Segreto | Contenuto |
|---|---|
| `KEYSTORE_BASE64` | il file `.jks` codificato in base64 (`base64 -w0 chiave.jks`) |
| `KEYSTORE_PASSWORD` | password del keystore |
| `KEY_ALIAS` | alias della chiave |
| `KEY_PASSWORD` | password della chiave |

Senza segreti la build si ferma: non vengono più pubblicati APK con la chiave di debug. Dopo ogni build
il workflow verifica la firma e scrive l'impronta SHA-256 del certificato nella release.
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
- **Bypass reale**: con il DSP spento l'audio non viene elaborato (PCM16 bit-exact, verificato dai test nativi).
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
