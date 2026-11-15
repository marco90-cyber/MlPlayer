# Archivio: grafica Hi-Fi di MlPlayer

Qui c'è la grafica di MlPlayer prima del passaggio a Material Design 3:
finiture (Nero e blu, Champagne, Argento, Noce, Bianco, Retrò 97) e layout del
lettore (Hi-Fi, Copertina, Alluminio, Cassetta, Giradischi).

Questi file **non vengono compilati** (stanno fuori da `app/src`): servono da
riferimento per recuperare una skin in futuro.

## Recuperare la versione completa e funzionante

Il branch `archivio-skin-hifi` è fermo sull'ultima versione con le skin
(commit `bdb70a3`). Da lì si compila l'app intera così com'era:

```
git checkout archivio-skin-hifi
```

## Cosa c'è

| File | Contenuto |
|---|---|
| `ui/Finish.kt` | Le 6 finiture (tutti i colori del frontale) |
| `ui/HifiKit.kt`, `ui/Knob.kt` | Frontale, viti, scritte incise, tasti e manopole in metallo |
| `ui/NowPlaying.kt` | Lettore Hi-Fi: VU a lancetta, display VFD, manopole Bassi/Volume/Alti |
| `ui/CoverPlayer.kt` | Layout Copertina (stile LastWave) |
| `ui/themes/` | Layout Alluminio, Cassetta, Giradischi |
| `ui/MenuPanel.kt` | Menu e schermata Aspetto con le anteprime delle finiture |
| `ui/HifiApp.kt` | Struttura dell'app con la barra a 6 sezioni |
| `ui/AnalyzerView.kt`, `ui/OutputScreen.kt`, ... | Le altre schermate nella versione Hi-Fi |
| `res/font/` | Caratteri Caveat e DotGothic16 (licenze in `licenses/`) |

Per riportare una skin nella versione attuale va adattata al nuovo codice:
i file qui dipendono dalle classi di allora (`Hifi`, `Finish`, `AppEnv` con 6 sezioni).
