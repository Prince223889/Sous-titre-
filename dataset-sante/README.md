# Jeu de données audio – station de surveillance santé

Format de **tous** les fichiers : WAV PCM 16 bits, **mono, 16 000 Hz, exactement 2,00 s**.
Chaque dossier garde son `LISEZ-MOI.txt` d'origine. Le fichier `metadata.csv` décrit chaque WAV :
source, fichier d'origine, groupe (patient / enregistrement d'origine), synthétique oui/non, licence,
et une colonne **`split_conseille`** (train/test).

> **Important pour l'entraînement :** utilisez `split_conseille` (ou séparez au moins par la colonne
> `groupe`). Plusieurs clips peuvent venir du même enregistrement / du même patient. Si on les mélange
> au hasard entre train et test, la précision mesurée sera trop optimiste.

## Contenu par dossier

| Dossier | Fichiers | Réels | Synthétiques | Détail |
|---|---|---|---|---|
| `alarme/` | 915 | 15 | 900 | Synthèse 900, Alarme réelle 15 |
| `bruit/` | 1000 | 1000 | 0 | ESC-50 541, SPRSound 300, MS-SNSD bruits/fonds 144, Chutes SisFall 15 |
| `choc/` | 792 | 72 | 720 | Synthèse 720, Chutes SisFall 72 |
| `cri/` | 750 | 750 | 0 | Cris NIGENS/Freesound 750 |
| `parole/` | 1000 | 1000 | 0 | MS-SNSD parole 613, MS-SNSD bruits/fonds 387 |
| `pleurs/` | 1000 | 1000 | 0 | Donate-a-cry 842, ESC-50 158 |
| `ronflement/` | 690 | 690 | 0 | Khan/Snoresense 500, ESC-50 190 |
| `sifflement/` | 1000 | 1000 | 0 | SPRSound 1000 |
| `toux/` | 1000 | 1000 | 0 | COUGHVID 944, ESC-50 56 |
| **Total** | **8147** | | | |

Les variantes (`_varN`) d'un même extrait ont un décalage temporel et des effets différents ; elles portent le même `groupe`.

## Comment c'est fabriqué

1. **Sources réelles** téléchargées depuis des dépôts publics (voir tableau des sources plus bas).
2. **Découpage** : fenêtres de 2 s centrées sur les pics d'énergie (l'événement est toujours dans le clip,
   pas de clips vides). Pour le sifflement, la fenêtre est centrée sur les sifflements **annotés par des
   médecins** (SPRSound).
3. **Réalisme « micro de la station »**, appliqué à toutes les classes avec les mêmes probabilités pour que
   l'IA n'apprenne pas de raccourci :
   - réverbération de pièce (35 % des clips réels, 60 % des synthétiques) ;
   - filtre passe-bas ~4 kHz (30 %), qui imite un micro bas de gamme et neutralise le fait que certaines
     sources (pleurs donateacry, sifflements SPRSound) sont enregistrées à 8 kHz ;
   - bruit de fond **réel** de maison (clim, machine à laver, cuisine, circulation, pluie, vent, insectes…)
     à un rapport signal/bruit de 0 à 25 dB (50 % des réels, 85 % des synthétiques) ;
   - volume aléatoire (crête entre −12 et −1 dBFS) et léger bruit de fond électronique.
4. **Équilibrage** : au plus 1000 fichiers par dossier, tirés à tour de rôle dans chaque source / sous-type.

## Sources et licences

