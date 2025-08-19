# Orthodoxer Kompass

Dieser Telegram-Bot hilft Dir, Deinen Weg im orthodoxen Glauben zu finden.
Er enthält Module wie "Taufe", "Kirche", "Familie" und mehr – verständlich, interaktiv, persönlich.

## Starten

1. Bot starten mit `/start`
2. Sprache wählen (RU / DE)
3. Modul auswählen und Fragen beantworten
4. Empfehlung erhalten

## Deployment

- Railway-fähig
- PostgreSQL erforderlich
- Webhook unter `/bot/webhook`

.env Beispiel:
```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=orthodoxer
DB_USER=postgres
DB_PASSWORD=password
```
