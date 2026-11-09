# BT Pilot – telefon jako mysz i klawiatura Bluetooth

Aplikacja na Androida, która zamienia telefon w **bezprzewodową mysz (gładzik) i klawiaturę Bluetooth**.
Drugi telefon (albo tablet, komputer, TV) widzi go jak zwykłą mysz i klawiaturę Bluetooth –
**na sterowanym urządzeniu nie trzeba nic instalować**.

## Wymagania

- Telefon sterujący: **Android 9 lub nowszy** z obsługą profilu „Bluetooth HID Device”
  (ma ją większość telefonów; niektórzy producenci ją wyłączają – aplikacja wtedy to zgłosi).
- Urządzenie sterowane: cokolwiek, co obsługuje mysz/klawiaturę Bluetooth
  (Android, iPhone/iPad, Windows, macOS, Linux, Android TV).

## Instalacja

1. Zdobądź plik `app-debug.apk` (zbuduj go – patrz niżej – albo użyj gotowego pliku).
2. Otwórz plik na telefonie sterującym i zezwól na instalację z nieznanych źródeł.

## Pierwsze połączenie

1. Uruchom **BT Pilot** na telefonie sterującym i zezwól na dostęp do Bluetooth.
2. Kliknij **Połącz → „Sparuj nowy telefon” → „Uczyń widocznym”**.
3. Na **drugim telefonie** wejdź w *Ustawienia → Bluetooth* i sparuj się z pierwszym telefonem.
   Nie zamykaj BT Pilot podczas parowania.
4. Po sparowaniu na drugim telefonie pojawi się kursor myszy.
   Jeśli nie pojawi się sam: w BT Pilot kliknij **Połącz** i wybierz drugi telefon z listy.

Później wystarczy kliknąć **Połącz** i wybrać urządzenie z listy sparowanych.

### Lista urządzeń i naprawa parowania

Drugi telefon zapamiętuje, co potrafi BT Pilot (mysz, klawiatura, pad, dotyk), **tylko w chwili
parowania**. Dlatego BT Pilot pokazuje przy każdym sparowanym telefonie:

- ✅ **gotowy** – wszystko działa,
- ⚠ **zna starą wersję** – mysz i klawiatura mogą działać, ale pad i dotyk nie,
- ❔ **nieznana wersja** – parowanie sprzed tej wersji aplikacji.

Przy ⚠ i ❔ wybierz telefon i kliknij **„Napraw parowanie”** – kreator w 3 krokach usunie stare
parowanie i sparuje telefony od nowa. To samo ostrzeżenie widać na ekranie pada i Wild Rift
(stuknij je, żeby otworzyć kreator).

## Sterowanie

| Gest na gładziku | Działanie |
|---|---|
| przesuwanie 1 palcem | ruch kursora |
| stuknięcie 1 palcem | lewy klik |
| stuknięcie 2 palcami | prawy klik |
| stuknięcie 3 palcami | środkowy klik |
| przesuwanie 2 palcami | przewijanie |
| przytrzymanie i przesunięcie | przeciąganie (lewy przycisk wciśnięty) |

- **Przyciski Lewy / Środ. / Prawy** działają jak w prawdziwej myszy – można je przytrzymać.
- **Pasek systemowy**: Wstecz, Home, przełączanie aplikacji, głośność, multimedia, jasność.
- **Pasek klawiszy**: Esc, Tab, strzałki, Enter, Backspace, Delete, Home/End, PgUp/PgDn, F1–F12.
- **Ctrl / Alt / Shift / Win** działają „na jeden klawisz”: stuknij Ctrl, potem wpisz `c` → Ctrl+C.
- **Pole „Pisz tutaj”** – wszystko, co wpiszesz klawiaturą telefonu, pojawia się na drugim urządzeniu
  (Enter wysyła Enter, kasowanie kasuje).
- **⌨** – chowa/pokazuje panel z przyciskami myszy i klawiszami (gładzik robi się wtedy większy).
- **⚙** – czułość kursora, szybkość i kierunek przewijania.
  Jest tam też **tryb zgodności** (bez dotyku Wild Rift) – tylko gdy drugi telefon w ogóle nie chce się połączyć.

