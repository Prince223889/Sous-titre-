# Voix Sous-titres

Application Android qui **lit à voix haute les sous-titres** des films, dans n'importe quelle application, avec **une voix différente par personnage**.

## Télécharger l'application

👉 Allez dans **[Releases](../../releases/latest)** et téléchargez `VoixSousTitres.apk` depuis votre téléphone.

Chaque modification du code fabrique automatiquement un nouvel APK (onglet **Actions**).

## Deux modes

| Mode | Comment ça marche | Marche avec |
|---|---|---|
| **Accessibilité** | Lit le texte des sous-titres exposé par l'app | VLC, certains lecteurs |
| **Capture d'écran** | Reconnaît le texte en bas de l'écran (hors ligne) | La plupart des apps, YouTube… |

Les apps protégées (Netflix, Prime Video…) bloquent la capture : l'écran est noir, donc ça ne peut pas marcher avec elles.

## Voix par personnage

- Une ligne qui commence par un tiret `-` = nouveau personnage → la voix change.
- `JEAN : Bonjour` ou `[MARIE] Salut` → chaque nom garde sa propre voix.
- Les bruits `[musique]`, `(rires)`, `♪` sont ignorés (réglable).

## Installer

1. Ouvrez l'APK téléchargé → autorisez « cette source » → **Installer**.
2. Si Play Protect avertit : **Installer quand même**.
3. Pour le mode Accessibilité sur Android 13+ : *Infos de l'app → ⋮ → Autoriser les paramètres restreints*, puis activez le service dans *Accessibilité*.
