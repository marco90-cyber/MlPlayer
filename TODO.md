# MLPlayer – da fare

Modifiche richieste da Marco, da fare in un secondo momento.

- [x] ~~Schermo sempre acceso dal frontale~~ (il frontale non c'è più: resta in Impostazioni → Aspetto)
- [x] **Primo avvio senza brani**: "Aggiungi cartella musicale" apre direttamente il selettore cartelle
- [x] ~~Manopole Bassi e Alti~~ (tolte dal lettore con la grafica Material 3; i toni restano nell'equalizzatore)
- [x] **Suoni dei click**: tolti del tutto, resta solo la vibrazione
- [ ] **Migliorare il motore audio DSP** (prima se ne parla insieme). Fonti da cui prendere idee, funzioni e codice (verificando le licenze):
  - https://github.com/BillyDM/awesome-audio-dsp (raccolta di librerie e risorse DSP)
  - https://github.com/mackron/miniaudio
  - **Soprattutto** il motore audio di Mornye: https://github.com/mornyeapp/Mornye/releases/tag/v5.2.6 (non l'equalizzatore: resta quello di MLEqualizer)
  - LastWave (https://github.com/Clash-Projects/LastWave-native): fatto il primo giro (uscite USB esclusiva, diretta e AAudio, bit-perfect, percorso del segnale, FFmpeg, layout Copertina; Clarity, anti-DC e saturazione morbida in MLEqualizer). Da valutare: AutoMix (dissolvenze intelligenti tra i brani)