| Source | Utilisée pour | Licence |
|---|---|---|
| [ESC-50](https://github.com/karolpiczak/ESC-50) | toux, pleurs, ronflement, bruit | CC BY-NC 3.0 (non commercial) |
| [COUGHVID via Virufy](https://github.com/virufy/virufy-cdf-coughvid) – seulement les toux avec score de détection ≥ 0,97 | toux | CC BY 4.0 |
| [Donate-a-cry corpus](https://github.com/gveres/donateacry-corpus) | pleurs | ODbL 1.0 / DbCL 1.0 |
| [Snoresense](https://github.com/Trinity1804/Snoresense) (clips du jeu « Snoring » de T. H. Khan) | ronflement | dépôt GPL-3.0 |
| [Scream_detector_dataset](https://github.com/rexj8/Scream_detector_dataset) (cris NIGENS/Freesound) | cri | non précisée – usage recherche |
| [MS-SNSD (Microsoft)](https://github.com/microsoft/MS-SNSD) | parole, bruit, bruits de fond | ODbL / CC0 / CC BY-SA 3.0 |
| [SPRSound](https://github.com/palopos/SPRSound) | sifflement | CC BY 4.0 |
| [fall_detection_audios](https://github.com/charanya78/fall_detection_audios) (vidéos SisFall) | choc (chutes réelles), bruit (activités) | non précisée – usage recherche |
| [alarmbeepdetect](https://github.com/benjaminchodroff/alarmbeepdetect) | alarme (1 enregistrement réel) | non précisée – usage recherche |
| Synthèse par script (`audiolib.py`) | alarme, choc | libre |

Si le projet devient **commercial**, retirez les fichiers `esc50` et ceux dont la licence est
« non précisée » (filtre sur la colonne `licence` du CSV).

## Limites à connaître (à lire avant d'utiliser l'IA pour la santé)

1. **Rien ne remplace le micro de la station.** Ces sons viennent de téléphones, de caméras, d'un
   stéthoscope et de studios. Enregistrez au moins 30 à 50 exemples par classe **avec la station**, dans
   de vraies maisons, et **gardez-les pour le test final**. C'est ce chiffre-là qui dit si l'IA est fiable.
2. **Sifflement (asthme) : la classe la plus fragile.** Les seuls sifflements réels, annotés par des
   médecins et en libre accès, sont enregistrés **au stéthoscope** chez des enfants (SPRSound). Un
   sifflement capté dans l'air par un micro à 1–2 m sonne différemment. Lors du premier test, l'IA
   prenait 97 % des respirations *normales* au stéthoscope pour des sifflements : elle reconnaissait le
   stéthoscope, pas le sifflement. Pour corriger cela, `bruit/` contient 300 respirations normales du
   même stéthoscope et des mêmes enfants (sous-type `respiration_normale_stethoscope`). L'IA doit donc
   apprendre le sifflement lui-même. Avant toute utilisation réelle, il faut quand même ajouter des
   enregistrements faits avec le micro de la station, près de personnes asthmatiques consentantes.
3. **Alarme : surtout de la synthèse.** Aucun jeu public de détecteurs de fumée n'était téléchargeable
   ici. L'alarme est synthétisée d'après les normes : buzzer piézo 2,9–3,7 kHz (UL 217 : 3100 ± 500 Hz),
   motif temporel T3 (ISO 8201) ou bip rapide, et 10 % de modèles 520 Hz. Le résultat a été comparé à un
   enregistrement réel (3,48 kHz, bips de 0,5 s), qui est inclus. Faites sonner le vrai détecteur
   (bouton TEST) devant la station : 20 enregistrements suffisent à valider.
4. **Choc : 12 chutes réelles (vidéos SisFall) × 6 variantes + synthèse physique** (impact grave amorti, claquement,
   vibration du sol, rebonds). Enregistrez de vrais sacs (riz, ciment) qui tombent sur votre type de sol.
5. **Bruit : il manque le pilon et la vaisselle.** Aucun enregistrement libre de pilon n'a été trouvé.
   Or le pilon fait des coups sourds rythmés, très proches d'un « choc ». **Enregistrez-le absolument**,
   sinon la station risque de déclencher des alertes « choc » pendant la préparation des repas.
6. **Parole : surtout de l'anglais lu.** Les sources sont l'anglais lu de MS-SNSD, des conversations de
   fond et des annonces, avec un effet radio/télé sur 35 % des clips. Ajoutez du français et du bambara
   (radio ORTM, télé, conversations à la maison).
7. Les sons `alarme` de type réveil (`clock_alarm`), `siren`, `glass_breaking`, `fireworks`, `thunderstorm`,
   `church_bells`, `sneezing`, `breathing` et `laughing` d'ESC-50 ont été **volontairement exclus** de
   `bruit`, car trop proches d'une classe à détecter.
8. **Aucun de ces sons ne permet un diagnostic.** La station peut servir d'**alerte** (« appeler
   quelqu'un / vérifier »), pas remplacer un soignant.

## Refaire / modifier

Les scripts `scripts/build.py`, `scripts/finalize.py`, `scripts/verify.py` et `scripts/audiolib.py`
permettent de tout régénérer (changer la durée `DUR` ou la fréquence `SR` dans `audiolib.py`).
