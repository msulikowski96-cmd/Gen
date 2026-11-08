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
2. Kliknij **Połącz → „Uczyń ten telefon widocznym”**.
3. Na **drugim telefonie** wejdź w *Ustawienia → Bluetooth* i sparuj się z pierwszym telefonem.
4. Po sparowaniu na drugim telefonie pojawi się kursor myszy.
   Jeśli nie pojawi się sam: w BT Pilot kliknij **Połącz** i wybierz drugi telefon z listy.

Później wystarczy kliknąć **Połącz** i wybrać urządzenie z listy sparowanych.

## Sterowanie

| Gest na gładziku | Działanie |
|---|---|
| przesuwanie 1 palcem | ruch kursora |
| stuknięcie 1 palcem | lewy klik |
| stuknięcie 2 palcami | prawy klik |
| stuknięcie 3 palcami | środkowy klik |
| przesuwanie 2 palcami | przewijanie (pion i poziom) |
| przytrzymanie i przesunięcie | przeciąganie (lewy przycisk wciśnięty) |

- **Przyciski Lewy / Środ. / Prawy** działają jak w prawdziwej myszy – można je przytrzymać.
- **Pasek systemowy**: Wstecz, Home, przełączanie aplikacji, głośność, multimedia, jasność.
- **Pasek klawiszy**: Esc, Tab, strzałki, Enter, Backspace, Delete, Home/End, PgUp/PgDn, F1–F12.
- **Ctrl / Alt / Shift / Win** działają „na jeden klawisz”: stuknij Ctrl, potem wpisz `c` → Ctrl+C.
- **Pole „Pisz tutaj”** – wszystko, co wpiszesz klawiaturą telefonu, pojawia się na drugim urządzeniu
  (Enter wysyła Enter, kasowanie kasuje).
- **⚙** – czułość kursora, szybkość i kierunek przewijania.

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
