# PhotoCleaner — Privacy Policy / Privacybeleid

Last updated / Laatst bijgewerkt: 2026-11-14

[English](#english) · [Nederlands](#nederlands)

---

## English

PhotoCleaner helps you clean up the photos and videos on your own phone, and optionally in a
folder on your own NAS. It is built so that your photos and your choices never leave your
device, except for the traffic between your phone and the NAS you link yourself.

### What the app accesses
- **Your photos and videos** (with your permission). The app reads them to show them for
  review, to find duplicates and similar photos, and to show "on this day" memories. It uses
  the pixels for thumbnails and comparison hashes, and metadata such as date, size,
  dimensions, file name, folder and the favourite flag.
- **Notifications** (only if you turn on the daily reminder).
- **Your NAS** (only if you link one): the app connects over your own network (SMB) to the
  server, share and folder you choose, with the username and password you enter. It reads the
  photos and videos in that folder the same way as above, and deletes a file there only after
  you confirm (see below).

### What the app stores, and where
Only on your device, in the app's private storage:
- the list of items you discarded (the in-app trash) and the items you chose to keep;
- comparison hashes of photos, so a second duplicate scan is fast;
- your settings (theme, reminder time);
- if you link a NAS: its address, share, folder and username, your password (encrypted with
  a key in Android's keystore), the folder listing, previews and temporary copies of items
  you share or play.

These files are excluded from cloud backup and device transfer. Uninstalling the app deletes
them.

### What the app does not do
- It only uses the network to reach a NAS **on your own network** (home Wi-Fi or your VPN),
  and only after you link one. Any other address is refused. It never contacts a server of
  the developer or anyone else, so it cannot send your photos or other data anywhere else.
- No accounts, no analytics, no advertising, no tracking, no third-party services.
- It never deletes a photo without your action: discarded items stay in the in-app trash for
  30 days, and are then moved to Android's own trash only after you confirm Android's
  dialog. Android keeps them there for another 30 days before removing them. A NAS has no
  such trash: items from your NAS are deleted there for good only after the 30 days (or
  when you empty the trash) and after you confirm that it cannot be undone.

### Data sharing
None. PhotoCleaner does not collect, sell or share personal data. If you use the share
button, Android's share sheet sends that one photo or video to the app you pick.

### Children
The app is not directed at children and collects no data from anyone.

### Changes and contact
Changes to this policy are published at this address, with a new date at the top.
Questions: open an issue at https://github.com/keebl/photocleaner/issues or use the
contact address on the app's Google Play page.

---

## Nederlands

PhotoCleaner helpt je de foto's en video's op je eigen telefoon op te ruimen, en desgewenst in
een map op je eigen NAS. De app is zo gebouwd dat je foto's en je keuzes je toestel nooit
verlaten, behalve het verkeer tussen je telefoon en de NAS die je zelf koppelt.

### Waar de app bij kan
- **Je foto's en video's** (met je toestemming). De app leest ze om ze te tonen, om dubbele
  en lijkende foto's te vinden en om "op deze dag"-herinneringen te laten zien. Daarvoor
  gebruikt ze de beelden (voor miniaturen en vergelijkings-hashes) en metadata zoals datum,
  grootte, afmetingen, bestandsnaam, map en de favoriet-markering.
- **Meldingen** (alleen als je de dagelijkse herinnering aanzet).
- **Je NAS** (alleen als je er een koppelt): de app maakt via je eigen netwerk (SMB)
  verbinding met de server, share en map die jij kiest, met de gebruikersnaam en het
  wachtwoord die je invult. Ze leest de foto's en video's in die map zoals hierboven, en
  verwijdert daar alleen een bestand nadat je dat bevestigt (zie hieronder).

### Wat de app bewaart, en waar
Alleen op je toestel, in de afgeschermde opslag van de app:
- de lijst met items die je hebt weggegooid (de prullenbak in de app) en de items die je
  wilt behouden;
- vergelijkings-hashes van foto's, zodat een tweede dubbelen-scan snel is;
- je instellingen (thema, tijdstip van de herinnering);
- als je een NAS koppelt: het adres, de share, map en gebruikersnaam, je wachtwoord
  (versleuteld met een sleutel in de Android-sleutelopslag), de mappenlijst, previews en
  tijdelijke kopieën van items die je deelt of afspeelt.

Deze bestanden worden niet meegenomen in een cloud-back-up of bij overzetten naar een nieuw
toestel. Als je de app verwijdert, verdwijnen ze.

### Wat de app niet doet
- Ze gebruikt het netwerk alleen om een NAS **op je eigen netwerk** (thuis-wifi of je VPN)
  te bereiken, en pas nadat je er een koppelt. Elk ander adres wordt geweigerd. Ze maakt
  nooit verbinding met een server van de ontwikkelaar of van iemand anders, dus ze kan je
  foto's of andere gegevens nergens anders naartoe sturen.
- Geen accounts, geen analytics, geen advertenties, geen tracking, geen diensten van derden.
- Ze verwijdert nooit een foto zonder jouw actie: weggegooide items blijven 30 dagen in de
  prullenbak van de app en gaan daarna pas naar de prullenbak van Android nadat je de
  Android-dialoog hebt bevestigd. Android bewaart ze daar nog 30 dagen voordat ze echt
  verdwijnen. Een NAS heeft zo'n prullenbak niet: items van je NAS worden daar pas na de 30
  dagen (of als je de prullenbak leegt) definitief verwijderd, en alleen nadat je bevestigt
  dat het niet ongedaan kan worden gemaakt.

### Gegevens delen
Geen. PhotoCleaner verzamelt, verkoopt of deelt geen persoonsgegevens. Gebruik je de
deelknop, dan stuurt het deelmenu van Android die ene foto of video naar de app die jij kiest.

### Kinderen
De app is niet op kinderen gericht en verzamelt van niemand gegevens.

### Wijzigingen en contact
Wijzigingen in dit beleid worden op dit adres gepubliceerd, met een nieuwe datum bovenaan.
Vragen: maak een issue aan op https://github.com/keebl/photocleaner/issues of gebruik het
contactadres op de Google Play-pagina van de app.
