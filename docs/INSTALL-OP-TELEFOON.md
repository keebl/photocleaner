# PhotoCleaner op een iPhone zetten via Xcode (gratis route)

Voor het toestel van je vrouw. Duurt ~10 minuten. Werkt met een **gratis Apple ID**
— nadeel: de app stopt na **7 dagen** en moet dan opnieuw via Run geïnstalleerd
worden (met het betaalde Developer Program niet). Haar iPhone moet **iOS 17 of
nieuwer** hebben.

## Eenmalig: jouw Apple ID in Xcode

1. Xcode → menu **Xcode → Settings… → Accounts**.
2. Klik **+** → **Apple ID** → log in met je eigen Apple ID.

## Project openen en ondertekenen

3. Open het project:
   ```bash
   open ~/Claude/Projects/photo-cleaner/PhotoCleaner.xcodeproj
   ```
4. Klik links op het blauwe **PhotoCleaner**-project → target **PhotoCleaner** →
   tab **Signing & Capabilities**.
5. Vink **Automatically manage signing** aan en kies bij **Team** je Apple ID
   ("(Personal Team)").
6. Krijg je een rode fout bij **Bundle Identifier** ("not available")? Zet 'm dan
   uniek, bijv. `com.guus.photocleaner2` of `nl.guus.photocleaner`. (De fout
   verdwijnt zodra 'ie uniek is.)

## Haar iPhone aansluiten

7. Sluit haar iPhone met een **kabel** aan op je Mac.
8. Op de iPhone verschijnt **"Vertrouw deze computer?"** → **Vertrouw** + pincode.
9. **Developer Mode aanzetten** (eenmalig, iOS 16+): op de iPhone
   **Instellingen → Privacy en beveiliging → Ontwikkelaarsmodus → aan** →
   iPhone herstart. (Xcode vraagt hier ook om.)

## Installeren

10. Bovenin Xcode, naast de ▶︎-knop, klik op het apparaatlijstje en kies **haar
    iPhone** (onder "iOS Device", niet een simulator).
11. Druk op **▶︎ (Run)** of `Cmd+R`. Xcode bouwt en installeert de app.
12. Eerste keer op de iPhone: **"Niet-vertrouwde ontwikkelaar"**. Ga naar
    **Instellingen → Algemeen → VPN en apparaatbeheer** → tik op je Apple ID →
    **Vertrouw**.
13. Open **PhotoCleaner** op haar toestel. Bij de eerste start vraagt de app
    toegang tot foto's → **Volledige toegang geven**.

## Na 7 dagen

De app-signering verloopt. Sluit haar iPhone weer aan en druk opnieuw op **Run**
in Xcode — klaar. Wil je van dat gedoe af en 'm draadloos kunnen versturen
(TestFlight)? Dan is het **Apple Developer Program** (US$ 99/jaar) — zie
[APPSTORE.md](APPSTORE.md).

## Problemen?

- **"Could not launch … Developer Mode disabled"** → stap 9.
- **Signing-fout / bundle id** → stap 6 (maak 'm uniek).
- **Toestel verschijnt niet in de lijst** → kabel opnieuw, iPhone ontgrendeld
  houden, "Vertrouw deze computer" bevestigen.
- **iOS te oud** → deze app vereist iOS 17+ (aan te passen indien nodig).
