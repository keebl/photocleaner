# PhotoCleaner

Een iPhone-app (SwiftUI + PhotoKit) om je fotobibliotheek op te schonen:

- **Op deze dag** — toont elke dag de foto's die op deze datum in eerdere jaren
  zijn gemaakt. Per foto kies je: behouden of weggooien.
- **Dubbelen** — vindt duplicaten op basis van metadata (opnametijdstip +
  afmetingen) en stelt voor de beste te behouden en de rest weg te gooien.
- **Prullenbak met 30 dagen retentie** — weggegooide foto's blijven eerst in je
  bibliotheek maar verdwijnen uit de opschoon-weergaven. Binnen 30 dagen kun je
  ze terugzetten; daarna (of handmatig) worden ze definitief verwijderd. Een
  definitieve verwijdering gaat via iOS nog naar "Recent verwijderd" — dubbel
  vangnet.

## Status: proof of concept (haalbaarheid)

De volledige app-code staat er. Om te bouwen en te draaien is **Xcode** nodig
(alleen de Command Line Tools volstaan niet).

### 1. Xcode installeren

Installeer Xcode via de App Store (gratis, ~7 GB). Daarna eenmalig:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

### 2. Openen en draaien

```bash
open PhotoCleaner.xcodeproj
```

Kies een simulator (bijv. iPhone 16) en druk op ▶︎ (Run).

> In de simulator is de fotobibliotheek leeg; sleep wat afbeeldingen op het
> simulatorvenster om te testen. Op een écht toestel werkt het met je eigen
> foto's.

### 3. Op je eigen iPhone zetten

In Xcode: selecteer het target **PhotoCleaner** → tab **Signing & Capabilities**
→ kies je **Team** (je gewone Apple ID werkt voor persoonlijk gebruik). Sluit je
iPhone aan, kies 'm als run-doel en druk op Run.

## Architectuur

De code is losgekoppeld van PhotoKit via een `PhotoSource`-abstractie, zodat
latere bronnen (Google Foto's, een NAS-map, ...) er zonder UI-wijzigingen bij
kunnen.

```
PhotoCleaner/
├─ App/          app-entry + navigatie
├─ Models/       PhotoAsset (bron-onafhankelijk)
├─ Sources/      PhotoSource-protocol + PhotoKitSource
├─ Services/     PhotoLibrary (toegang), DuplicateDetector, TrashStore
└─ Views/        Op deze dag, Dubbelen, Prullenbak, thumbnails
```

## Bekende beperkingen / volgende stappen

- Dubbelen worden nu op metadata gevonden. Voor "lijkt op elkaar"-duplicaten
  (bewerkt/gecomprimeerd) is een perceptual hash de logische volgende stap.
- Bestandsgrootte wordt nu voor elke foto tijdens de scan bepaald; bij zeer
  grote bibliotheken loont het dit lui te doen.
- Dagelijkse melding voor "Op deze dag" (lokale notificatie) staat op de rol.
- Bronnen Google Foto's / NAS: implementeren als extra `PhotoSource`.
