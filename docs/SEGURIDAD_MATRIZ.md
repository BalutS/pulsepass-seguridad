# Matriz de autorización — PulsePass Fase 4 (Etapa 1: Contrato)

Contrato de seguridad de las **20 operaciones heredadas** + los endpoints nuevos de autenticación.
Fuente: PRD de Seguridad §10, §11 y §14.3.

**Leyenda de estado (mitad 1):**
✅ regla implementada y probada · 🔒 denegada por defecto hasta completar la etapa indicada · ⏳ pendiente

> **Importante:** en esta entrega todavía no existe `JwtAuthenticationFilter` (etapa 5). Por eso, en la
> aplicación real, **ninguna ruta protegida es alcanzable aún**: responden 401. Es intencional
> (SEC001, denegar por defecto). Las reglas de rol ya están en `SecurityConfig` y se prueban con
> `@WithMockUser` en `SecurityRulesTest`.

## Endpoints heredados (20)

| ID | Método y ruta | Acceso (PRD) | Dónde se aplica | Estado |
|---|---|---|---|---|
| FR-SECVEN-01 | `GET /api/venues/{code}` | Público | `SecurityConfig` | ✅ |
| FR-SECVEN-02 | `GET /api/venues/active` | Público | `SecurityConfig` | ✅ |
| FR-SECEVT-01 | `POST /api/events` | ADMIN | `SecurityConfig` | ✅ (operable en etapa 5) |
| FR-SECEVT-02 | `GET /api/events/{eventCode}` | Público solo si `PUBLISHED`; otros estados STAFF/ADMIN | `SecurityConfig` + `EventService` | 🔒 etapa 7 |
| FR-SECEVT-03 | `GET /api/events/published` | Público | `SecurityConfig` | ✅ |
| FR-SECEVT-04 | `PATCH /api/events/{eventCode}/publish` | ADMIN | `SecurityConfig` | ✅ (operable en etapa 5) |
| FR-SECEVT-05 | `POST /api/events/{eventCode}/artists/{artistId}` | ADMIN | `SecurityConfig` | ✅ (operable en etapa 5) |
| FR-SECEVT-06 | `GET /api/events/by-artist?stageName=` | Público solo `PUBLISHED`; otros STAFF/ADMIN | `SecurityConfig` + `EventService` | 🔒 etapa 7 |
| FR-SECART-01 | `GET /api/artists/{id}` | Público | `SecurityConfig` | ✅ |
| FR-SECART-02 | `GET /api/artists/by-stage-name?stageName=` | Público | `SecurityConfig` | ✅ |
| FR-SECART-03 | `GET /api/artists/active` | Público | `SecurityConfig` | ✅ |
| FR-SECUSR-01 | `POST /api/users` | Público (rol inicial solo USER) | `SecurityConfig` + `AuthService` | ✅ |
| FR-SECUSR-02 | `GET /api/users/by-email?email=` | Propio usuario, o ADMIN | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECUSR-03 | `GET /api/users/by-username?username=` | Propio usuario, o ADMIN | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECTKT-01 | `POST /api/tickets` | USER; `userEmail` debe ser del principal | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECTKT-02 | `GET /api/tickets/{ticketCode}` | Propietario, o STAFF/ADMIN | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECTKT-03 | `GET /api/tickets/by-user?email=` | Propietario, o ADMIN | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECTKT-04 | `GET /api/events/{eventCode}/tickets/paid` | STAFF/ADMIN | `SecurityConfig` | ✅ (operable en etapa 5) |
| FR-SECTKT-05 | `PATCH /api/tickets/{ticketCode}/cancel` | Propietario (USER) o ADMIN | `AuthorizationService` | 🔒 etapa 7 |
| FR-SECTKT-06 | `PATCH /api/tickets/{ticketCode}/use` | STAFF/ADMIN | `SecurityConfig` | ✅ (operable en etapa 5) |

Las filas 🔒 hoy responden **401** a cualquiera (`anyRequest().authenticated()` sin forma de autenticarse):
cierran el hueco de filtración de borradores / IDOR mientras se construye la etapa 7.

## Endpoints nuevos

| ID | Método y ruta | Acceso | Estado |
|---|---|---|---|
| FR-SECAUTH-002 | `POST /api/auth/login` | Público | ✅ |
| FR-SECAUTH-010 | `GET /api/auth/me` | Autenticado (Bearer) | ⏳ etapa 5 |

## Decisiones tomadas / propuestas

| Tema | Decisión | Estado |
|---|---|---|
| Ticket ajeno: ¿403 o 404? | **403** en todos los casos (más simple y consistente con UC-SEC-06). Se puede cambiar a 404 sin tocar el contrato de error. | Propuesta: confirmar con el docente |
| STAFF global | En el MVP STAFF opera sobre cualquier evento (el dominio no modela asignación). Limitación documentada, no apta para producción. | Aceptada por el PRD |
| Usuarios legados | Reciben `ROLE_USER` en V4 pero **sin credencial**: no pueden iniciar sesión hasta una activación segura. No hay contraseñas por defecto. | Implementada |
| Alta de STAFF/ADMIN | Nunca por HTTP. Falta definir el procedimiento (semilla local controlada para dev/tests). | ⏳ decidir en mitad 2 |
| Mensaje 401 de login | `Credenciales inválidas`, idéntico para usuario inexistente, contraseña errónea y cuenta inactiva. | ✅ |
