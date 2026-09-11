# Axiom Licence

Licence server and admin panel. Java 21, no framework, SQLite, encrypted verify endpoint.

## Modules

- `common` – shared code and the licence API: envelope crypto (X25519 + HKDF + AES-256-GCM, Ed25519-signed responses), binary protocol, SQLite persistence, `LicenseEndpoint`, `LicenseClient` for products that embed licence checks.
- `web` – admin panel on the JDK HTTP server: sign-in, `ADMIN` and `MOD` roles, licence issuing, account management.

## Run

```sh
./gradlew installDist
web/build/install/web/bin/web
```

First start creates the account `admin` and prints its password once. Change it from Accounts.

| Variable     | Default     | Meaning                    |
|--------------|-------------|----------------------------|
| `AXIOM_BIND` | `127.0.0.1` | Listen address             |
| `AXIOM_PORT` | `8080`      | Listen port                |
| `AXIOM_DATA` | `data`      | Database and key directory |

## Expose through Cloudflare

```sh
scripts/tunnel.sh
```

Without a Cloudflare login this opens a quick tunnel on a random `trycloudflare.com` host and prints the address. For a stable hostname:

```sh
cloudflared tunnel login
cloudflared tunnel create axiom
AXIOM_TUNNEL=axiom AXIOM_HOSTNAME=licenses.example.com scripts/tunnel.sh
```

## Integrate a product

The Integration page shows the endpoint and both public keys.

```java
var client = new LicenseClient(URI.create("https://licenses.example.com"), EXCHANGE_PUBLIC, SIGNING_PUBLIC);
client.verify(key, "my-product", hardwareId).thenAccept(response -> {
    if (response.verdict().granted()) { ... }
});
```

A key binds to the first hardware id it sees; Unbind in the panel releases it.

## Test

```sh
./gradlew test
```