## 🕹 Pad Xbox (uniwersalny)

**🎮 → Pad Xbox** zamienia telefon w pad Bluetooth w układzie Xbox. Działa w grach na Androida
z obsługą pada (i na PC jako standardowy pad HID).

- **Lewa gałka**, **prawa gałka** – szybkie stuknięcie gałki bez ruchu = wciśnięcie (L3 / R3)
- **D-pad** – można przesuwać palec między kierunkami (także po skosie)
- **A B X Y**, **LB RB**
- **LT RT** – analogowe: dotknięcie = pół siły, im niżej przesuniesz palec, tym mocniej
- **⧉ Widok**, **≡ Menu**, **Ⓧ Xbox**
- Wiele palców naraz (np. gałka + strzał + celowanie)

Na PC wiele gier oczekuje „prawdziwego” pada Xbox (XInput) – wtedy pomaga Steam (Ustawienia →
Kontroler → obsługa pada ogólnego) albo program typu x360ce.

## 🎮 Tryb gry – kontroler pod Wild Rift

**🎮 → Wild Rift** otwiera kontroler pod *League of Legends: Wild Rift*. Telefon sterujący działa wtedy
jak **ekran dotykowy drugiego telefonu – do 3 palców naraz** (ruch + umiejętność + atak jednocześnie).

- Na ekranie są kółka z konfiguracji Wild Rift: **Ruch** (joystick), **Atak**, **Q W E R**,
  czary przywoływacza **D F**, **Przedmiot**, **Ward**, **Powrót**, **Sklep**.
- Dotknięcie kółka = palec na drugim telefonie ląduje dokładnie w środku tego przycisku w grze,
  a potem podąża za Twoim ruchem → tak działa joystick i celowanie umiejętnościami.
- Dotyk poza kółkami trafia w to samo miejsce ekranu gry (sklep, ulepszanie umiejętności, minimapa).

**Konfiguracja (raz):**
1. Jeśli na górze ekranu widzisz **⚠ Napraw parowanie** – stuknij i przejdź kreator.
2. Na telefonie z grą zrób zrzut ekranu w trakcie meczu (np. w treningu) i przenieś go na telefon sterujący.
3. W trybie gry: **🖼 Tło** → wybierz zrzut, potem **✎ Edytuj** → przeciągnij kółka na przyciski
   ze zrzutu (− / + zmienia rozmiar), **✓ Gotowe**.
4. Jeśli dotyk trafia w złe miejsce, przełącz **↻** (90° / 270° / 0°) – zależy, w którą stronę
   obrócony jest telefon z grą.
5. Pomocne: na telefonie z grą *Opcje programisty → „Pokaż dotknięcia”*.

Domyślne położenia przycisków są przybliżone – każdy telefon i ustawienia HUD-u w grze są trochę inne,
dlatego krok 3 jest ważny.

### Polskie znaki

Aplikacja wysyła ą, ć, ę, ł, ń, ó, ś, ź, ż jako **AltGr + litera**. Żeby działały, na sterowanym
urządzeniu ustaw układ fizycznej klawiatury **„Polski (programisty)”**
(Android: *Ustawienia → System → Języki i wprowadzanie → Klawiatura fizyczna → BT Pilot*;
Windows ma go domyślnie).

## Budowanie samemu

```bash
cd bt-pilot
./gradlew assembleDebug
# wynik: app/build/outputs/apk/debug/app-debug.apk
```

Potrzebne: JDK 17+ i Android SDK (ustaw `ANDROID_HOME`).

## Jak to działa

Aplikacja używa systemowego API `BluetoothHidDevice` (Android 9+): rejestruje telefon jako
złożone urządzenie HID (klawiatura + mysz + klawisze multimedialne) i wysyła standardowe raporty HID.
Kod:

- `Hid.kt` – deskryptor HID i kody klawiszy multimedialnych
- `HidController.kt` – rejestracja, połączenie i wysyłanie raportów
- `KeyMap.kt` – zamiana znaków na kody klawiszy
- `TouchpadView.kt` – gładzik i gesty
- `MainActivity.kt` – ekran aplikacji
