# Matriz de autorización — PulsePass Fase 4 (Estado Final)

Contrato de seguridad de las **20 operaciones heredadas** + los endpoints nuevos de autenticación.
Fuente de verdad: `PRD_PulsePass_Capa_Seguridad_Spring_Security_JJWT.md`.

**Leyenda de estado:**
✅ Regla implementada y probada exhaustivamente con pruebas unitarias, MockMvc e integración con JWT reales.

## Endpoints heredados (20)

| ID | Método y ruta | Acceso (PRD) | Dónde se aplica | Estado |
|---|---|---|---|---|
| FR-SECVEN-01 | `GET /api/venues/{code}` | Público | `SecurityConfig` | ✅ |
| FR-SECVEN-02 | `GET /api/venues/active` | Público | `SecurityConfig` | ✅ |
| FR-SECEVT-01 | `POST /api/events` | ADMIN | `SecurityConfig` (`ROLE_ADMIN`) | ✅ |
| FR-SECEVT-02 | `GET /api/events/{eventCode}` | Público solo si `PUBLISHED`; otros estados STAFF/ADMIN | `SecurityConfig` + `AuthorizationService` | ✅ |
| FR-SECEVT-03 | `GET /api/events/published` | Público | `SecurityConfig` | ✅ |
| FR-SECEVT-04 | `PATCH /api/events/{eventCode}/publish` | ADMIN | `SecurityConfig` (`ROLE_ADMIN`) | ✅ |
| FR-SECEVT-05 | `POST /api/events/{eventCode}/artists/{artistId}` | ADMIN | `SecurityConfig` (`ROLE_ADMIN`) | ✅ |
| FR-SECEVT-06 | `GET /api/events/by-artist?stageName=` | Público solo `PUBLISHED`; otros estados STAFF/ADMIN | `SecurityConfig` + `EventServiceImpl` + `AuthorizationService` | ✅ |
| FR-SECART-01 | `GET /api/artists/{id}` | Público | `SecurityConfig` | ✅ |
| FR-SECART-02 | `GET /api/artists/by-stage-name?stageName=` | Público | `SecurityConfig` | ✅ |
| FR-SECART-03 | `GET /api/artists/active` | Público | `SecurityConfig` | ✅ |
| FR-SECUSR-01 | `POST /api/users` | Público (rol inicial exclusivamente `USER`) | `SecurityConfig` + `AuthService` | ✅ |
| FR-SECUSR-02 | `GET /api/users/by-email?email=` | Propio usuario, o ADMIN | `AuthorizationService` | ✅ |
| FR-SECUSR-03 | `GET /api/users/by-username?username=` | Propio usuario, o ADMIN | `AuthorizationService` | ✅ |
| FR-SECTKT-01 | `POST /api/tickets` | USER; `userEmail` debe pertenecer al principal | `SecurityConfig` (`ROLE_USER`) + `AuthorizationService` | ✅ |
| FR-SECTKT-02 | `GET /api/tickets/{ticketCode}` | Propietario, o STAFF/ADMIN | `AuthorizationService` | ✅ |
| FR-SECTKT-03 | `GET /api/tickets/by-user?email=` | Propietario, o ADMIN | `AuthorizationService` | ✅ |
| FR-SECTKT-04 | `GET /api/events/{eventCode}/tickets/paid` | STAFF/ADMIN | `SecurityConfig` (`ROLE_STAFF`, `ROLE_ADMIN`) | ✅ |
| FR-SECTKT-05 | `PATCH /api/tickets/{ticketCode}/cancel` | Propietario (USER) o ADMIN | `AuthorizationService` | ✅ |
| FR-SECTKT-06 | `PATCH /api/tickets/{ticketCode}/use` | STAFF/ADMIN | `SecurityConfig` (`ROLE_STAFF`, `ROLE_ADMIN`) | ✅ |

## Endpoints nuevos de autenticación

| ID | Método y ruta | Acceso | Dónde se aplica | Estado |
|---|---|---|---|---|
| FR-SECAUTH-001 / 002 | `POST /api/auth/login` | Público | `SecurityConfig` + `AuthController` | ✅ |
| FR-SECAUTH-010 | `GET /api/auth/me` | Autenticado (Bearer) | `SecurityConfig` + `AuthServiceImpl` | ✅ |

## Decisiones de diseño y seguridad aplicadas

| Tema | Decisión final implementada |
|---|---|
| Respuesta ante recurso ajeno | Retorna **403 Forbidden** con el contrato estandarizado `ErrorResponse`. |
| Vinculación estricta de compra de ticket | `POST /api/tickets` exige rol `USER` y valida mediante `AuthorizationService.validateTicketPurchaseOwnership()` que el email del DTO coincida estrictamente con el email del usuario autenticado. |
| Filtrado de eventos por artista | `GET /api/events/by-artist?stageName=` retorna únicamente eventos publicados para usuarios anónimos o `USER`. STAFF y ADMIN reciben eventos en cualquier estado. |
| Visibilidad de evento individual | `GET /api/events/{eventCode}` permite acceso público solo si el estado es `PUBLISHED`. De lo contrario, exige rol `STAFF` o `ADMIN`. |
| Autenticación Stateless y JWT | Firma HS256 con JJWT 0.13.0, revalidación de principal e inhabilitación contra la base de datos en cada petición mediante `JwtAuthenticationFilter`. |
