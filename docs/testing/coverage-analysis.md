# Análisis de cobertura (Fase 12)

## Gate
- JaCoCo, líneas >= 80 % sobre `domain` + `application`, sin exclusiones.
- Solo cuentan los tests **unitarios** (Surefire). Los `*IT` no suman: que
  estas capas superen el umbral sin base de datos, Redis ni HTTP demuestra
  que la arquitectura hexagonal aísla la lógica de la infraestructura.
- Ejecución: `./mvnw verify` o, en CI, `./mvnw jacoco:check@coverage-gate`.
  **Atención**: `./mvnw jacoco:check` sin `@coverage-gate` se ejecuta sin
  reglas y siempre pasa.
- El umbral es un piso, no una meta: no se sube aunque la cobertura real
  lo supere.

## Resultado
~95 % de líneas. Las líneas restantes se analizaron una por una:

| Categoría | Ejemplos | Decisión |
|---|---|---|
| Guardas defensivas inalcanzables | Guardas de tipo en los handlers, fallo de serialización de Jackson, proveedor inexistente bajo FK | Se mantienen, sin test |
| Validaciones duplicadas en objetos intermedios | `TransferMoneyCommand`, `AuthorizationRequest`, `TransferCommand` | Se mantienen como contrato; la regla ya está probada en la frontera HTTP |
| Getters sin consumidor en tests unitarios | `User`, `Provider`, `getIdempotencyKey()` | Sin acción |
| **Código muerto** | Chequeos `null` en `TransferMoney` ya garantizados por los factories del comando | **Eliminado** |
| **Invariantes sin test** | Referencia externa en blanco en `Transaction`; `IdempotencyResult` sin test | **Tests añadidos** |

## Hallazgo no detectable por el porcentaje
`BR-013` (banco/proveedor inexistente o inactivo) estaba al 100 % de
líneas cubiertas, pero solo 2 de sus 5 casos tenían test: las
validaciones estaban encadenadas en una sola expresión. La auditoría se
hizo contra los requisitos del contexto, no contra el porcentaje.

## Deuda técnica conocida
Aviso de carga dinámica del agente de Mockito/ByteBuddy (JDK 21). Hoy es
solo un aviso; será un problema cuando un JDK futuro bloquee la carga
dinámica. La solución (cargar Mockito como `-javaagent` vía `argLine`)
se pospone para no complicar el build junto al agente de JaCoCo.