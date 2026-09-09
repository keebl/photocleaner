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

## Functies (v1)

- **Op deze dag** — swipe-stapel: één foto tegelijk van een (te kiezen) datum door
  de jaren heen. Swipe rechts = behouden, links = weggooien; met voortgang, undo en
  afrondscherm. Dagelijkse lokale notificatie in te schakelen.
- **Dubbelen** — *Exacte dubbelen* (metadata) en *Lijkende foto's* (perceptual
  hash / dHash) voor bewerkte of gecomprimeerde kopieën.
- **Prullenbak** — 30 dagen retentie, met bevestiging vóór definitief verwijderen.
- **Fotobron** — iPhone-bibliotheek actief; Google Foto's en NAS voorbereid via
  de `PhotoSource`-abstractie (zie `Sources/RemoteSources.swift`).
- **Weergave** — Systeem/Licht/Donker (standaard Donker).

## Prestaties & best practices

- Snelle bibliotheekscan (geen trage per-foto resource-calls); bestandsgrootte
  alleen voor gevonden dubbelen.
- Beeldverzoeken worden geannuleerd bij uit-beeld-scrollen (geen geheugenpieken).
- Perceptuele scan: **geen iCloud-downloads**, draait op de achtergrond met
  voortgang + stopknop, en **hashes worden op schijf bewaard** (Caches,
  gevalideerd op wijzigingsdatum) → tweede scan vrijwel instant.
- Automatisch verversen bij bibliotheekwijzigingen (`PHPhotoLibraryChangeObserver`).
- Haptische feedback, VoiceOver-labels, en caches worden bij het naar de
  achtergrond gaan weggeschreven.

## Bekende beperkingen / volgende stappen

- Google Foto's / NAS: architectuur staat klaar; de echte koppeling vereist
  externe stappen (Google-OAuth, netwerkconfig) — zie de doc-comments in
  `Sources/RemoteSources.swift`.
- Naar de App Store: zie [docs/APPSTORE.md](docs/APPSTORE.md).
