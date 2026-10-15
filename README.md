# SkyRynek 1.0.0 (Paper 1.21.4, Java 21)

## Budowanie
Zainstaluj JDK 21 i Apache Maven, a następnie w folderze projektu uruchom `mvn clean package`.
Wynik: `target/SkyRynek-1.0.0.jar` (biblioteka SQLite zostaje dołączona do JAR).

## Instalacja
1. Zainstaluj Paper 1.21.4, Vault oraz **plugin ekonomii obsługujący Vault** (sam Vault nie przechowuje pieniędzy).
2. Skopiuj wygenerowany JAR do `plugins/` i uruchom serwer.
3. Ustaw w LuckPerms: VIP `skyrynek.limit.6`, MVIP `skyrynek.limit.10`, FLOWY `skyrynek.limit.15`.
4. Zmieniaj `config.yml` i `messages.yml`, a następnie wykonaj restart (lub `/ah reload`).

## Komendy
- `/ah` i `/rynek` — menu startowe
- `/ah open` / `/rynek otworz` — rynek
- `/ah sell <price>` / `/rynek sprzedaj <cena>` — sprzedaż trzymanego stacka
- `/ah search <text>` / `/rynek szukaj <tekst>` — wyszukiwanie
- `/ah my` / `/rynek moje` — własne aukcje
- `/ah expired` / `/rynek wygasle` — wygasłe
- `/ah collect` / `/rynek odbierz` — zwroty do odbioru
- `/ah history` / `/rynek historia` — historia ostatnich transakcji
- `/ah player <nick>` / `/rynek gracz <nick>` — oferty gracza
- `/ah cancel <id>` / `/rynek anuluj <id>` — anulowanie własnej aukcji
- `/ah help` / `/rynek pomoc` — pomoc
- `/ah admin` / `/rynek admin` — podgląd panelu admina
- `/ah remove <id>` / `/rynek usun <id>` — usunięcie aukcji przez admina
- `/ah reload` / `/rynek przeladuj` — przeładowanie plików

## Uwaga techniczna
To projekt źródłowy, **nieprzetestowany na działającym serwerze**. Nie deklaruje pełnej odporności na awarie między zapisem transakcji a operacją ekonomii Vault. Przed użyciem produkcyjnym wykonaj testy i kopię zapasową bazy.
