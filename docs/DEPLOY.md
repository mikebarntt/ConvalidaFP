# Despliegue en un servidor (AWS Lightsail o EC2)

Guía para poner ConvalidaFP en producción en un único servidor Linux con Docker. Usa
[`docker-compose.prod.yml`](../docker-compose.prod.yml), que cumple lo que exige el despliegue:

| Requisito | Cómo se cumple |
|---|---|
| HTTPS | Caddy termina TLS con un certificado de Let's Encrypt que obtiene y renueva solo. HTTP redirige a HTTPS y se envía HSTS. Las cookies de sesión y de CSRF salen marcadas como `Secure` |
| Sin puerto 8080 publicado | Solo Caddy publica puertos (80 y 443). El backend solo es accesible a través de Caddy → Nginx |
| Sin datos de ejemplo | No se define `CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED` (desactivada por defecto) ni se monta `data/sample`. La base de datos arranca vacía |
| `X-Forwarded-For` solo de proxies de confianza | Caddy descarta la cabecera que envía el cliente y pone la IP real. El backend solo la acepta de Caddy (`172.30.0.10`) y de Nginx (`172.30.0.20`), que tienen IP fija (`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`) |
| Primer administrador | `ADMIN_USERNAME` y `ADMIN_PASSWORD` en `.env.prod` solo para el primer arranque; después se vacían |

La base de datos está en una red interna de Docker, sin acceso desde fuera ni salida a Internet.

Todo esto se comprueba en CI (`deploy/check-prod-config.mjs` y `deploy/smoke-test.sh`), y puedes
comprobarlo en tu equipo con `bash deploy/smoke-test.sh` (necesita los puertos 80 y 443 libres).

## 1. Crear el servidor

Cualquiera de las dos opciones sirve. Recomendado: **2 GB de RAM** como mínimo (Java + MariaDB), 2
vCPU y 40 GB de disco. Sistema: **Ubuntu 24.04 LTS**.

**Lightsail.** Crear instancia → Linux/Unix → «OS Only» → Ubuntu 24.04 → plan de 2 GB o más. Después:

- *Networking* → crear una **IP estática** y asociarla a la instancia.
- *Networking* → *IPv4 Firewall*: dejar abiertos **22 (SSH), 80 (HTTP) y 443 (HTTPS)**. Nada más.

**EC2.** Lanzar instancia → Ubuntu Server 24.04 LTS → `t3.small` o superior → disco gp3 de 40 GB.
Después:

- Asignar una **Elastic IP** a la instancia.
- *Security group*: entrada solo **22 desde tu IP**, y **80 y 443 desde cualquier origen**
  (IPv4 e IPv6 si lo usas). No abras el 3306 ni el 8080.

## 2. Apuntar el dominio

En tu proveedor de DNS, crea un registro **A** (y **AAAA** si el servidor tiene IPv6) con el dominio
(por ejemplo `convalidafp.example.com`) apuntando a la IP estática. Espera a que resuelva:

```bash
dig +short convalidafp.example.com
```

Caddy no podrá obtener el certificado hasta que el dominio apunte al servidor y el puerto 80 esté
abierto.

## 3. Instalar Docker en el servidor

Conéctate por SSH (`ssh ubuntu@IP`) e instala Docker Engine con el plugin de Compose desde el
repositorio oficial de Docker (instrucciones: <https://docs.docker.com/engine/install/ubuntu/>).
Después:

```bash
sudo usermod -aG docker $USER
```

Cierra la sesión y vuelve a entrar. Comprueba que funciona:

```bash
docker compose version
```

Opcional pero recomendable: actualizaciones de seguridad automáticas.

```bash
sudo apt install -y unattended-upgrades
```

## 4. Descargar el código y configurar

```bash
git clone https://github.com/mikebarntt/ConvalidaFP.git
```

```bash
cd ConvalidaFP && cp .env.prod.example .env.prod && chmod 600 .env.prod
```

Edita `.env.prod` (`nano .env.prod`):

- `DOMAIN`: el dominio del paso 2. `ACME_EMAIL`: un correo para los avisos de Let's Encrypt.
- `DB_PASSWORD` y `DB_ROOT_PASSWORD`: claves largas y aleatorias (`openssl rand -base64 32`).
- `ADMIN_USERNAME` y `ADMIN_PASSWORD`: el primer administrador (contraseña de 12 caracteres o más).

`.env.prod` no se sube al repositorio (está en `.gitignore`).

## 5. Arrancar

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

La primera vez tarda unos minutos (compila backend y frontend). Comprueba el estado:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
```

Los cuatro servicios (`db`, `backend`, `frontend`, `proxy`) deben aparecer como `healthy` o
`running`. Abre `https://TU-DOMINIO`: verás la aplicación con candado y, todavía, sin datos.

**Retira el administrador inicial.** Ya está creado en la base de datos: vacía `ADMIN_USERNAME` y
`ADMIN_PASSWORD` en `.env.prod` y vuelve a ejecutar el comando de arranque.

## 6. Cargar un dataset

En producción no hay datos de ejemplo: se cargan desde el panel.

1. Prepara la carpeta del curso según [CONTRACT.md](../CONTRACT.md) (`data/real/<curso>/`, 9 CSV y
   `manifest.json`) y comprímela en un ZIP.
2. Entra en `https://TU-DOMINIO/admin`, sube el ZIP en *Importaciones* y revisa el informe
   (errores, avisos y diferencias).
3. Si no hay errores, **Publicar**. Desde ese momento la API y la web usan esa versión.

Nunca subas a producción los datos de `data/sample/`: son ficticios.

## 7. Operación

Ver los registros:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f --tail 100 backend
```

Actualizar a una versión nueva del código:

```bash
git pull && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Copia de seguridad de la base de datos (guárdala fuera del servidor, por ejemplo en S3):

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec db sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction "$MARIADB_DATABASE"' | gzip > convalidafp-$(date +%F).sql.gz
```

Además, en Lightsail activa las **instantáneas automáticas** de la instancia; en EC2, una política de
**Data Lifecycle Manager** para el volumen EBS.

Los volúmenes `caddy-data` (certificados) y `db-data` (datos) sobreviven a reinicios y
actualizaciones. **No uses `down -v`** en producción: borra la base de datos.

## Riesgos y límites conocidos

- **Un solo servidor.** Sin alta disponibilidad: si la instancia cae, el servicio cae. Las
  instantáneas y los volcados permiten recuperarlo.
- **IP real del cliente.** El límite de peticiones usa la IP que ve Caddy. Si pones delante otro
  proxy o un balanceador (CloudFront, ALB), Caddy verá la IP de ese proxy y todos los visitantes
  compartirán límite: habría que declararlo en `trusted_proxies` de Caddy.
- **Red `172.30.0.0/24`.** Las IP fijas de la red de Docker no deben chocar con la red de la VPC; si
  chocan, cambia la subred y las IP en `docker-compose.prod.yml` y en `deploy/Caddyfile`. La
  comprobación de CI avisa si quedan desalineadas.
- **Compilación en el servidor.** Las imágenes se construyen en el propio servidor; con menos de 2
  GB de RAM la compilación del backend puede fallar.
