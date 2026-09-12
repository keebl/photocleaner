# PhotoCleaner naar de App Store

Stapsgewijs. De meeste stappen kun alleen jíj doen (ze hangen aan jouw Apple-account
en vereisen je wachtwoord/2FA); ik kan de code, iconen, teksten en build-instellingen
voorbereiden.

## Security, performance & hardening — status

**Al gedaan (in de code):**
- **Privacy-manifest** (`PrivacyInfo.xcprivacy`): geen tracking, geen dataverzameling;
  required-reason API's gedeclareerd (UserDefaults, bestandstijden). Verplicht sinds 2024.
- **Export-compliance**: `ITSAppUsesNonExemptEncryption = NO` — geen encryptie-vraag bij upload.
- **Foto-toegang** met duidelijke uitleg (`NSPhotoLibraryUsageDescription`).
- **Lokaal & sandboxed**: geen netwerk, accounts of analytics. NAS-toegang via
  security-scoped bookmark (verloopt-bookmark wordt automatisch ververst).
- **Robuustheid**: thread-safe caches (lock), annuleerbare beeldverzoeken,
  achtergrond-scan met voortgang + stopknop, thumbnail-retry bij hapering,
  geheugen-cache met limiet, geen iCloud-downloads tijdens scannen, foutmelding
  bij mislukt verwijderen, 30-dagen prullenbak vóór definitief wissen.
- **App-icoon** (1024px, geen alpha) en launch screen aanwezig.

**Wat jij nog moet doen (account/store):**
- Apple Developer Program (US$ 99/jr) + App Store Connect-app aanmaken.
- **Privacybeleid-URL** (verplicht bij foto-toegang) — tekst kan ik leveren, jij host 'm.
- In App Store Connect het **privacy-label** op "Data Not Collected" zetten (klopt met het manifest).
- **Screenshots** (6.7"/6.9") — de simulator-shots zijn een basis.
- Bundle ID naar je eigen domein (bijv. `nl.guusvanzeijl.photocleaner`) — kan ik omzetten.

**Mogelijke volgende optimalisaties (optioneel):**
- Perceptuele hashes al berekenen tijdens rustige momenten (nu bij eerste "Lijkend"-scan).
- Video-duplicaten via een echte video-vingerafdruk (nu alleen exacte bestanden).

## 0. Wat je nodig hebt

- **Apple Developer Program-lidmaatschap**: **US$ 99 / jaar**.
  → https://developer.apple.com/programs/ (inschrijven met je Apple ID; betaling + verificatie).
- Xcode (heb je al) en een Mac (heb je al).

## 1. App-identiteit vastleggen

- **Bundle ID**: nu `com.guus.photocleaner`. Voor de store gebruik je liefst je
  eigen domein-omgekeerd, bijv. `nl.guusvanzeijl.photocleaner`. (Kan ik aanpassen.)
- **App-naam** in de store: "PhotoCleaner" moet uniek zijn — check beschikbaarheid;
  anders een variant (bijv. "PhotoCleaner: Opschonen").

## 2. App Store Connect

1. Ga naar https://appstoreconnect.apple.com → **Apps** → **+** → **New App**.
2. Kies platform iOS, taal Nederlands, je bundle ID, en een SKU (vrij te kiezen, bijv. `photocleaner-001`).

## 3. Verplichte materialen (kan ik grotendeels maken)

- **App-icoon** 1024×1024 (nu een placeholder — ik kan een echt icoon ontwerpen).
- **Screenshots** per schermformaat (6.7"/6.9" iPhone verplicht). De simulator-shots
  die we maakten zijn een prima basis.
- **Beschrijving, subtitel, trefwoorden, categorie** (Foto & video). Kan ik schrijven.
- **Privacybeleid-URL** — **verplicht** omdat de app foto's gebruikt. Er moet een
  webpagina zijn met je privacybeleid. Kernpunt is makkelijk: *alles blijft lokaal
  op het toestel; er worden geen foto's of data geüpload.* (Ik kan de tekst leveren;
  jij zet 'm op een URL — bijv. GitHub Pages of je eigen site.)
- **Privacy "nutrition label"** in App Store Connect: voor deze app kun je
  "Data Not Collected" aangeven (we versturen niets).

## 4. Ondertekenen & uploaden (in Xcode)

1. Target **PhotoCleaner** → **Signing & Capabilities** → vink *Automatically manage
   signing* en kies je **Team** (verschijnt na inschrijving in het Developer Program).
2. Zet een echt versienummer (1.0) en build (1).
3. Kies als run-doel **Any iOS Device (arm64)**.
4. Menu **Product → Archive**. Als de archive klaar is opent de **Organizer**.
5. **Distribute App → App Store Connect → Upload**.

## 5. TestFlight (aanrader vóór publicatie)

- Na de upload verschijnt de build onder **TestFlight**. Installeer 'm zo op je
  eigen iPhone (en eventueel testers) en probeer alles met je échte foto's.
- Dit is ook de snelste manier om de app op je toestel te krijgen zónder volledige
  store-review.

## 6. Indienen voor review

1. In App Store Connect: koppel de build aan de app-versie, vul alle velden + screenshots in.
2. **Submit for Review**. Beoordeling duurt meestal 1–3 dagen.
3. Aandachtspunten voor deze app bij review:
   - Duidelijke reden voor foto-toegang (staat in `NSPhotoLibraryUsageDescription`). ✅
   - Verwijderen gebeurt door de gebruiker en via de systeembevestiging. ✅
   - Werkende functies, geen crashes, privacybeleid-URL bereikbaar.

## Snelste route naar "op mijn iPhone" (zonder store)

Wil je 'm nu al op je eigen toestel zonder dit hele traject? Dan hoeft de store
niet: sluit je iPhone aan, kies je Apple ID als Team in Xcode en druk op Run
(een gratis Apple ID mag apps op je eigen toestel zetten; ze verlopen dan na
7 dagen en moeten opnieuw geïnstalleerd worden — met het betaalde Developer
Program niet).
